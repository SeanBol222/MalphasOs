package com.malphasos.malphasos.report.infrastructure.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderReportsPort;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Los dos adaptadores con los que {@code report} contesta a quien depende de él desde {@code V50}, contra
 * la base real: a {@code work-order}, qué equipos tiene una orden y cómo se ponen y se quitan —abriendo y
 * retirando borradores—, y a {@code equipment}, si una unidad está en una orden sin ejecutar.
 *
 * <p>Las pruebas de los servicios usan dobles de los dos puertos; esta es la que dice que el adaptador
 * filtra los retirados, agrupa por orden, no abre dos borradores y no retira un cerrado.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM reporte_servicio",
            "DELETE FROM orden_trabajo",
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM fabricante",
            "DELETE FROM marca",
            "DELETE FROM protocolo_tipo_equipo",
            "DELETE FROM protocolo",
            "DELETE FROM tipo_equipo",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM cliente"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class WorkOrderReportsAdapterTest {

    @Autowired private WorkOrderReportsAdapter adapter;
    @Autowired private WorkOrderAttendanceAdapter attendance;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    private record Sitio(UUID sede, UUID equipo, UUID otroEquipo) {
    }

    /** Una sede con dos equipos de un tipo que tiene dos protocolos asignados. */
    private Sitio unSitio() {
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social) VALUES (?, ?, 'NIT_juridico', ?)",
                cliente, unico(), "Hospital " + unico());
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
        for (int prioridad = 1; prioridad <= 2; prioridad++) {
            UUID protocolo = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO protocolo (k_id_protocolo, n_nombre, n_clave, t_descripcion) VALUES (?, ?, ?, 'Hacerlo')",
                    protocolo, "Paso " + prioridad, "paso-" + unico());
            jdbcTemplate.update(
                    "INSERT INTO protocolo_tipo_equipo (k_id_tipo_equipo, k_id_protocolo, i_prioridad) VALUES (?, ?, ?)",
                    tipo, protocolo, prioridad);
        }
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
        for (UUID unidad : List.of(uno, dos)) {
            jdbcTemplate.update(
                    "INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio) VALUES (?, ?, ?, ?)",
                    unidad, "S-" + unico(), modelo, area);
        }

        return new Sitio(sede, uno, dos);
    }

    private UUID unaOrden(UUID sede, String estado) {
        UUID orden = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento, n_periodicidad,
                                           t_tipo_servicio, t_estado_ejecucion, t_documento)
                VALUES (?, ?, ?, 'MENSUAL', 'PREVENTIVO', ?, ?::jsonb)
                """,
                orden, sede, LocalDate.now(), estado, "EJECUTADA".equals(estado) ? "{}" : null);
        return orden;
    }

    private void cerrar(UUID orden, UUID equipo) {
        jdbcTemplate.update(
                """
                UPDATE reporte_servicio SET t_estado_reporte = 'FINALIZADO', t_resultado = 'OPERATIVO', f_finalizado = ?
                WHERE k_id_orden_trabajo = ? AND k_id_equipo_cliente = ? AND b_estado_activo
                """,
                LocalDateTime.now(), orden, equipo);
    }

    private Integer activos(UUID orden, UUID equipo) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reporte_servicio WHERE k_id_orden_trabajo = ? AND k_id_equipo_cliente = ? AND b_estado_activo",
                Integer.class, orden, equipo);
    }

    @Test
    @DisplayName("abrir el borrador copia los pasos del protocolo, y abrirlo otra vez no abre otro")
    void abrirEsIdempotente() {
        Sitio sitio = unSitio();
        UUID orden = unaOrden(sitio.sede(), "CREADA");

        adapter.openDraft(orden, ServiceType.PREVENTIVO, sitio.equipo());
        adapter.openDraft(orden, ServiceType.PREVENTIVO, sitio.equipo());

        assertThat(activos(orden, sitio.equipo())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT sum(jsonb_array_length(t_contenido -> 'pasos')) FROM reporte_servicio"
                                + " WHERE k_id_orden_trabajo = ?",
                        Integer.class, orden))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("retirar un borrador saca el equipo; retirarlo otra vez no hace nada")
    void retirarUnBorrador() {
        Sitio sitio = unSitio();
        UUID orden = unaOrden(sitio.sede(), "EN_EJECUCION");
        adapter.openDraft(orden, ServiceType.PREVENTIVO, sitio.equipo());

        adapter.retireDraft(orden, sitio.equipo());
        adapter.retireDraft(orden, sitio.equipo());

        assertThat(activos(orden, sitio.equipo())).isZero();
        assertThat(adapter.activeReports(orden)).isEmpty();
    }

    @Test
    @DisplayName("un reporte cerrado no se retira: el equipo sigue en la orden")
    void unCerradoNoSeRetira() {
        Sitio sitio = unSitio();
        UUID orden = unaOrden(sitio.sede(), "EN_EJECUCION");
        adapter.openDraft(orden, ServiceType.PREVENTIVO, sitio.equipo());
        cerrar(orden, sitio.equipo());

        assertThatIllegalStateException().isThrownBy(() -> adapter.retireDraft(orden, sitio.equipo()))
                .withMessageContaining("se sustituye");
        assertThat(activos(orden, sitio.equipo())).isEqualTo(1);
    }

    @Test
    @DisplayName("los equipos de varias ordenes salen en una consulta, por orden, sin los retirados")
    void variasOrdenes() {
        Sitio sitio = unSitio();
        UUID una = unaOrden(sitio.sede(), "EN_EJECUCION");
        UUID otra = unaOrden(sitio.sede(), "EN_EJECUCION");
        UUID sinEquipos = unaOrden(sitio.sede(), "CREADA");
        adapter.openDraft(una, ServiceType.PREVENTIVO, sitio.equipo());
        adapter.openDraft(una, ServiceType.PREVENTIVO, sitio.otroEquipo());
        cerrar(una, sitio.otroEquipo());
        adapter.openDraft(otra, ServiceType.PREVENTIVO, sitio.equipo());
        adapter.retireDraft(otra, sitio.equipo());

        var porOrden = adapter.activeReports(List.of(una, otra, sinEquipos));

        assertThat(porOrden).containsOnlyKeys(una);
        assertThat(porOrden.get(una))
                .extracting(WorkOrderReportsPort.ActiveReport::idEquipoCliente, WorkOrderReportsPort.ActiveReport::cerrado)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(sitio.equipo(), false),
                        org.assertj.core.groups.Tuple.tuple(sitio.otroEquipo(), true));
        assertThat(porOrden.get(una)).allSatisfy(reporte ->
                assertThat(reporte.numero()).isEqualTo(reporte.idReporte().toString().substring(0, 6).toUpperCase()));
        assertThat(adapter.activeReports(List.of())).isEmpty();
        assertThat(adapter.workOrdersOf(sitio.equipo())).containsExactly(una);
    }

    @Test
    @DisplayName("una unidad esta en una orden sin ejecutar mientras tenga en ella un reporte activo")
    void enUnaOrdenSinEjecutar() {
        Sitio sitio = unSitio();
        UUID creada = unaOrden(sitio.sede(), "CREADA");
        adapter.openDraft(creada, ServiceType.PREVENTIVO, sitio.equipo());

        assertThat(attendance.inUnexecutedWorkOrder(sitio.equipo())).isTrue();
        assertThat(attendance.inUnexecutedWorkOrder(sitio.otroEquipo())).isFalse();

        adapter.retireDraft(creada, sitio.equipo());
        assertThat(attendance.inUnexecutedWorkOrder(sitio.equipo())).isFalse();
    }

    @Test
    @DisplayName("una orden ejecutada o cancelada ya no retiene a sus equipos")
    void ejecutadaOCanceladaNoRetiene() {
        Sitio sitio = unSitio();
        UUID ejecutada = unaOrden(sitio.sede(), "EJECUTADA");
        adapter.openDraft(ejecutada, ServiceType.PREVENTIVO, sitio.equipo());
        UUID cancelada = unaOrden(sitio.sede(), "EN_EJECUCION");
        adapter.openDraft(cancelada, ServiceType.PREVENTIVO, sitio.otroEquipo());
        jdbcTemplate.update("UPDATE orden_trabajo SET b_estado_activo = false WHERE k_id_orden_trabajo = ?", cancelada);

        assertThat(attendance.inUnexecutedWorkOrder(sitio.equipo())).isFalse();
        assertThat(attendance.inUnexecutedWorkOrder(sitio.otroEquipo())).isFalse();
    }
}
