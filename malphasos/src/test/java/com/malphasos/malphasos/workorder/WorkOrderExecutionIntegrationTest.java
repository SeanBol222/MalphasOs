package com.malphasos.malphasos.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ExecuteWorkOrderCommand;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderHasOpenReportsException;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Ejecutar una orden exige todos sus reportes cerrados (2026-10-14), de extremo a extremo: el servicio
 * de órdenes pregunta por un puerto suyo y contesta el adaptador de {@code report}, contra la base real.
 * La prueba del servicio usa un doble del puerto; esta es la que dice que el adaptador cuenta bien.
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
class WorkOrderExecutionIntegrationTest {

    @Autowired private WorkOrderServicePort servicio;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    private record Orden(UUID id, UUID uno, UUID dos) {
    }

    /** Una orden en ejecución con dos equipos de la misma área, todo por SQL. */
    private Orden unaOrdenConDosEquipos() {
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social) VALUES (?, ?, 'NIT_juridico', ?)",
                cliente, unico(), "Cliente " + unico());
        UUID pais = UUID.randomUUID();
        UUID ciudad = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pais (k_id_pais, k_codigo_iso, n_nombre_pais) VALUES (?, ?, ?)",
                pais, CodigoIsoLibre.en(jdbcTemplate), "Pais " + unico());
        jdbcTemplate.update("INSERT INTO ciudad (k_id_ciudad, n_nombre_ciudad, k_id_pais) VALUES (?, ?, ?)",
                ciudad, "Ciudad " + unico(), pais);
        UUID sede = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO sede (k_id_sede, n_nombre_sede, t_calle, t_carrera, t_numero, k_id_cliente, k_id_ciudad) VALUES (?, ?, '1', '2', '3', ?, ?)",
                sede, "Sede " + unico(), cliente, ciudad);
        UUID area = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                area, "Area " + unico(), sede);

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

        UUID uno = UUID.randomUUID();
        UUID dos = UUID.randomUUID();
        for (UUID unidad : new UUID[] {uno, dos}) {
            jdbcTemplate.update(
                    "INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio) VALUES (?, ?, ?, ?)",
                    unidad, "Serie-" + unico(), modelo, area);
        }

        UUID orden = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_cliente, k_id_sede, f_fecha_mantenimiento,
                                           n_periodicidad, t_tipo_servicio, t_estado_ejecucion)
                VALUES (?, ?, ?, ?, 'MENSUAL', 'CORRECTIVO', 'EN_EJECUCION')
                """,
                orden, cliente, sede, LocalDate.now());
        for (UUID unidad : new UUID[] {uno, dos}) {
            jdbcTemplate.update(
                    "INSERT INTO orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente, k_id_area_servicio) VALUES (?, ?, ?)",
                    orden, unidad, area);
        }

        return new Orden(orden, uno, dos);
    }

    private void unReporte(UUID orden, UUID equipo, boolean cerrado, boolean vigente) {
        jdbcTemplate.update(
                """
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,
                                              t_estado_reporte, t_resultado, t_finalizado, b_estado_activo)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), orden, equipo,
                cerrado ? "FINALIZADO" : "BORRADOR",
                cerrado ? "OPERATIVO" : null,
                cerrado ? LocalDateTime.now() : null,
                vigente);
    }

    private ExecutionState estadoDe(UUID orden) {
        return ExecutionState.valueOf(jdbcTemplate.queryForObject(
                "SELECT t_estado_ejecucion FROM orden_trabajo WHERE k_id_orden_trabajo = ?", String.class, orden));
    }

    @Test
    @DisplayName("con los dos reportes cerrados la orden queda ejecutada")
    void todosCerrados() {
        Orden orden = unaOrdenConDosEquipos();
        unReporte(orden.id(), orden.uno(), true, true);
        unReporte(orden.id(), orden.dos(), true, true);

        servicio.execute(new ExecuteWorkOrderCommand(orden.id()));

        assertThat(estadoDe(orden.id())).isEqualTo(ExecutionState.EJECUTADA);
    }

    @Test
    @DisplayName("un borrador, un equipo sin reporte o un cerrado ya retirado cuentan como pendientes")
    void pendientes() {
        Orden orden = unaOrdenConDosEquipos();
        unReporte(orden.id(), orden.uno(), false, true);
        // Cerrado pero retirado: el equipo vuelve a estar sin reporte, como dice la ficha de la orden.
        unReporte(orden.id(), orden.dos(), true, false);

        assertThatThrownBy(() -> servicio.execute(new ExecuteWorkOrderCommand(orden.id())))
                .isInstanceOf(WorkOrderHasOpenReportsException.class)
                .hasMessageContaining("2 equipos sin reporte cerrado");
        assertThat(estadoDe(orden.id())).isEqualTo(ExecutionState.EN_EJECUCION);
    }

    @Test
    @DisplayName("un equipo sin ningún reporte basta para que no se ejecute")
    void sinReporte() {
        Orden orden = unaOrdenConDosEquipos();
        unReporte(orden.id(), orden.uno(), true, true);

        assertThatThrownBy(() -> servicio.execute(new ExecuteWorkOrderCommand(orden.id())))
                .isInstanceOf(WorkOrderHasOpenReportsException.class)
                .hasMessageContaining("1 equipo sin reporte cerrado");
    }
}
