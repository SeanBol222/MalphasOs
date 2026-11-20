package com.malphasos.malphasos.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.UseReferenceStandardCommand;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationStandard;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderStandardsServicePort;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AddEquipmentToWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.RemoveEquipmentFromWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrderStandards.commands.AssignWorkOrderStandardsCommand;
import com.malphasos.malphasos.workorder.domain.workOrderStandards.WorkOrderStandards;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * Los patrones de la orden de extremo a extremo (V60), contra la base real: la orden los elige por tipo, pone
 * sola el único vigente, y cada cambio llega a los borradores de ese tipo, marcado, sin tocar lo que el
 * ingeniero puso a mano. Las pruebas de los servicios usan dobles; esta dice que las piezas encajan.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM patron_orden_trabajo",
            "DELETE FROM patron_verificacion",
            "DELETE FROM reporte_servicio",
            "DELETE FROM orden_trabajo",
            "DELETE FROM equipo_cliente",
            "DELETE FROM modelo",
            "DELETE FROM equipo",
            "DELETE FROM fabricante",
            "DELETE FROM marca",
            "DELETE FROM equipo_patron",
            "DELETE FROM verificacion_tipo_equipo",
            "DELETE FROM protocolo_tipo_equipo",
            "DELETE FROM tipo_equipo",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM cliente"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class WorkOrderStandardsIntegrationTest {

    @Autowired private WorkOrderServicePort ordenes;
    @Autowired private WorkOrderStandardsServicePort patronesDeLaOrden;
    @Autowired private ServiceReportServicePort reportes;
    @Autowired private ServiceReportPersistencePort almacenDeReportes;
    @Autowired private ClientEquipmentServicePort equipos;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID area;
    private UUID orden;

    /** Un monitor con presión y frecuencia; una balanza con masa. */
    private UUID monitor;
    private UUID presion;
    private UUID frecuencia;
    private UUID balanza;

    private UUID monitorUno;
    private UUID monitorDos;
    private UUID balanzaUno;

    /** El simulador vale para presión y frecuencia, el manómetro solo para presión, las pesas para la balanza. */
    private UUID simulador;
    private UUID manometro;
    private UUID pesas;

    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    @BeforeEach
    void unaOrdenEnEjecucion() {
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
        area = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO area_servicio (k_id_area_servicio, n_nombre_area, k_id_sede) VALUES (?, ?, ?)",
                area, "Area " + unico(), sede);

        monitor = unTipo();
        presion = unaVerificacion(monitor, "mmHg");
        frecuencia = unaVerificacion(monitor, "lpm");
        balanza = unTipo();
        UUID masa = unaVerificacion(balanza, "kg");

        UUID modeloMonitor = unModelo(monitor);
        UUID modeloBalanza = unModelo(balanza);
        monitorUno = unaUnidad(modeloMonitor);
        monitorDos = unaUnidad(modeloMonitor);
        balanzaUno = unaUnidad(modeloBalanza);

        simulador = unPatron();
        manometro = unPatron();
        pesas = unPatron();
        jdbcTemplate.update("INSERT INTO patron_verificacion VALUES (?, ?), (?, ?), (?, ?), (?, ?)",
                presion, simulador, presion, manometro, frecuencia, simulador, masa, pesas);

        orden = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento,
                                           n_periodicidad, t_tipo_servicio, t_estado_ejecucion)
                VALUES (?, ?, ?, 'MENSUAL', 'CORRECTIVO', 'EN_EJECUCION')
                """,
                orden, sede, LocalDate.now());
    }

    private UUID unTipo() {
        UUID tipo = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tipo_equipo (k_id_tipo_equipo, n_nombre_tipo_equipo, t_definicion_tecnica,
                                         t_recomendaciones_cuidado, t_tecnologia_predominante,
                                         m_valor_unitario_mantenimiento)
                VALUES (?, ?, 'Definicion', 'Cuidados', 'Electronica', 150000)
                """,
                tipo, "Tipo " + unico());
        return tipo;
    }

    private UUID unaVerificacion(UUID tipo, String simbolo) {
        UUID verificacion = UUID.randomUUID();
        int filas = jdbcTemplate.update(
                """
                INSERT INTO verificacion_tipo_equipo (k_id_verificacion, k_id_tipo_equipo, k_id_unidad_medida,
                                                      n_modalidad_verificacion)
                SELECT ?, ?, k_id_unidad_medida, 'patron_equipo_variable'
                FROM unidad_medida WHERE n_simbolo_unidad = ?
                """,
                verificacion, tipo, simbolo);
        assertThat(filas).describedAs("la unidad " + simbolo + " esta sembrada").isEqualTo(1);
        return verificacion;
    }

    private UUID unModelo(UUID tipo) {
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
        return modelo;
    }

    private UUID unaUnidad(UUID modelo) {
        UUID unidad = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio) VALUES (?, ?, ?, ?)",
                unidad, "Serie-" + unico(), modelo, area);
        return unidad;
    }

    private UUID unPatron() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO equipo_patron (k_id_equipo_patron, n_nombre, k_serie, n_certificado,
                                           f_vencimiento_certificado)
                VALUES (?, 'Patron', ?, 'C-1', ?)
                """,
                id, "P-" + unico(), LocalDate.now().plusYears(1));
        return id;
    }

    private void anadir(UUID unidad) {
        ordenes.addEquipment(new AddEquipmentToWorkOrderCommand(orden, unidad));
    }

    private ServiceReport borradorDe(UUID unidad) {
        return almacenDeReportes.findActiveByWorkOrderAndEquipment(orden, unidad).orElseThrow();
    }

    private Set<UUID> patronesEnLaOrden(UUID tipo) {
        return Set.copyOf(jdbcTemplate.queryForList(
                "SELECT k_id_equipo_patron FROM patron_orden_trabajo WHERE k_id_orden_trabajo = ? AND k_id_tipo_equipo = ?",
                UUID.class, orden, tipo));
    }

    @Test
    @DisplayName("el tipo de varios equipos sale en una consulta, sin los que no existen")
    void losTiposDeVariosEquipos() {
        UUID inventado = UUID.randomUUID();

        assertThat(equipos.equipmentTypesOf(List.of(monitorUno, balanzaUno, inventado)))
                .isEqualTo(Map.of(monitorUno, monitor, balanzaUno, balanza));
        assertThat(equipos.equipmentTypesOf(List.of())).isEmpty();
    }

    @Test
    @DisplayName("con un solo patron vigente la orden lo pone sola y llega al borrador; con dos, no elige")
    void elUnicoVigente() {
        anadir(balanzaUno);
        anadir(monitorUno);

        assertThat(patronesEnLaOrden(balanza)).containsExactly(pesas);
        assertThat(borradorDe(balanzaUno).patronesActivos())
                .extracting(VerificationStandard::idEquipoPatron, VerificationStandard::desdeOrden)
                .containsExactly(tuple(pesas, true));

        assertThat(patronesEnLaOrden(monitor)).isEmpty();
        assertThat(borradorDe(monitorUno).patronesActivos()).isEmpty();
    }

    @Test
    @DisplayName("cambiarlos llega a los borradores del tipo, a su verificacion, y deja lo puesto a mano")
    void cambiarlosLlegaALosBorradores() {
        anadir(monitorUno);
        anadir(monitorDos);
        anadir(balanzaUno);
        reportes.useReferenceStandard(new UseReferenceStandardCommand(borradorDe(monitorUno).getId(), presion, manometro));

        patronesDeLaOrden.assign(new AssignWorkOrderStandardsCommand(orden, monitor, List.of(simulador, manometro)));

        assertThat(borradorDe(monitorUno).patronesActivos())
                .extracting(VerificationStandard::idVerificacion, VerificationStandard::idEquipoPatron,
                        VerificationStandard::desdeOrden)
                .containsExactlyInAnyOrder(
                        tuple(presion, manometro, false),
                        tuple(presion, simulador, true),
                        tuple(frecuencia, simulador, true));
        assertThat(borradorDe(monitorDos).patronesActivos())
                .extracting(VerificationStandard::idVerificacion, VerificationStandard::idEquipoPatron)
                .containsExactlyInAnyOrder(
                        tuple(presion, manometro), tuple(presion, simulador), tuple(frecuencia, simulador));
        // La balanza es de otro tipo: lo del monitor no le llega.
        assertThat(borradorDe(balanzaUno).patronesActivos())
                .extracting(VerificationStandard::idEquipoPatron).containsExactly(pesas);

        patronesDeLaOrden.assign(new AssignWorkOrderStandardsCommand(orden, monitor, List.of()));

        assertThat(borradorDe(monitorUno).patronesActivos())
                .extracting(VerificationStandard::idVerificacion, VerificationStandard::idEquipoPatron)
                .containsExactly(tuple(presion, manometro));
        assertThat(borradorDe(monitorDos).patronesActivos()).isEmpty();
    }

    @Test
    @DisplayName("un reporte cerrado del tipo no recibe nada: cuenta lo que se uso")
    void unCerradoNoSeToca() {
        anadir(monitorUno);
        UUID cerrado = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,
                                              t_estado_reporte, t_resultado, f_finalizado)
                VALUES (?, ?, ?, 'FINALIZADO', 'OPERATIVO', now())
                """,
                cerrado, orden, monitorDos);

        patronesDeLaOrden.assign(new AssignWorkOrderStandardsCommand(orden, monitor, List.of(simulador)));

        assertThat(borradorDe(monitorUno).patronesActivos()).hasSize(2);
        assertThat(borradorDe(monitorDos).patronesActivos()).isEmpty();
    }

    @Test
    @DisplayName("se leen uno por tipo de la orden, y con el ultimo equipo de un tipo salen sus patrones")
    void leerlosYSoltarlos() {
        anadir(monitorUno);
        anadir(balanzaUno);

        assertThat(patronesDeLaOrden.standardsOf(orden, ReadScope.unrestricted()))
                .extracting(WorkOrderStandards::getIdTipoEquipo, WorkOrderStandards::getPatrones)
                .containsExactlyInAnyOrder(tuple(monitor, Set.of()), tuple(balanza, Set.of(pesas)));

        ordenes.removeEquipment(new RemoveEquipmentFromWorkOrderCommand(orden, balanzaUno));

        assertThat(patronesEnLaOrden(balanza)).isEmpty();
        assertThat(patronesDeLaOrden.standardsOf(orden, ReadScope.unrestricted()))
                .extracting(WorkOrderStandards::getIdTipoEquipo).containsExactly(monitor);
    }
}
