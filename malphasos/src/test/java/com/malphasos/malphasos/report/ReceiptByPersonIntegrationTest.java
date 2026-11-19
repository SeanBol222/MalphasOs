package com.malphasos.malphasos.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.CodigoIsoLibre;
import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.report.application.model.ReportDocumentView;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.ReceiptScope;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.SignReceiptCommand;
import com.malphasos.malphasos.report.domain.exception.ReceiverNotSupervisorException;
import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.time.LocalDate;
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
 * Quien recibe como persona registrada (V55, 2026-11-01), de extremo a extremo: el supervisor del cliente
 * que recibe y aprueba a la vez, como el doctor de un consultorio. Cruza el reporte, la orden, el cliente
 * que dice quién supervisa y la persona con su nombre, y lo que hay que ver es que el nombre no se copia y
 * aun así llega al documento.
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
            "DELETE FROM tipo_equipo",
            "DELETE FROM area_servicio",
            "DELETE FROM sede",
            "DELETE FROM supervisor_cliente",
            "DELETE FROM cliente",
            "DELETE FROM persona WHERE n_primer_apellido = 'Recibido'"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ReceiptByPersonIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 4, 5, 6};

    @Autowired private ServiceReportServicePort reportes;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String unico() {
        return String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
    }

    private record Escenario(UUID cliente, UUID uno, UUID dos, UUID supervisor) {
    }

    private UUID unaPersona(String tipo, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO persona (k_identificador, k_cedula, n_primer_nombre, n_primer_apellido, t_tipo_persona)
                VALUES (?, ?, ?, 'Recibido', ?)
                """,
                id, unico(), nombre, tipo);
        return id;
    }

    /**
     * Un cliente con su supervisora y una orden en ejecución con dos reportes en borrador: un borrador
     * compone su documento en vivo, y uno cerrado necesita el congelado desde V51. El recibido se firma
     * igual en los dos.
     */
    private Escenario unaOrdenConDosReportes() {
        UUID cliente = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO cliente (k_id_cliente, k_documento, n_tipo_identificacion, n_razon_social) VALUES (?, ?, 'NIT_juridico', ?)",
                cliente, unico(), "Consultorio " + unico());
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

        UUID orden = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO orden_trabajo (k_id_orden_trabajo, k_id_sede, f_fecha_mantenimiento,
                                           n_periodicidad, t_tipo_servicio, t_estado_ejecucion)
                VALUES (?, ?, ?, 'MENSUAL', 'CORRECTIVO', 'EN_EJECUCION')
                """,
                orden, sede, LocalDate.now());
        UUID[] ids = new UUID[2];
        for (int i = 0; i < 2; i++) {
            UUID unidad = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO equipo_cliente (k_id_equipo_cliente, k_serie, k_id_modelo, k_id_area_servicio) VALUES (?, ?, ?, ?)",
                    unidad, "Serie-" + unico(), modelo, area);
            ids[i] = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente,
                                                  t_estado_reporte)
                    VALUES (?, ?, ?, 'BORRADOR')
                    """,
                    ids[i], orden, unidad);
        }

        UUID supervisor = unaPersona("SUPERVISOR", "Dora");
        jdbcTemplate.update(
                "INSERT INTO supervisor_cliente (k_identificador, k_id_cliente, n_cargo) VALUES (?, ?, 'Médica propietaria')",
                supervisor, cliente);

        return new Escenario(cliente, ids[0], ids[1], supervisor);
    }

    private SignReceiptCommand porPersona(UUID reporte, UUID persona, ReceiptScope alcance) {
        return new SignReceiptCommand(reporte, null, null, null, PNG, alcance, persona);
    }

    @Test
    @DisplayName("la supervisora firma el recibido de toda la orden: su ficha y no su nombre copiado")
    void firmaLaSupervisora() {
        Escenario e = unaOrdenConDosReportes();

        reportes.signReceipt(porPersona(e.uno(), e.supervisor(), ReceiptScope.ORDEN));

        for (UUID reporte : new UUID[] {e.uno(), e.dos()}) {
            assertThat(jdbcTemplate.queryForMap(
                            "SELECT k_id_recibe, t_contenido ->> 'recibe' AS recibe, k_huella_firma_recibe"
                                    + " FROM reporte_servicio WHERE k_id_reporte_servicio = ?",
                            reporte))
                    .containsEntry("k_id_recibe", e.supervisor())
                    .containsEntry("recibe", null)
                    .doesNotContainEntry("k_huella_firma_recibe", null);
        }
    }

    @Test
    @DisplayName("el documento la nombra por su ficha, con el cargo con que supervisa al cliente")
    void elDocumentoLaNombra() {
        Escenario e = unaOrdenConDosReportes();
        reportes.signReceipt(porPersona(e.uno(), e.supervisor(), ReceiptScope.REPORTE));

        ReportDocumentView vista = reportes.document(e.uno(), ReadScope.unrestricted());

        assertThat(vista.recibe()).isEqualTo(new Handover("Dora Recibido",
                jdbcTemplate.queryForObject("SELECT k_cedula FROM persona WHERE k_identificador = ?", String.class,
                        e.supervisor()),
                "Médica propietaria"));
        assertThat(vista.firmaRecibe()).isNotNull();
    }

    @Test
    @DisplayName("quien no supervisa a ese cliente no firma como persona registrada: se la escribe por su nombre")
    void soloSuSupervisora() {
        Escenario e = unaOrdenConDosReportes();
        Escenario otro = unaOrdenConDosReportes();
        UUID ingeniero = unaPersona("ENGINEER", "Ivan");

        for (UUID quien : new UUID[] {otro.supervisor(), ingeniero}) {
            assertThatThrownBy(() -> reportes.signReceipt(porPersona(e.uno(), quien, ReceiptScope.REPORTE)))
                    .isInstanceOf(ReceiverNotSupervisorException.class);
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT k_huella_firma_recibe FROM reporte_servicio WHERE k_id_reporte_servicio = ?",
                        String.class, e.uno()))
                .isNull();
    }

    @Test
    @DisplayName("una persona registrada o un nombre escrito, no las dos cosas, y el esquema tampoco lo admite")
    void noLasDosCosas() {
        Escenario e = unaOrdenConDosReportes();

        assertThatThrownBy(() -> reportes.signReceipt(new SignReceiptCommand(
                        e.uno(), "Luis", null, null, PNG, ReceiptScope.REPORTE, e.supervisor())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no las dos cosas");
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE reporte_servicio SET k_id_recibe = ?, t_contenido = jsonb_set(t_contenido, '{recibe}',"
                                + " '{\"nombre\": \"Luis\", \"cedula\": null, \"cargo\": null}')"
                                + " WHERE k_id_reporte_servicio = ?",
                        e.supervisor(), e.uno()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("por su nombre sigue funcionando como antes de V55")
    void porSuNombre() {
        Escenario e = unaOrdenConDosReportes();

        reportes.signReceipt(new SignReceiptCommand(e.uno(), "Luis Gómez", null, "Jefe de sede", PNG, ReceiptScope.REPORTE));

        assertThat(reportes.document(e.uno(), ReadScope.unrestricted()).recibe())
                .isEqualTo(new Handover("Luis Gómez", null, "Jefe de sede"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT k_id_recibe FROM reporte_servicio WHERE k_id_reporte_servicio = ?", UUID.class, e.uno()))
                .isNull();
    }
}
