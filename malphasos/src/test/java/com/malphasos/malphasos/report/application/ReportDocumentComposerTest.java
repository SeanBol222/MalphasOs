package com.malphasos.malphasos.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheet;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetServicePort;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.application.ports.input.PersonSignatureServicePort;
import com.malphasos.malphasos.person.domain.person.PersonSignature;
import com.malphasos.malphasos.report.application.services.serviceReport.ReportDocumentComposer;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Lo que el informe imprime de los demás, compuesto de donde vive (2026-10-17). */
@ExtendWith(MockitoExtension.class)
class ReportDocumentComposerTest {

    private static final UUID ORDEN = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID INGENIERO = UUID.randomUUID();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 5};

    @Mock private WorkOrderServicePort workOrderServicePort;
    @Mock private LifeSheetServicePort lifeSheetServicePort;
    @Mock private ServiceCompanyServicePort serviceCompanyServicePort;
    @Mock private PersonCommunicationPort personCommunicationPort;
    @Mock private PersonSignatureServicePort personSignatureServicePort;

    @InjectMocks private ReportDocumentComposer composer;

    private final ServiceReport reporte = ServiceReport.open(ORDEN, EQUIPO);

    private static WorkOrder orden(UUID ingeniero) {
        return WorkOrder.rehydrate(ORDEN, UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 10, 1),
                Periodicity.TRIMESTRAL, ServiceType.CORRECTIVO, true, ExecutionState.EN_EJECUCION, ingeniero,
                true);
    }

    private static LifeSheet hoja() {
        return new LifeSheet(
                new LifeSheet.Identificacion(EQUIPO, "HV-CDN-0007", "SN-77", "INV-1", "BAS-3", null, null, null,
                        "Clínica del Norte", "900.111.222-3", "Sede Norte", "Calle 1", "Ciudad", "Urgencias", true,
                        List.of("Ana Encargada"), List.of("601 1"), List.of("c@n.example")),
                new LifeSheet.Tecnica("Báscula", null, "Electrónica", null, "Pesar pacientes", null, "Kern",
                        "MS-200", null,
                        new TechnicalSheet(RiskClass.IIA, null, "AC", 110, 20, new BigDecimal("0.5"), 60),
                        List.of()),
                new LifeSheet.Fabricante("Kern", null),
                List.of());
    }

    private static EquipmentType tipo() {
        Magnitude masa = new Magnitude(UUID.randomUUID(), "masa", "Masa", true);
        MeasurementUnit kg = new MeasurementUnit(UUID.randomUUID(), masa.id(), "kg", "kilogramo", true);
        VerificationPoint punto = new VerificationPoint(UUID.randomUUID(), new BigDecimal("10.5"), true);
        TypeVerification verificacion = TypeVerification.of(
                masa, kg, VerificationMode.PATRON_CONSTANTE, 3, List.of(punto));

        return EquipmentType.rehydrate(UUID.randomUUID(), "Báscula", null, null, null, null, null,
                List.of(verificacion), 0L, true);
    }

    private static PersonCommunicationResponse ingeniero() {
        return new PersonCommunicationResponse(INGENIERO, "1", "Ana", null, "Pérez", "Gómez", null, true,
                List.of(), List.of());
    }

    @BeforeEach
    void laHojaDeVida() {
        when(lifeSheetServicePort.findByEquipment(EQUIPO, ReadScope.unrestricted())).thenReturn(hoja());
    }

    @Test
    @DisplayName("copia la orden, el cliente, el equipo con su ficha, la verificación del tipo, la empresa y el ingeniero")
    void todo() {
        when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted())).thenReturn(orden(INGENIERO));
        when(personCommunicationPort.findById(INGENIERO)).thenReturn(ingeniero());
        when(personSignatureServicePort.find(INGENIERO)).thenReturn(Optional.empty());
        CompanyLogo logo = CompanyLogo.of(PNG);
        ServiceCompany empresa = ServiceCompany.rehydrate(UUID.randomUUID(),
                new CompanyDetails("Ejemplo S.A.S.", "900", "Carrera 1", UUID.randomUUID(), List.of(), "300", null,
                        "La garantía", "La aclaración", "FT-1", "FT-2"),
                logo);
        when(serviceCompanyServicePort.find()).thenReturn(Optional.of(empresa));
        // La empresa guarda su ciudad por identificador desde V44, y el documento lleva el nombre.
        when(serviceCompanyServicePort.cityName(empresa)).thenReturn("Ciudad");
        EquipmentType tipo = tipo();

        ReportDocumentComposer.Composed compuesto = composer.compose(reporte, tipo);
        ReportDocument documento = compuesto.documento();

        assertThat(documento.version()).isEqualTo(ReportDocument.VERSION);
        assertThat(documento.orden()).isEqualTo(new ReportDocument.Orden(
                orden(INGENIERO).numero(), "CORRECTIVO", true, "TRIMESTRAL", LocalDate.of(2026, 10, 1)));
        assertThat(documento.cliente()).isEqualTo(new ReportDocument.Cliente("Clínica del Norte", "900.111.222-3",
                "Sede Norte", "Calle 1", "Ciudad", "Urgencias", List.of("Ana Encargada"), List.of("601 1"),
                List.of("c@n.example")));
        assertThat(documento.equipo()).isEqualTo(new ReportDocument.Equipo("Báscula", "Kern", "MS-200", "SN-77",
                "HV-CDN-0007", "INV-1", "BAS-3", "Electrónica", "Pesar pacientes", "IIA", "AC", 110, 20,
                new BigDecimal("0.5"), 60));
        TypeVerification declarada = tipo.getVerificaciones().getFirst();
        assertThat(documento.verificaciones()).containsExactly(new ReportDocument.Verificacion(
                declarada.id(), "Masa", "kg", 3,
                List.of(new ReportDocument.Punto(declarada.puntos().getFirst().id(), new BigDecimal("10.5")))));
        assertThat(documento.empresa()).isEqualTo(new ReportDocument.Empresa("Ejemplo S.A.S.", "900", "Carrera 1",
                "Ciudad", "300", "La garantía", "La aclaración", "FT-2", new DocumentImage(PNG, "image/png").huella()));
        assertThat(documento.ingeniero()).isEqualTo("Ana Pérez Gómez");
        assertThat(compuesto.logo()).isEqualTo(new DocumentImage(PNG, "image/png"));
        // Sin firma guardada, la línea del profesional se firma a mano.
        assertThat(documento.huellaFirmaIngeniero()).isNull();
        assertThat(compuesto.firmaIngeniero()).isNull();
    }

    @Test
    @DisplayName("la firma guardada del ingeniero de la orden va por su huella, y aparte la imagen")
    void conFirma() {
        when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted())).thenReturn(orden(INGENIERO));
        when(personCommunicationPort.findById(INGENIERO)).thenReturn(ingeniero());
        when(serviceCompanyServicePort.find()).thenReturn(Optional.empty());
        byte[] trazo = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 42};
        when(personSignatureServicePort.find(INGENIERO))
                .thenReturn(Optional.of(new PersonSignature(trazo, LocalDateTime.of(2026, 10, 17, 9, 0))));

        ReportDocumentComposer.Composed compuesto = composer.compose(reporte, tipo());

        DocumentImage firma = new DocumentImage(trazo, "image/png");
        assertThat(compuesto.firmaIngeniero()).isEqualTo(firma);
        assertThat(compuesto.documento().huellaFirmaIngeniero()).isEqualTo(firma.huella());
    }

    @Test
    @DisplayName("sin empresa configurada ni ingeniero en la orden, los dos quedan vacíos y no se consulta a nadie")
    void sinEmpresaNiIngeniero() {
        when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted())).thenReturn(orden(null));
        when(serviceCompanyServicePort.find()).thenReturn(Optional.empty());

        ReportDocumentComposer.Composed compuesto = composer.compose(reporte, tipo());

        assertThat(compuesto.documento().empresa()).isNull();
        assertThat(compuesto.documento().ingeniero()).isNull();
        assertThat(compuesto.logo()).isNull();
        verify(personCommunicationPort, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(personSignatureServicePort, never()).find(org.mockito.ArgumentMatchers.any());
    }
}
