package com.malphasos.malphasos.workorder;

import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordIntakeCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FinishServiceReportCommand;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.person.application.ports.input.PersonSignatureServicePort;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.ReplaceServiceReportCommand;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderApprovalServicePort;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.services.approval.commands.ApproveFromAccountCommand;
import com.malphasos.malphasos.workorder.application.services.approval.commands.ApproveOnSiteCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ExecuteWorkOrderCommand;
import com.malphasos.malphasos.workorder.domain.approval.WorkOrderApproval;
import com.malphasos.malphasos.workorder.domain.exception.NoSavedSignatureException;
import com.malphasos.malphasos.workorder.domain.exception.NotSupervisorOfClientException;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * La aprobación de una orden (V54, 2026-11-01), de extremo a extremo y contra la base real: cruza cuatro
 * módulos —la orden, el cliente que dice quién supervisa, la persona con su firma y el reporte que la
 * retira al sustituirse—, y lo que hay que ver es que el cableado llega. Las reglas una a una están aquí
 * también, porque ninguna se puede probar sin las otras tres piezas.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
        statements = {
            "DELETE FROM aprobacion_orden",
            "DELETE FROM firma_persona",
            "DELETE FROM paso_reporte",
            "DELETE FROM dato_verificacion",
            "DELETE FROM accesorio_reporte",
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
            "DELETE FROM supervisor_cliente",
            "DELETE FROM cliente",
            "DELETE FROM persona WHERE n_primer_apellido = 'Aprobacion'"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class WorkOrderApprovalIntegrationTest {

    /** Lo único que se mira de una firma son los ocho bytes con que empieza todo PNG. */
    private static final byte[] PNG_FALSO = Arrays.copyOf(
            new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3}, 11);

    @Autowired private WorkOrderApprovalServicePort aprobaciones;
    @Autowired private WorkOrderServicePort ordenes;
    @Autowired private ServiceReportServicePort reportes;
    @Autowired private PersonSignatureServicePort firmas;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    private record Escenario(UUID cliente, UUID orden, UUID reporte, UUID supervisor, UUID ingeniero) {
    }

    private UUID unaPersona(String tipo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_primer_apellido, t_tipo_persona)
                VALUES (?, ?, 'Ana', 'Aprobacion', ?)
                """,
                id, unico(), tipo);
        return id;
    }

    /** Un cliente con su supervisor, y una orden suya ejecutada con un reporte cerrado. */
    private Escenario unaOrdenEjecutada() {
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
        UUID unidad = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio) VALUES (?, ?, ?, ?)",
                unidad, "Serie-" + unico(), modelo, area);

        UUID ingeniero = unaPersona("ENGINEER");
        UUID orden = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento,
                                           n_periodicidad, t_tipo_servicio, t_estado_ejecucion, k_id_ingeniero)
                VALUES (?, ?, ?, 'MENSUAL', 'CORRECTIVO', 'EN_EJECUCION', ?)
                """,
                orden, sede, LocalDate.now(), ingeniero);
        UUID reporte = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente) VALUES (?, ?, ?)",
                reporte, orden, unidad);
        // Cerrado y ejecutada por el servicio y no por SQL: así llevan el documento congelado que el esquema
        // exige (V49 y V51), y el informe se puede componer.
        reportes.fill(new FillServiceReportCommand(reporte, null, null, "Limpieza", null, ServiceResult.OPERATIVO));
        reportes.recordIntake(new RecordIntakeCommand(
                reporte, IntakeState.CONFORME, null, IntakeState.CONFORME, null, List.of()));
        reportes.finish(new FinishServiceReportCommand(reporte));
        ordenes.execute(new ExecuteWorkOrderCommand(orden));

        UUID supervisor = unaPersona("SUPERVISOR");
        jdbcTemplate.update(
                "INSERT INTO supervisor_cliente (k_identificador, k_id_cliente, n_cargo) VALUES (?, ?, 'Ingeniera biomédica')",
                supervisor, cliente);

        return new Escenario(cliente, orden, reporte, supervisor, ingeniero);
    }

    private WorkOrderApproval enSitio(Escenario e, UUID supervisor, UUID operador) {
        return aprobaciones.approveOnSite(
                new ApproveOnSiteCommand(e.orden(), supervisor, PNG_FALSO, operador), ReadScope.unrestricted());
    }

    @Test
    @DisplayName("en sitio queda vigente, con la firma guardada por su huella y el ingeniero como operador")
    void enSitio() {
        Escenario e = unaOrdenEjecutada();

        WorkOrderApproval aprobacion = enSitio(e, e.supervisor(), e.ingeniero());

        assertThat(aprobacion.fromOwnAccount()).isFalse();
        assertThat(aprobaciones.findActive(e.orden(), ReadScope.unrestricted()))
                .get()
                .satisfies(vigente -> {
                    assertThat(vigente.getIdSupervisor()).isEqualTo(e.supervisor());
                    assertThat(vigente.getIdOperador()).isEqualTo(e.ingeniero());
                });
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM imagen_documento WHERE k_huella = ?", Integer.class,
                        aprobacion.getHuellaFirma()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("desde su cuenta usa la firma que guardó, y el operador es el propio supervisor")
    void desdeSuCuenta() {
        Escenario e = unaOrdenEjecutada();
        firmas.save(e.supervisor(), PNG_FALSO);

        WorkOrderApproval aprobacion = aprobaciones.approveFromAccount(
                new ApproveFromAccountCommand(e.orden(), e.supervisor()), ReadScope.ofClients(Set.of(e.cliente())));

        assertThat(aprobacion.fromOwnAccount()).isTrue();
        assertThat(aprobacion.getIdOperador()).isEqualTo(e.supervisor());
    }

    @Test
    @DisplayName("sin firma guardada no se aprueba desde la cuenta, y lo dice")
    void sinFirmaGuardada() {
        Escenario e = unaOrdenEjecutada();

        assertThatThrownBy(() -> aprobaciones.approveFromAccount(
                        new ApproveFromAccountCommand(e.orden(), e.supervisor()), ReadScope.unrestricted()))
                .isInstanceOf(NoSavedSignatureException.class);
        assertThat(aprobaciones.findActive(e.orden(), ReadScope.unrestricted())).isEmpty();
    }

    @Test
    @DisplayName("solo aprueba un supervisor activo de ese cliente: ni el de otro, ni un relevado, ni un ingeniero")
    void soloSuSupervisor() {
        Escenario e = unaOrdenEjecutada();
        Escenario otro = unaOrdenEjecutada();
        UUID relevado = unaPersona("SUPERVISOR");
        jdbcTemplate.update(
                "INSERT INTO supervisor_cliente (k_identificador, k_id_cliente, b_estado_activo) VALUES (?, ?, false)",
                relevado, e.cliente());

        for (UUID quien : new UUID[] {otro.supervisor(), relevado, e.ingeniero()}) {
            assertThatThrownBy(() -> enSitio(e, quien, e.ingeniero()))
                    .isInstanceOf(NotSupervisorOfClientException.class);
        }
        assertThat(aprobaciones.findActive(e.orden(), ReadScope.unrestricted())).isEmpty();
    }

    @Test
    @DisplayName("nombrado pero ya no SUPERVISOR no aprueba: el nombramiento solo no basta")
    void nombradoConOtroTipo() {
        // Sin esta, la regla del tipo no la ejercía nadie: en la prueba de arriba al ingeniero lo rechaza
        // antes no estar nombrado, que es la otra regla.
        Escenario e = unaOrdenEjecutada();
        jdbcTemplate.update("UPDATE persona SET t_tipo_persona = 'ENGINEER' WHERE k_identificador = ?", e.supervisor());

        assertThatThrownBy(() -> enSitio(e, e.supervisor(), e.ingeniero()))
                .isInstanceOf(NotSupervisorOfClientException.class);
    }

    @Test
    @DisplayName("una orden sin ejecutar no se aprueba: se aprueba el trabajo terminado")
    void sinEjecutar() {
        Escenario e = unaOrdenEjecutada();
        jdbcTemplate.update(
                "UPDATE orden_trabajo SET t_estado_ejecucion = 'EN_EJECUCION', t_documento = NULL WHERE k_id_orden_trabajo = ?",
                e.orden());

        assertThatThrownBy(() -> enSitio(e, e.supervisor(), e.ingeniero()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no está ejecutada");
    }

    @Test
    @DisplayName("aprobar dos veces es un conflicto, y el esquema tampoco admite dos vigentes")
    void dosVeces() {
        Escenario e = unaOrdenEjecutada();
        WorkOrderApproval primera = enSitio(e, e.supervisor(), e.ingeniero());

        assertThatThrownBy(() -> enSitio(e, e.supervisor(), e.ingeniero()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya está aprobada");
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO aprobacion_orden (k_id_aprobacion, k_id_orden_trabajo, k_id_supervisor,
                                                      k_huella_firma, f_aprobada)
                        VALUES (?, ?, ?, ?, now())
                        """,
                        UUID.randomUUID(), e.orden(), e.supervisor(), primera.getHuellaFirma()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("en la sesión del SuperUsuario, que no tiene ficha, el operador queda vacío")
    void sinFichaDeOperador() {
        Escenario e = unaOrdenEjecutada();

        WorkOrderApproval aprobacion = enSitio(e, e.supervisor(), UUID.randomUUID());

        assertThat(aprobacion.getIdOperador()).isNull();
        assertThat(aprobacion.fromOwnAccount()).isFalse();
    }

    @Test
    @DisplayName("por aprobar: las ejecutadas sin aprobación vigente, y a un supervisor solo las de su cliente")
    void porAprobar() {
        Escenario aprobada = unaOrdenEjecutada();
        Escenario pendiente = unaOrdenEjecutada();
        Escenario enCurso = unaOrdenEjecutada();
        enSitio(aprobada, aprobada.supervisor(), aprobada.ingeniero());
        jdbcTemplate.update(
                "UPDATE orden_trabajo SET t_estado_ejecucion = 'EN_EJECUCION', t_documento = NULL WHERE k_id_orden_trabajo = ?",
                enCurso.orden());

        // La que sigue en ejecución no está por aprobar: todavía no hay trabajo terminado que revisar.
        assertThat(aprobaciones.pending(ReadScope.unrestricted()))
                .extracting(WorkOrder::getId)
                .contains(pendiente.orden())
                .doesNotContain(aprobada.orden(), enCurso.orden());
        assertThat(aprobaciones.pending(ReadScope.ofClients(Set.of(aprobada.cliente())))).isEmpty();
    }

    @Test
    @DisplayName("sustituir un reporte retira la aprobación, que queda como historial, y la orden vuelve a por aprobar")
    void sustituirRetira() {
        Escenario e = unaOrdenEjecutada();
        enSitio(e, e.supervisor(), e.ingeniero());

        reportes.replace(new ReplaceServiceReportCommand(e.reporte()));

        assertThat(aprobaciones.findActive(e.orden(), ReadScope.unrestricted())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM aprobacion_orden WHERE k_id_orden_trabajo = ? AND NOT b_estado_activo",
                        Integer.class, e.orden()))
                .isEqualTo(1);
        assertThat(aprobaciones.pending(ReadScope.ofClients(Set.of(e.cliente()))))
                .extracting(WorkOrder::getId)
                .containsExactly(e.orden());
    }

    @Test
    @DisplayName("el informe lleva a quien aprobó, con el cargo con que supervisa, su firma y el día (V54)")
    void enElInforme() {
        Escenario e = unaOrdenEjecutada();
        assertThat(reportes.document(e.reporte(), ReadScope.unrestricted()).aprobacion()).isNull();

        enSitio(e, e.supervisor(), e.ingeniero());

        assertThat(reportes.document(e.reporte(), ReadScope.unrestricted()).aprobacion())
                .satisfies(aprobacion -> {
                    assertThat(aprobacion.nombre()).isEqualTo("Ana Aprobacion");
                    assertThat(aprobacion.cargo()).isEqualTo("Ingeniera biomédica");
                    assertThat(aprobacion.firma().contenido()).isEqualTo(PNG_FALSO);
                    assertThat(aprobacion.fecha()).isEqualTo(LocalDate.now());
                });
    }

    @Test
    @DisplayName("sustituido un reporte, el informe de los demás vuelve a dejar la línea para firmar a mano")
    void sustituidoSaleDelInforme() {
        Escenario e = unaOrdenEjecutada();
        enSitio(e, e.supervisor(), e.ingeniero());

        reportes.replace(new ReplaceServiceReportCommand(e.reporte()));

        assertThat(reportes.document(e.reporte(), ReadScope.unrestricted()).aprobacion()).isNull();
    }
}
