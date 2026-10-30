package com.malphasos.malphasos.report.application.sheet;

import com.malphasos.malphasos.report.application.model.ReportDocumentView;
import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportAccessory;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.UsedReferenceStandard;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationStandard;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un informe de ejemplo para las pruebas de lo que se imprime (2026-10-17): los mismos datos inventados que
 * usaba la prueba del documento en Angular. Se cambia lo que cada prueba necesita y se pide la vista.
 */
public final class UnInforme {

    public static final UUID ID = UUID.fromString("1dc6f5c8-0000-4000-8000-000000000001");
    public static final UUID VERIFICACION = UUID.fromString("0e0e0e0e-0000-4000-8000-000000000001");
    public static final UUID PUNTO = UUID.fromString("0e0e0e0e-0000-4000-8000-000000000002");
    public static final String GARANTIA = "La garantía cubre lo que se reporte en los cinco días siguientes.";

    public static final UsedReferenceStandard PATRON = new UsedReferenceStandard(
            UUID.fromString("0e0e0e0e-0000-4000-8000-000000000003"), "Manómetro patrón", "Fluke", "M-1", "CAL-1",
            LocalDate.of(2099, 4, 30), "700G", "BB-PAT-003", "0 a 300 mmHg", "0,1 mmHg", LocalDate.of(2026, 4, 30),
            "Metrología Andina");

    public ReportState estado = ReportState.BORRADOR;
    public LocalDateTime finalizado;
    public ServiceResult resultado;
    public String tipoServicio = "PREVENTIVO";
    public boolean garantiaDeLaOrden;
    public String periodicidad = "TRIMESTRAL";
    public String fallaReportada;
    public String diagnostico;
    public String procedimientos = "Limpieza general";
    public String observaciones;
    public String causa;
    public String repuestos;
    public IntakeCheck ingresoFisico = new IntakeCheck(IntakeState.CONFORME, null);
    public IntakeCheck ingresoOperativo = new IntakeCheck(IntakeState.NO_CONFORME, "No enciende la pantalla");
    public List<ReportAccessory> accesorios = new ArrayList<>();
    public Handover recibe;
    public List<VerificationReading> lecturas = new ArrayList<>();
    public List<VerificationStandard> patrones = new ArrayList<>();
    public List<ReportStep> pasos = new ArrayList<>();
    public List<ReportDocument.Verificacion> verificaciones = new ArrayList<>(List.of(new ReportDocument.Verificacion(
            VERIFICACION, "Presión", "mmHg", 3, new BigDecimal("0.5"),
            List.of(new ReportDocument.Punto(PUNTO, new BigDecimal("100"))))));
    /** El texto de cada paso, como lo lleva el documento desde V51. Lo pone {@link #conPaso}. */
    public List<ReportDocument.Paso> textosDePasos = new ArrayList<>();
    /** La ficha de cada patrón que se pueda elegir, por su identificador. */
    public java.util.Map<UUID, UsedReferenceStandard> fichas = new java.util.LinkedHashMap<>(java.util.Map.of(PATRON.id(), PATRON));
    public ReportDocument.Empresa empresa = new ReportDocument.Empresa("Servicios de Ejemplo S.A.S.", "900.000.000-0",
            "Carrera 1 N.º 2-3", "Ciudad de Ejemplo", "300 000 0000", GARANTIA,
            "Mantenimiento realizado bajo el protocolo de la empresa.", "FT-002 · Versión 1", null);
    public List<String> responsables = List.of("Carla Ruiz");
    public String ingeniero = "Ana Ruiz";
    public DocumentImage logo;
    public DocumentImage firmaIngeniero;
    public DocumentImage firmaRecibe;

    /** Las dos lecturas del punto de 100 mmHg con ±0,5: una dentro y otra fuera, con su patrón. */
    public UnInforme conLecturas() {
        lecturas.add(VerificationReading.rehydrate(UUID.randomUUID(), VERIFICACION, PUNTO, 2,
                new BigDecimal("100"), new BigDecimal("100.9"), true));
        lecturas.add(VerificationReading.rehydrate(UUID.randomUUID(), VERIFICACION, PUNTO, 1,
                new BigDecimal("100"), new BigDecimal("100.2"), true));
        patrones.add(new VerificationStandard(UUID.randomUUID(), VERIFICACION, PATRON.id(), true));
        return this;
    }

    /** Un paso del protocolo: su fila en el reporte y su texto en el documento, en el orden en que se añaden. */
    public UnInforme conPaso(String nombre, com.malphasos.malphasos.report.domain.serviceReport.StepState estado,
            String nota) {
        UUID protocolo = UUID.randomUUID();
        pasos.add(new ReportStep(UUID.randomUUID(), protocolo, estado, nota));
        textosDePasos.add(new ReportDocument.Paso(protocolo, textosDePasos.size() + 1, nombre, "Descripción", null));
        return this;
    }

    public UnInforme cerrado(ServiceResult resultado) {
        this.estado = ReportState.FINALIZADO;
        this.finalizado = LocalDateTime.of(2026, 10, 20, 16, 30);
        this.resultado = resultado;
        return this;
    }

    public ReportDocumentView vista() {
        ServiceReport reporte = ServiceReport.rehydrate(ID, UUID.randomUUID(), UUID.randomUUID(), estado,
                fallaReportada, diagnostico, procedimientos, observaciones, causa, repuestos, patrones,
                ingresoFisico, ingresoOperativo, accesorios, recibe, resultado, finalizado, lecturas, pasos, true,
                null, false, null);
        ReportDocument documento = new ReportDocument(ReportDocument.VERSION,
                new ReportDocument.Orden("4F2A9C", tipoServicio, garantiaDeLaOrden, periodicidad,
                        LocalDate.of(2026, 10, 15)),
                new ReportDocument.Cliente("Hospital Central", "900123456", "Sede Norte", "Calle 10 # 20 - 30",
                        "Bogotá", "Urgencias", responsables, List.of("3001112233"), List.of("compras@hospital.co")),
                new ReportDocument.Equipo("Tensiómetro", "Welch Allyn", "ProBP 2400", "SN-0001", "HV-HCE-0007",
                        "INV-77", "MON-03", "Electrónica", "Medir la presión arterial", "IIA", "AC", 110, null, null,
                        60),
                verificaciones, empresa, ingeniero, null, textosDePasos,
                patrones.stream()
                        .filter(VerificationStandard::estadoActivo)
                        .map(usado -> new ReportDocument.Patron(usado.idVerificacion(), fichas.get(usado.idEquipoPatron())))
                        .toList());

        return new ReportDocumentView(reporte, documento, logo, firmaIngeniero, firmaRecibe,
                estado == ReportState.FINALIZADO);
    }
}
