package com.malphasos.malphasos.dataload.infrastructure.output.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.dataload.EjemplosDeCarga;
import com.malphasos.malphasos.dataload.UbicacionDelEjemplo;
import com.malphasos.malphasos.dataload.application.exception.DataLoadRejectedException;
import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.ports.input.DataLoadServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.application.ports.output.PersonSignaturePersistencePort;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonSignature;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderPersistencePort;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * La carga completa del ejemplo contra PostgreSQL, en el orden decidido: el catálogo solo, la firma del
 * ingeniero guardada, y después catálogo, cliente e historial juntos. Todo en una transacción que se
 * deshace al terminar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class HistoryLoadIntegrationTest {

    private static final byte[] FIRMA = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 42};

    @Autowired private DataLoadServicePort carga;
    @Autowired private ClientPersistencePort clientes;
    @Autowired private WorkOrderPersistencePort ordenes;
    @Autowired private ServiceReportPersistencePort reportes;
    @Autowired private PersonPersistencePort personas;
    @Autowired private PersonSignaturePersistencePort firmas;
    @Autowired private EquipmentTypePersistencePort tipos;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final DataLoadFile CATALOGO = EjemplosDeCarga.archivo(EjemplosDeCarga.CATALOGO);
    private static final DataLoadFile CLIENTE = EjemplosDeCarga.archivo(EjemplosDeCarga.CLIENTE);
    private static final DataLoadFile HISTORIAL = EjemplosDeCarga.archivo(EjemplosDeCarga.HISTORIAL);

    /** La ubicación del ejemplo, si otra clase la borró; el catálogo cargado; y la firma de su ingeniero. */
    @BeforeEach
    void elCatalogoYLaFirma() {
        UbicacionDelEjemplo.crear(jdbcTemplate);

        assertThat(carga.apply(List.of(CATALOGO)).errores()).isEmpty();
        firmas.save(ingeniero().getIdentificador(), new PersonSignature(FIRMA, LocalDateTime.of(2026, 11, 7, 9, 0)));
    }

    private Person ingeniero() {
        return personas.findAll().stream().filter(p -> p.getCedula().equals("1000000001")).findFirst().orElseThrow();
    }

    private static DataLoadFile historial(String de, String a) {
        String texto = EjemplosDeCarga.texto(EjemplosDeCarga.HISTORIAL);
        String cambiado = texto.replaceFirst(Pattern.quote(de), Matcher.quoteReplacement(a));
        assertThat(cambiado).describedAs("El ejemplo tiene que traer " + de).isNotEqualTo(texto);
        return new DataLoadFile("historial.json", cambiado.getBytes(StandardCharsets.UTF_8));
    }

    private List<WorkOrder> susOrdenes() {
        return ordenes.findByClient(clientes.findByDocumento("900000001-1").orElseThrow().getId()).stream()
                .sorted(Comparator.comparing(WorkOrder::getFechaMantenimiento)).toList();
    }

    private List<ServiceReport> deLaOrden(WorkOrder orden) {
        return reportes.findByWorkOrder(orden.getId()).stream()
                .sorted(Comparator.comparing(ServiceReport::getFinalizado)).toList();
    }

    @Test
    @DisplayName("las ordenes entran ejecutadas, con su documento congelado tarde y sin fecha de ejecucion")
    void lasOrdenes() {
        assertThat(carga.apply(List.of(CATALOGO, CLIENTE, HISTORIAL)).errores()).isEmpty();

        assertThat(susOrdenes()).hasSize(2).allSatisfy(o -> {
            assertThat(o.getEstadoEjecucion()).isEqualTo(ExecutionState.EJECUTADA);
            assertThat(o.getIdIngeniero()).isEqualTo(ingeniero().getIdentificador());
            assertThat(o.getDocumento()).isNotNull();
            assertThat(o.isDocumentoTardio()).isTrue();
            assertThat(o.getDocumento().fechaEjecucion()).isNull();
        });
        assertThat(susOrdenes()).extracting(WorkOrder::getFechaMantenimiento)
                .containsExactly(LocalDate.of(2025, 3, 10), LocalDate.of(2025, 5, 2));
        assertThat(susOrdenes().getFirst().getDocumento().equipos()).hasSize(2);
    }

    @Test
    @DisplayName("los reportes entran cerrados en su dia, congelados tarde con la firma guardada del ingeniero")
    void losReportes() {
        assertThat(carga.apply(List.of(CATALOGO, CLIENTE, HISTORIAL)).errores()).isEmpty();

        List<ServiceReport> primeros = deLaOrden(susOrdenes().getFirst());
        assertThat(primeros).extracting(ServiceReport::getFinalizado)
                .containsExactly(LocalDateTime.of(2025, 3, 10, 12, 0), LocalDateTime.of(2025, 3, 10, 15, 30));
        String huella = new DocumentImage(FIRMA, "image/png").huella();
        assertThat(primeros).allSatisfy(r -> {
            assertThat(r.getEstado()).isEqualTo(ReportState.FINALIZADO);
            assertThat(r.isDocumentoTardio()).isTrue();
            assertThat(r.getDocumento().huellaFirmaIngeniero()).isEqualTo(huella);
        });
        assertThat(primeros.getFirst().getPasos()).hasSize(3);
        assertThat(primeros.getFirst().getDocumento().patrones()).extracting(p -> p.ficha().certificado())
                .describedAs("el 10 de marzo de 2025 el manómetro ya tenía su certificado de 2025").containsExactly("CAL-2025-014");
        assertThat(primeros.getFirst().getLecturas()).hasSize(5).extracting(VerificationReading::secuencia).containsOnly(1);
        assertThat(primeros.get(1).getLecturas()).hasSize(6).extracting(VerificationReading::secuencia)
                .containsExactlyInAnyOrder(1, 2, 3, 1, 2, 3);

        ServiceReport correctivo = deLaOrden(susOrdenes().get(1)).getFirst();
        assertThat(correctivo.getRecibe().nombre()).isEqualTo("Carlos Pérez");
        assertThat(correctivo.getPasos()).isEmpty();
    }

    @Test
    @DisplayName("el informe lleva el certificado del patron que estaba vigente el dia del servicio")
    void elCertificadoDeEseDia() {
        DataLoadFile de2024 = historial("\"finalizado\": \"2025-03-10\"", "\"finalizado\": \"2024-06-01\"");

        assertThat(carga.apply(List.of(CATALOGO, CLIENTE, de2024)).errores()).isEmpty();

        ServiceReport viejo = deLaOrden(susOrdenes().getFirst()).getFirst();
        assertThat(viejo.getDocumento().patrones()).singleElement().extracting(ReportDocument.Patron::ficha).satisfies(f -> {
            assertThat(f.certificado()).isEqualTo("CAL-2024-001");
            assertThat(f.vencimiento()).isEqualTo(LocalDate.of(2025, 2, 1));
            assertThat(f.fechaCalibracion()).isEqualTo(LocalDate.of(2024, 2, 1));
        });
    }

    @Test
    @DisplayName("una lectura en un punto anterior apunta al punto retirado")
    void enUnPuntoAnterior() {
        DataLoadFile en300 = historial("\"punto\": 50,", "\"punto\": 300,");

        assertThat(carga.apply(List.of(CATALOGO, CLIENTE, en300)).errores()).isEmpty();

        EquipmentType tensiometro = tipos.findAll().stream().filter(t -> t.getNombre().equals("Tensiómetro análogo"))
                .findFirst().orElseThrow();
        VerificationPoint retirado = tensiometro.getVerificaciones().getFirst().puntos().stream()
                .filter(p -> p.valor().compareTo(new BigDecimal("300")) == 0).findFirst().orElseThrow();
        assertThat(retirado.estadoActivo()).isFalse();
        assertThat(deLaOrden(susOrdenes().getFirst()).getFirst().getLecturas())
                .extracting(VerificationReading::idPuntoVerificacion).contains(retirado.id());
    }

    @Test
    @DisplayName("una lectura en un punto que la verificacion no tiene rechaza la carga, en su campo")
    void enUnPuntoQueNoEsta() {
        DataLoadFile en999 = historial("\"punto\": 50,", "\"punto\": 999,");

        assertThatThrownBy(() -> carga.apply(List.of(CATALOGO, CLIENTE, en999)))
                .isInstanceOfSatisfying(DataLoadRejectedException.class, e -> {
                    assertThat(e.problema().ruta()).isEqualTo("/ordenes/0/reportes/0/verificaciones/0/lecturas/0/punto");
                    assertThat(e.problema().mensaje()).contains("999").contains("puntosAnteriores");
                });
    }
}
