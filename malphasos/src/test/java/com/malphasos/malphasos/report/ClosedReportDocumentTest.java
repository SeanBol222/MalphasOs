package com.malphasos.malphasos.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FinishServiceReportCommand;
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
 * El reporte cerrado vive en su documento (2026-10-28, {@code V51}), y <b>la versión 2 del documento tiene dos
 * autores</b>: el compositor de Java, para los que se cierran desde ahora, y el relleno en SQL de la migración,
 * para los ya cerrados, que lee las copias que el reporte guardaba antes de quitarlas. Los lee el mismo código,
 * así que tienen que escribir lo mismo clave por clave.
 *
 * <p>Como la prueba gemela de {@code V49}, <b>aplica la propia migración</b>: siembra los mismos datos, con los
 * mismos identificadores, en la base principal —donde Java cierra el reporte— y en un esquema aparte migrado
 * hasta {@code V50}, donde el mismo reporte ya está cerrado con sus copias y su documento en la versión 1. Ahí
 * se aplica {@code V51}, y los dos documentos tienen que ser iguales.
 *
 * <p>Los datos están elegidos para que cada diferencia conocida salte: los pasos y los patrones insertados al
 * revés de como los lista el documento, un paso con indicación y otro sin ella, un patrón con ficha y otro sin
 * ella, y una verificación con lecturas —su tolerancia sale de ellas en SQL— y otra retirada y sin lecturas —su
 * tolerancia sale del tipo—.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM paso_reporte",
            "DELETE FROM dato_verificacion",
            "DELETE FROM patron_verificacion_reporte",
            "DELETE FROM accesorio_reporte",
            "DELETE FROM reporte_servicio",
            "DELETE FROM orden_trabajo",
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM fabricante",
            "DELETE FROM marca",
            "DELETE FROM protocolo_tipo_equipo",
            "DELETE FROM protocolo",
            "DELETE FROM equipo_patron",
            "DELETE FROM verificacion_tipo_equipo",
            "DELETE FROM tipo_equipo",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM cliente"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClosedReportDocumentTest {

    private static final String ESQUEMA = "prueba_gemelo_v51";

    @Autowired private ServiceReportServicePort servicio;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private SingleConnectionDataSource conexion;
    private JdbcTemplate enElEsquema;

    @AfterEach
    void borrarEsquema() {
        if (conexion != null) {
            enElEsquema.execute("RESET enable_indexscan");
            enElEsquema.execute("RESET enable_bitmapscan");
            enElEsquema.execute("RESET search_path");
            conexion.destroy();
        }
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + ESQUEMA + " CASCADE");
    }

    private static String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    /** Un identificador que empieza por ese carácter: así se elige en qué orden quedan al compararlos como texto. */
    private static UUID empezandoPor(char primero) {
        return UUID.fromString(primero + UUID.randomUUID().toString().substring(1));
    }

    /** Todo lo que se siembra, elegido una vez para sembrarlo idéntico en los dos sitios. */
    private record Datos(
            UUID cliente, String documento, String sigla, UUID sede, UUID area, UUID tipo, UUID presion,
            UUID temperatura, UUID marca, UUID equipo, UUID fabricante, UUID modelo, UUID unidad, UUID orden,
            UUID limpieza, UUID electrico, UUID conFicha, UUID sinFicha, UUID reporte, UUID lectura,
            UUID pasoLimpieza, UUID pasoElectrico, UUID usoConFicha, UUID usoSinFicha, String sufijo) {

        static Datos nuevos() {
            ThreadLocalRandom azar = ThreadLocalRandom.current();
            String sigla = "" + (char) ('A' + azar.nextInt(26)) + (char) ('A' + azar.nextInt(26))
                    + (char) ('A' + azar.nextInt(26)) + (char) ('A' + azar.nextInt(26));

            // El patrón con ficha empieza por «f» y el otro por «0»: por texto va primero el segundo, y se
            // insertan al revés.
            return new Datos(UUID.randomUUID(), unico(), sigla, UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), empezandoPor('f'), empezandoPor('0'), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), empezandoPor('f'), empezandoPor('0'), unico());
        }
    }

    /** El catálogo, la orden y el equipo: lo que es igual en los dos sitios. */
    private static void sembrarElCatalogo(JdbcTemplate destino, Datos d) {
        UUID ciudad = unaCiudad(destino);
        destino.update(
                "INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social, n_sigla)"
                        + " VALUES (?, ?, 'NIT_juridico', 'Clínica de Ejemplo', ?)",
                d.cliente(), d.documento(), d.sigla());
        destino.update(
                "INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero, k_id_cliente, k_id_ciudad)"
                        + " VALUES (?, 'Norte', '1', '2', '3', ?, ?)",
                d.sede(), d.cliente(), ciudad);
        destino.update("INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, 'Urgencias', ?)",
                d.area(), d.sede());
        destino.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', 150000)
                """,
                d.tipo(), "Tensiómetro " + d.sufijo());
        verificacion(destino, d.presion(), d.tipo(), "mmHg", "0.5", true);
        // Retirada y sin lecturas: el documento la lleva igual, y su tolerancia sale del tipo.
        verificacion(destino, d.temperatura(), d.tipo(), "°C", "0.3", false);
        protocolo(destino, d.limpieza(), "Limpieza", d.tipo(), 1, "Con guantes", d.sufijo());
        protocolo(destino, d.electrico(), "Eléctrico", d.tipo(), 2, null, d.sufijo());
        destino.update("INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, ?)", d.marca(), "Marca " + d.sufijo());
        destino.update("INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)",
                d.equipo(), d.tipo(), d.marca());
        destino.update("INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, ?)",
                d.fabricante(), "Fabricante " + d.sufijo());
        destino.update(
                "INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?, ?)",
                d.modelo(), "Modelo " + d.sufijo(), d.fabricante(), d.equipo());
        destino.update(
                "INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio) VALUES (?, ?, ?, ?)",
                d.unidad(), "S-" + d.sufijo(), d.modelo(), d.area());
        destino.update(
                """
                INSERT INTO equipo_patron (k_id_equipo_patron, n_nombre, n_marca, k_serie, n_certificado,
                                           f_vencimiento_certificado, n_modelo, k_codigo_interno, t_rango, t_resolucion,
                                           f_calibracion, n_empresa_calibradora)
                VALUES (?, 'Manómetro patrón', 'Fluke', ?, 'CAL-1', '2099-04-30', '700G', ?, '0 a 300 mmHg',
                        '0,1 mmHg', '2026-04-30', 'Metrología Andina')
                """,
                d.conFicha(), "M-" + d.sufijo(), "PAT-" + d.sufijo());
        destino.update(
                """
                INSERT INTO equipo_patron (k_id_equipo_patron, n_nombre, k_serie, n_certificado, f_vencimiento_certificado)
                VALUES (?, 'Termómetro de casa', ?, 'CAL-9', '2099-01-01')
                """,
                d.sinFicha(), "T-" + d.sufijo());
        destino.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento, n_periodicidad,
                                           t_tipo_servicio, t_estado_ejecucion)
                VALUES (?, ?, ?, 'TRIMESTRAL', 'PREVENTIVO', 'EN_EJECUCION')
                """,
                d.orden(), d.sede(), LocalDate.of(2026, 10, 20));
    }

    /**
     * Una ciudad cualquiera de esa base, o una nueva si no queda ninguna: otras pruebas borran las sembradas.
     * La del documento es la de la base principal, y el esquema la hereda en el documento de la versión 1.
     */
    private static UUID unaCiudad(JdbcTemplate destino) {
        java.util.List<UUID> ciudades = destino.queryForList("SELECT k_id_ciudad FROM ciudad LIMIT 1", UUID.class);
        if (!ciudades.isEmpty()) {
            return ciudades.getFirst();
        }

        ThreadLocalRandom azar = ThreadLocalRandom.current();
        String iso;
        do {
            iso = "" + (char) ('A' + azar.nextInt(26)) + (char) ('A' + azar.nextInt(26)) + (char) ('A' + azar.nextInt(26));
        } while (destino.queryForObject("SELECT count(*) FROM pais WHERE k_codigo_iso = ?", Integer.class, iso) > 0);
        UUID pais = UUID.randomUUID();
        UUID ciudad = UUID.randomUUID();
        destino.update("INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)", pais, iso, "Pais " + iso);
        destino.update("INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)",
                ciudad, "Ciudad " + iso, pais);

        return ciudad;
    }

    private static void verificacion(JdbcTemplate destino, UUID id, UUID tipo, String simbolo, String tolerancia,
            boolean activa) {
        destino.update(
                """
                INSERT INTO verificacion_tipo_equipo (k_id_verificacion, k_id_tipo_equipo, k_id_unidad_medida,
                                                      n_modalidad_verificacion, d_tolerancia, b_estado_activo)
                SELECT ?, ?, k_id_unidad_medida, 'patron_equipo_variable', ?::numeric, ?
                FROM unidad_medida WHERE n_simbolo_unidad = ?
                """,
                id, tipo, tolerancia, activa, simbolo);
    }

    private static void protocolo(JdbcTemplate destino, UUID id, String nombre, UUID tipo, int prioridad,
            String indicacion, String sufijo) {
        destino.update("INSERT INTO protocolo (k_id_protocolo, n_nombre, n_clave, t_descripcion) VALUES (?, ?, ?, ?)",
                id, nombre, (nombre + " " + sufijo).toLowerCase(), "Cómo se hace " + nombre);
        destino.update(
                "INSERT INTO protocolo_tipo_equipo (k_id_tipo_equipo, k_id_protocolo, i_prioridad, t_indicacion)"
                        + " VALUES (?, ?, ?, ?)",
                tipo, id, prioridad, indicacion);
    }

    /**
     * El borrador de la base principal, sin copias (V51): pasos hechos, una lectura y los dos patrones, todo
     * insertado al revés de como lo lista el documento.
     */
    private void sembrarElBorrador(Datos d) {
        jdbcTemplate.update(
                """
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,
                                              t_resultado, t_ingreso_fisico, t_ingreso_operativo)
                VALUES (?, ?, ?, 'OPERATIVO', 'CONFORME', 'CONFORME')
                """,
                d.reporte(), d.orden(), d.unidad());
        jdbcTemplate.update("INSERT INTO paso_reporte (k_id_paso_reporte, k_id_reporte_servicio, k_id_protocolo,"
                + " t_estado_paso) VALUES (?, ?, ?, 'HECHO')", d.pasoElectrico(), d.reporte(), d.electrico());
        jdbcTemplate.update("INSERT INTO paso_reporte (k_id_paso_reporte, k_id_reporte_servicio, k_id_protocolo,"
                + " t_estado_paso) VALUES (?, ?, ?, 'HECHO')", d.pasoLimpieza(), d.reporte(), d.limpieza());
        jdbcTemplate.update(
                """
                INSERT INTO dato_verificacion (k_id_dato_verificacion, k_id_reporte_servicio, k_id_verificacion,
                                               i_secuencia, d_valor_patron, d_valor_equipo)
                VALUES (?, ?, ?, 1, 100, 100.4)
                """,
                d.lectura(), d.reporte(), d.presion());
        for (UUID[] uso : new UUID[][] {{d.usoConFicha(), d.conFicha()}, {d.usoSinFicha(), d.sinFicha()}}) {
            jdbcTemplate.update(
                    "INSERT INTO patron_verificacion_reporte (k_id_patron_verificacion_reporte, k_id_reporte_servicio,"
                            + " k_id_verificacion, k_id_equipo_patron) VALUES (?, ?, ?, ?)",
                    uso[0], d.reporte(), d.presion(), uso[1]);
        }
    }

    /**
     * El mismo reporte en el esquema en V50, ya cerrado: con las copias que guardaba entonces y el documento en
     * la versión 1, que es el de Java sin lo que la 2 añade.
     */
    private void sembrarElCerrado(Datos d, String documentoDeJava) {
        enElEsquema.update(
                """
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,
                                              t_estado_reporte, t_resultado, f_finalizado, t_ingreso_fisico,
                                              t_ingreso_operativo, t_documento)
                SELECT ?, ?, ?, 'FINALIZADO', 'OPERATIVO', ?, 'CONFORME', 'CONFORME',
                       jsonb_set(j - 'pasos' - 'patrones' || '{"version": 1}', '{verificaciones}',
                                 (SELECT jsonb_agg(v - 'tolerancia' ORDER BY i)
                                  FROM jsonb_array_elements(j -> 'verificaciones') WITH ORDINALITY AS e(v, i)))
                FROM (SELECT ?::jsonb AS j) AS java
                """,
                d.reporte(), d.orden(), d.unidad(), LocalDateTime.now(), documentoDeJava);
        enElEsquema.update(
                """
                INSERT INTO paso_reporte (k_id_paso_reporte, k_id_reporte_servicio, i_orden, k_id_protocolo, n_nombre,
                                          t_descripcion, t_indicacion, t_estado_paso)
                VALUES (?, ?, 2, ?, 'Eléctrico', 'Cómo se hace Eléctrico', NULL, 'HECHO'),
                       (?, ?, 1, ?, 'Limpieza', 'Cómo se hace Limpieza', 'Con guantes', 'HECHO')
                """,
                d.pasoElectrico(), d.reporte(), d.electrico(), d.pasoLimpieza(), d.reporte(), d.limpieza());
        enElEsquema.update(
                """
                INSERT INTO dato_verificacion (k_id_dato_verificacion, k_id_reporte_servicio, k_id_verificacion,
                                               i_secuencia, d_valor_patron, d_valor_equipo, n_unidad, d_tolerancia)
                VALUES (?, ?, ?, 1, 100, 100.4, 'mmHg', 0.5)
                """,
                d.lectura(), d.reporte(), d.presion());
        enElEsquema.update(
                """
                INSERT INTO patron_verificacion_reporte (k_id_patron_verificacion_reporte, k_id_reporte_servicio,
                    k_id_verificacion, k_id_equipo_patron, n_nombre, n_marca, k_serie, n_certificado, f_vencimiento,
                    n_modelo, k_codigo_interno, t_rango, t_resolucion, f_calibracion, n_empresa_calibradora)
                VALUES (?, ?, ?, ?, 'Manómetro patrón', 'Fluke', ?, 'CAL-1', '2099-04-30', '700G', ?, '0 a 300 mmHg',
                        '0,1 mmHg', '2026-04-30', 'Metrología Andina'),
                       (?, ?, ?, ?, 'Termómetro de casa', NULL, ?, 'CAL-9', '2099-01-01', NULL, NULL, NULL, NULL, NULL,
                        NULL)
                """,
                d.usoConFicha(), d.reporte(), d.presion(), d.conFicha(), "M-" + d.sufijo(), "PAT-" + d.sufijo(),
                d.usoSinFicha(), d.reporte(), d.presion(), d.sinFicha(), "T-" + d.sufijo());
    }

    private void migrarElEsquemaHasta(String version) {
        migrarElEsquemaHasta(version, dataSource);
    }

    private void migrarElEsquemaHasta(String version, DataSource donde) {
        Flyway.configure()
                .dataSource(donde)
                .schemas(ESQUEMA)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    @Test
    @DisplayName("el relleno de V51, aplicado de verdad, escribe el mismo documento que compone Java al cerrar")
    void elRellenoEsGemeloDelCompositor() {
        Datos d = Datos.nuevos();
        sembrarElCatalogo(jdbcTemplate, d);
        sembrarElBorrador(d);
        servicio.finish(new FinishServiceReportCommand(d.reporte()));
        String deJava = jdbcTemplate.queryForObject(
                "SELECT t_documento::text FROM reporte_servicio WHERE k_id_reporte_servicio = ?", String.class,
                d.reporte());

        migrarElEsquemaHasta("50");
        conexion = new SingleConnectionDataSource(DataSourceUtils.getConnection(dataSource), true);
        enElEsquema = new JdbcTemplate(conexion);
        enElEsquema.execute("SET search_path TO " + ESQUEMA);
        sembrarElCatalogo(enElEsquema, d);
        sembrarElCerrado(d, deJava);
        // Sin recorrer índices: las filas salen en el orden en que se insertaron, que es el contrario del del
        // documento. Con ellos, los únicos de las dos tablas ya las devuelven ordenadas y un relleno que no
        // ordenara pasaría por suerte del plan, no por estar bien.
        enElEsquema.execute("SET enable_indexscan = off");
        enElEsquema.execute("SET enable_bitmapscan = off");
        migrarElEsquemaHasta("51", conexion);

        String delRelleno = enElEsquema.queryForObject(
                "SELECT t_documento::text FROM reporte_servicio WHERE k_id_reporte_servicio = ?", String.class,
                d.reporte());
        Boolean iguales = enElEsquema.queryForObject(
                "SELECT t_documento = ?::jsonb FROM reporte_servicio WHERE k_id_reporte_servicio = ?", Boolean.class,
                deJava, d.reporte());

        assertThat(iguales).describedAs("Java: " + deJava + "\nSQL:  " + delRelleno).isTrue();
        // Y no es igual por estar vacío: lleva lo que la versión 2 añade.
        assertThat(enElEsquema.queryForObject(
                        """
                        SELECT jsonb_array_length(t_documento -> 'pasos') || ' ' || jsonb_array_length(t_documento -> 'patrones')
                               || ' ' || (SELECT (v ->> 'tolerancia')::numeric = 0.5
                                          FROM jsonb_array_elements(t_documento -> 'verificaciones') AS v
                                          WHERE v ->> 'id' = ?)
                        FROM reporte_servicio WHERE k_id_reporte_servicio = ?
                        """,
                        String.class, d.presion().toString(), d.reporte()))
                .isEqualTo("2 2 true");
    }
}
