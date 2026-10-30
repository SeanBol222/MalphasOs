package com.malphasos.malphasos.workorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ExecuteWorkOrderCommand;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.jdbc.Sql;

/**
 * La orden se congela al ejecutarse (2026-10-28, {@code V49}), y <b>el documento tiene dos autores</b>: el
 * compositor de Java, para las que se ejecutan desde ahora, y el relleno en SQL de la migración, para las
 * ejecutadas antes. Los lee el mismo código, así que tienen que escribir lo mismo clave por clave.
 *
 * <p><b>La comparación aplica la propia migración.</b> Se siembran los mismos datos, con los mismos
 * identificadores, en dos sitios: en la base principal, donde Java ejecuta la orden, y en un esquema
 * aparte migrado hasta {@code V48}, donde se aplica {@code V49} encima. Hasta {@code V50} se ejecutaba la
 * sentencia del relleno, leída del archivo, sobre la base principal; {@code V50} quitó la tabla puente que
 * esa sentencia lee, y desde entonces el relleno solo puede correr donde corrió de verdad: en una base que
 * todavía no ha llegado a {@code V50}.
 *
 * <p>Los datos están elegidos para que cada diferencia conocida salte: un área con tilde —por bytes va
 * después de la «Z», por el idioma antes—, espacios alrededor de la dirección, un segundo nombre en blanco
 * y los equipos insertados al revés de como los lista el documento.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM intervencion",
            "DELETE FROM paso_reporte",
            "DELETE FROM dato_verificacion",
            "DELETE FROM reporte_servicio",
            "DELETE FROM orden_trabajo",
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM fabricante",
            "DELETE FROM marca",
            "DELETE FROM protocolo_tipo_equipo",
            "DELETE FROM tipo_equipo",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM cliente"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class WorkOrderFrozenDocumentTest {

    private static final String ESQUEMA = "prueba_gemelo_v49";

    @Autowired private WorkOrderServicePort servicio;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    /** La conexión del esquema aparte, con él en su {@code search_path}: ver la prueba de la migración. */
    private SingleConnectionDataSource conexion;
    private JdbcTemplate enElEsquema;

    @AfterEach
    void borrarEsquema() {
        if (conexion != null) {
            enElEsquema.execute("RESET search_path");
            conexion.destroy();
        }
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + ESQUEMA + " CASCADE");
    }

    private static String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    /**
     * Todo lo que se siembra, elegido una vez para sembrarlo idéntico en los dos sitios. La sigla va
     * explícita: si la generara el trigger, en la base principal podría salir otra por las de otros
     * clientes, y con ella otro número de hoja de vida.
     */
    private record Datos(
            UUID cliente, String documento, String razonSocial, String sigla, String iso,
            UUID pais, UUID ciudad, String nombreCiudad, UUID sede, String nombreSede,
            UUID zona, String nombreZona, UUID area, String nombreArea,
            UUID tipo, String nombreTipo, UUID marca, String nombreMarca, UUID equipo, UUID fabricante,
            String nombreFabricante, UUID modelo, String nombreModelo,
            UUID enZona, UUID enArea, UUID conRetirado, String serieZona, String serieArea, String serieRetirado,
            UUID ingeniero, String cedula, UUID orden,
            UUID reporteZona, UUID reporteArea, UUID reporteRetirado, UUID reporteVigente) {

        static Datos nuevos(String iso) {
            ThreadLocalRandom azar = ThreadLocalRandom.current();
            String sigla = "" + (char) ('A' + azar.nextInt(26)) + (char) ('A' + azar.nextInt(26))
                    + (char) ('A' + azar.nextInt(26)) + (char) ('A' + azar.nextInt(26));

            return new Datos(UUID.randomUUID(), unico(), "Clínica " + unico(), sigla, iso,
                    UUID.randomUUID(), UUID.randomUUID(), "Bogotá " + unico(), UUID.randomUUID(), "Sede " + unico(),
                    UUID.randomUUID(), "Zona " + unico(), UUID.randomUUID(), "Área " + unico(),
                    UUID.randomUUID(), "Tipo " + unico(), UUID.randomUUID(), "Marca " + unico(), UUID.randomUUID(),
                    UUID.randomUUID(), "Fabricante " + unico(), UUID.randomUUID(), "Modelo " + unico(),
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    "Serie-" + unico(), "Serie-" + unico(), "Serie-" + unico(),
                    UUID.randomUUID(), unico(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        }
    }

    /**
     * Una orden con tres equipos en dos áreas, un ingeniero y un reporte cerrado por equipo; el del tercero
     * se retiró y se le cerró otro vigente: el documento lleva el número de ese.
     *
     * @param estado {@code EN_EJECUCION} en la base principal, para que Java la ejecute;
     *     {@code EJECUTADA} en el esquema en {@code V48}, para que la congele el relleno
     * @param conPuente si se siembran las filas de la tabla puente: el esquema en {@code V48} las tiene, y
     *     las necesita su foránea compuesta antes que los reportes
     */
    private static void sembrar(JdbcTemplate destino, Datos d, String estado, boolean conPuente) {
        destino.update(
                "INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social, n_sigla)"
                        + " VALUES (?, ?, 'NIT_juridico', ?, ?)",
                d.cliente(), d.documento(), d.razonSocial(), d.sigla());
        destino.update("INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)",
                d.pais(), d.iso(), "Pais " + d.iso());
        destino.update("INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)",
                d.ciudad(), d.nombreCiudad(), d.pais());
        // Con espacios alrededor: la dirección de Java los recorta al leerla, y el relleno tiene que hacer
        // lo mismo.
        destino.update(
                "INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero, k_id_cliente, k_id_ciudad)"
                        + " VALUES (?, ?, ' 45 ', '12A', ' 30-1', ?, ?)",
                d.sede(), d.nombreSede(), d.cliente(), d.ciudad());
        destino.update("INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                d.zona(), d.nombreZona(), d.sede());
        destino.update("INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                d.area(), d.nombreArea(), d.sede());
        destino.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', 150000)
                """,
                d.tipo(), d.nombreTipo());
        destino.update("INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, ?)", d.marca(), d.nombreMarca());
        destino.update("INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)",
                d.equipo(), d.tipo(), d.marca());
        destino.update("INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, ?)",
                d.fabricante(), d.nombreFabricante());
        destino.update(
                "INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?, ?)",
                d.modelo(), d.nombreModelo(), d.fabricante(), d.equipo());

        // Al reves de como los lista el documento: si alguno de los dos no ordenara, la comparacion lo veria.
        // Y en el mismo orden en los dos sitios, para que el trigger les de el mismo numero de hoja de vida.
        insertEquipo(destino, d.conRetirado(), d.serieRetirado(), d.modelo(), d.area());
        insertEquipo(destino, d.enArea(), d.serieArea(), d.modelo(), d.area());
        insertEquipo(destino, d.enZona(), d.serieZona(), d.modelo(), d.zona());

        destino.update(
                """
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_segundo_nombre,
                                     n_primer_apellido, n_segundo_apellido, t_tipo_persona)
                VALUES (?, ?, 'Ana', '  ', 'Pérez', NULL, 'ENGINEER')
                """,
                d.ingeniero(), d.cedula());
        destino.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento,
                                           n_periodicidad, t_tipo_servicio, t_estado_ejecucion, k_id_ingeniero)
                VALUES (?, ?, ?, 'TRIMESTRAL', 'PREVENTIVO', ?, ?)
                """,
                d.orden(), d.sede(), LocalDate.of(2026, 10, 20), estado, d.ingeniero());

        if (conPuente) {
            for (UUID[] fila : new UUID[][] {{d.conRetirado(), d.area()}, {d.enArea(), d.area()}, {d.enZona(), d.zona()}}) {
                destino.update(
                        "INSERT INTO orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio)"
                                + " VALUES (?, ?, ?)",
                        d.orden(), fila[0], fila[1]);
            }
        }

        unReporte(destino, d.reporteZona(), d.orden(), d.enZona(), true);
        unReporte(destino, d.reporteArea(), d.orden(), d.enArea(), true);
        unReporte(destino, d.reporteRetirado(), d.orden(), d.conRetirado(), false);
        unReporte(destino, d.reporteVigente(), d.orden(), d.conRetirado(), true);
    }

    private static void insertEquipo(JdbcTemplate destino, UUID id, String serie, UUID modelo, UUID area) {
        destino.update(
                "INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio) VALUES (?, ?, ?, ?)",
                id, serie, modelo, area);
    }

    private static void unReporte(JdbcTemplate destino, UUID id, UUID orden, UUID equipo, boolean vigente) {
        destino.update(
                """
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,
                                              t_estado_reporte, t_resultado, f_finalizado, b_estado_activo)
                VALUES (?, ?, ?, 'FINALIZADO', 'OPERATIVO', ?, ?)
                """,
                id, orden, equipo, LocalDateTime.now(), vigente);
    }

    private String documentoDe(UUID orden) {
        return jdbcTemplate.queryForObject(
                "SELECT t_documento::text FROM orden_trabajo WHERE k_id_orden_trabajo = ?", String.class, orden);
    }

    /**
     * Un código ISO que no tenga ningún país en ninguna de las dos bases. No basta con mirar la principal:
     * otras pruebas borran países sembrados por V7 que no usan, y el esquema recién migrado los tiene todos.
     */
    private String isoLibreEnLasDos() {
        while (true) {
            String iso = CodigoIsoLibre.en(jdbcTemplate);
            if (enElEsquema.queryForObject("SELECT count(*) FROM pais WHERE k_codigo_iso = ?", Integer.class, iso) == 0) {
                return iso;
            }
        }
    }

    private void migrarElEsquemaHasta(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(ESQUEMA)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    @Test
    @DisplayName("ejecutar congela la orden: cabecera, sede, ingeniero y equipos ordenados por bytes")
    void ejecutarCongela() {
        Datos d = Datos.nuevos(CodigoIsoLibre.en(jdbcTemplate));
        sembrar(jdbcTemplate, d, "EN_EJECUCION", false);

        WorkOrderDocument documento = servicio.execute(new ExecuteWorkOrderCommand(d.orden())).getDocumento();

        assertThat(documento.ingeniero()).isEqualTo("Ana Pérez");
        assertThat(documento.sede().direccion()).isEqualTo("Calle 45 # 12A - 30-1");
        assertThat(documento.fechaEjecucion()).isNotNull();
        // «Área» va después de «Zona» comparando bytes, que es lo que hace COLLATE "C" en el relleno.
        assertThat(documento.equipos()).extracting(WorkOrderDocument.Equipo::id).first().isEqualTo(d.enZona());
        // El retirado no cuenta: el documento lleva el número del vigente.
        assertThat(documento.equipos()).filteredOn(e -> e.id().equals(d.conRetirado()))
                .singleElement().extracting(WorkOrderDocument.Equipo::numeroReporte)
                .isEqualTo(d.reporteVigente().toString().substring(0, 6).toUpperCase());
        assertThat(documentoDe(d.orden())).isNotNull();
    }

    @Test
    @DisplayName("el relleno de V49, aplicado de verdad, escribe el mismo documento que compone Java")
    void elRellenoEsGemeloDelCompositor() {
        migrarElEsquemaHasta("48");
        conexion = new SingleConnectionDataSource(DataSourceUtils.getConnection(dataSource), true);
        enElEsquema = new JdbcTemplate(conexion);
        enElEsquema.execute("SET search_path TO " + ESQUEMA);
        Datos d = Datos.nuevos(isoLibreEnLasDos());

        sembrar(jdbcTemplate, d, "EN_EJECUCION", false);
        servicio.execute(new ExecuteWorkOrderCommand(d.orden()));
        String deJava = documentoDe(d.orden());

        sembrar(enElEsquema, d, "EJECUTADA", true);
        migrarElEsquemaHasta("49");

        Boolean iguales = enElEsquema.queryForObject(
                """
                SELECT (?::jsonb - 'fechaEjecucion') = (t_documento - 'fechaEjecucion')
                FROM orden_trabajo WHERE k_id_orden_trabajo = ?
                """,
                Boolean.class, deJava, d.orden());
        String delRelleno = enElEsquema.queryForObject(
                "SELECT t_documento::text FROM orden_trabajo WHERE k_id_orden_trabajo = ?", String.class, d.orden());
        Boolean tardio = enElEsquema.queryForObject(
                "SELECT b_documento_tardio AND t_documento -> 'fechaEjecucion' = 'null'::jsonb"
                        + " FROM orden_trabajo WHERE k_id_orden_trabajo = ?",
                Boolean.class, d.orden());

        assertThat(iguales).describedAs("Java: " + deJava + "\nSQL:  " + delRelleno).isTrue();
        assertThat(tardio).isTrue();
    }
}
