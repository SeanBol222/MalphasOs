package com.malphasos.malphasos.workorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ExecuteWorkOrderCommand;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La orden se congela al ejecutarse (2026-10-28, {@code V49}), y <b>el documento tiene dos autores</b>: el
 * compositor de Java, para las que se ejecutan desde ahora, y el relleno en SQL de la migración, para las
 * ejecutadas antes. Los lee el mismo código, así que tienen que escribir lo mismo clave por clave.
 *
 * <p>La comparación ejecuta <b>la sentencia de la propia migración</b>, leída del archivo, dentro de una
 * transacción que se deshace: una copia de la consulta en la prueba podría coincidir con Java y no con lo
 * que de verdad corrió. Los datos están elegidos para que cada diferencia conocida salte: un área con
 * tilde —por bytes va después de la «Z», por el idioma antes—, espacios alrededor de la dirección y un
 * segundo nombre en blanco.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM intervencion",
            "DELETE FROM paso_reporte",
            "DELETE FROM dato_verificacion",
            "DELETE FROM reporte_servicio",
            "DELETE FROM orden_trabajo_equipo",
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

    @Autowired private WorkOrderServicePort servicio;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    private record Orden(UUID id, UUID enZona, UUID enArea, UUID conRetirado, String ingeniero) {
    }

    /**
     * Una orden en ejecución con tres equipos en dos áreas, un ingeniero y un reporte cerrado por equipo.
     * El del tercero está retirado, y cada prueba le cierra uno vigente: el documento lleva el número de
     * ese, no el del retirado.
     */
    private Orden unaOrdenParaEjecutar() {
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social) VALUES (?, ?, 'NIT_juridico', ?)",
                cliente, unico(), "Clínica " + unico());
        UUID pais = UUID.randomUUID();
        UUID ciudad = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)",
                pais, CodigoIsoLibre.en(jdbcTemplate), "Pais " + unico());
        jdbcTemplate.update("INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)",
                ciudad, "Bogotá " + unico(), pais);
        UUID sede = UUID.randomUUID();
        // Con espacios alrededor: la dirección de Java los recorta al leerla, y el relleno tiene que hacer
        // lo mismo.
        jdbcTemplate.update(
                "INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero, k_id_cliente, k_id_ciudad) VALUES (?, ?, ' 45 ', '12A', ' 30-1', ?, ?)",
                sede, "Sede " + unico(), cliente, ciudad);
        UUID zona = UUID.randomUUID();
        UUID area = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                zona, "Zona " + unico(), sede);
        jdbcTemplate.update("INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                area, "Área " + unico(), sede);

        UUID tipo = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', 150000)
                """,
                tipo, "Tipo " + unico());
        UUID marca = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO marca (k_id_marca, n_nombre_marca) VALUES (?, ?)", marca, "Marca " + unico());
        UUID equipo = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO equipo (k_id_equipo, k_id_tipo_equipo, k_id_marca) VALUES (?, ?, ?)",
                equipo, tipo, marca);
        UUID fabricante = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO fabricante (k_id_fabricante, n_nombre_fabricante) VALUES (?, ?)",
                fabricante, "Fabricante " + unico());
        UUID modelo = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO modelo (k_id_modelo, n_nombre_modelo, k_id_fabricante, k_id_equipo) VALUES (?, ?, ?, ?)",
                modelo, "Modelo " + unico(), fabricante, equipo);

        UUID enZona = UUID.randomUUID();
        UUID enArea = UUID.randomUUID();
        UUID conRetirado = UUID.randomUUID();
        insertEquipo(enZona, modelo, zona);
        insertEquipo(enArea, modelo, area);
        insertEquipo(conRetirado, modelo, area);

        UUID ingeniero = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_segundo_nombre,
                                     n_primer_apellido, n_segundo_apellido, t_tipo_persona)
                VALUES (?, ?, 'Ana', '  ', 'Pérez', NULL, 'ENGINEER')
                """,
                ingeniero, unico());

        UUID orden = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento,
                                           n_periodicidad, t_tipo_servicio, t_estado_ejecucion, k_id_ingeniero)
                VALUES (?, ?, ?, 'TRIMESTRAL', 'PREVENTIVO', 'EN_EJECUCION', ?)
                """,
                orden, sede, LocalDate.of(2026, 10, 20), ingeniero);
        // Al reves de como los lista el documento: si el compositor no ordenara, la comparacion lo veria.
        jdbcTemplate.update(
                "INSERT INTO orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio) VALUES (?, ?, ?)",
                orden, conRetirado, area);
        jdbcTemplate.update(
                "INSERT INTO orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio) VALUES (?, ?, ?)",
                orden, enArea, area);
        jdbcTemplate.update(
                "INSERT INTO orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio) VALUES (?, ?, ?)",
                orden, enZona, zona);

        unReporte(orden, enZona, true);
        unReporte(orden, enArea, true);
        unReporte(orden, conRetirado, false);

        return new Orden(orden, enZona, enArea, conRetirado, "Ana Pérez");
    }

    private void insertEquipo(UUID id, UUID modelo, UUID area) {
        jdbcTemplate.update(
                "INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio) VALUES (?, ?, ?, ?)",
                id, "Serie-" + unico(), modelo, area);
    }

    private void unReporte(UUID orden, UUID equipo, boolean vigente) {
        jdbcTemplate.update(
                """
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,
                                              t_estado_reporte, t_resultado, f_finalizado, b_estado_activo)
                VALUES (?, ?, ?, 'FINALIZADO', 'OPERATIVO', ?, ?)
                """,
                UUID.randomUUID(), orden, equipo, LocalDateTime.now(), vigente);
    }

    private String documentoDe(UUID orden) {
        return jdbcTemplate.queryForObject(
                "SELECT t_documento::text FROM orden_trabajo WHERE k_id_orden_trabajo = ?", String.class, orden);
    }

    /** La sentencia que rellena las órdenes ejecutadas, tal como está en la migración. */
    private static String rellenoDeV49() throws IOException {
        String migracion = new ClassPathResource("db/migration/V49__work_order_frozen_document.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        int desde = migracion.indexOf("UPDATE orden_trabajo o");
        int hasta = migracion.indexOf(";", desde);
        assertThat(desde).describedAs("la migracion ya no tiene el relleno donde se buscaba").isNotNegative();

        return migracion.substring(desde, hasta);
    }

    @Test
    @DisplayName("ejecutar congela la orden: cabecera, sede, ingeniero y equipos ordenados por bytes")
    void ejecutarCongela() {
        Orden orden = unaOrdenParaEjecutar();
        // El reporte retirado del tercero no cuenta: hay que cerrarle uno vigente para poder ejecutar.
        unReporteVigenteCerrado(orden.id(), orden.conRetirado());
        String vigente = jdbcTemplate.queryForObject(
                "SELECT upper(left(k_id_reporte_servicio::text, 6)) FROM reporte_servicio"
                        + " WHERE k_id_equipo_cliente = ? AND b_estado_activo",
                String.class, orden.conRetirado());

        WorkOrderDocument documento =
                servicio.execute(new ExecuteWorkOrderCommand(orden.id())).getDocumento();

        assertThat(documento.ingeniero()).isEqualTo(orden.ingeniero());
        assertThat(documento.sede().direccion()).isEqualTo("Calle 45 # 12A - 30-1");
        assertThat(documento.fechaEjecucion()).isNotNull();
        // «Área» va después de «Zona» comparando bytes, que es lo que hace COLLATE "C" en el relleno.
        assertThat(documento.equipos()).extracting(WorkOrderDocument.Equipo::id)
                .first().isEqualTo(orden.enZona());
        assertThat(documento.equipos()).filteredOn(e -> e.id().equals(orden.conRetirado()))
                .singleElement().extracting(WorkOrderDocument.Equipo::numeroReporte).isEqualTo(vigente);
        assertThat(documentoDe(orden.id())).isNotNull();
    }

    private void unReporteVigenteCerrado(UUID orden, UUID equipo) {
        unReporte(orden, equipo, true);
    }

    @Test
    @DisplayName("el relleno de V49 escribe el mismo documento que compone Java, salvo la fecha de ejecucion")
    void elRellenoEsGemeloDelCompositor() throws IOException {
        Orden orden = unaOrdenParaEjecutar();
        unReporteVigenteCerrado(orden.id(), orden.conRetirado());
        servicio.execute(new ExecuteWorkOrderCommand(orden.id()));
        String deJava = documentoDe(orden.id());
        String sentencia = rellenoDeV49();

        // Dentro de una transaccion que se deshace: el relleno toca todas las ordenes ejecutadas.
        List<Boolean> iguales = transactionTemplate.execute(estado -> {
            jdbcTemplate.update(sentencia);
            Boolean comparacion = jdbcTemplate.queryForObject(
                    """
                    SELECT (?::jsonb - 'fechaEjecucion') = (t_documento - 'fechaEjecucion')
                    FROM orden_trabajo WHERE k_id_orden_trabajo = ?
                    """,
                    Boolean.class, deJava, orden.id());
            Boolean tardio = jdbcTemplate.queryForObject(
                    "SELECT b_documento_tardio AND t_documento -> 'fechaEjecucion' = 'null'::jsonb"
                            + " FROM orden_trabajo WHERE k_id_orden_trabajo = ?",
                    Boolean.class, orden.id());
            estado.setRollbackOnly();

            return List.of(comparacion, tardio);
        });

        assertThat(iguales).describedAs("Java: " + deJava).containsExactly(true, true);
    }
}
