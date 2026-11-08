package com.malphasos.malphasos.equipment.application.services.lifeSheet;

import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheet;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPrint;
import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Una hoja de vida de ejemplo para las pruebas del PDF (2026-10-18), con datos inventados: MalphasOS no
 * lleva los de ningún cliente real, tampoco en sus pruebas.
 */
public class UnaHojaDeVida {

    public static final UUID ID = UUID.fromString("5046e506-439a-4d38-a31c-bc70f408a496");
    public static final LocalDate HOY = LocalDate.of(2026, 10, 18);
    /** La empresa guarda su ciudad por identificador desde V44; esto es lo que el servicio resuelve. */
    public static final String CIUDAD_DE_LA_EMPRESA = "Ciudad de Ejemplo";

    private boolean activo = true;
    private int pasos = 2;
    private BigDecimal voltaje = new BigDecimal("110");
    private final List<Intervention> historial = new ArrayList<>();
    private Optional<ServiceCompany> empresa = Optional.of(empresa(null));
    private String foto;

    public UnaHojaDeVida deBaja() {
        activo = false;
        return this;
    }

    /** El voltaje del modelo, que desde V57 admite decimales. */
    public UnaHojaDeVida conVoltaje(String valor) {
        voltaje = new BigDecimal(valor);
        return this;
    }

    public UnaHojaDeVida conPasos(int cuantos) {
        pasos = cuantos;
        return this;
    }

    /** Intervenciones de la más reciente a la más antigua, como las manda el servidor. */
    public UnaHojaDeVida con(InterventionResult resultado, LocalDate fecha) {
        historial.add(new Intervention(ID, UUID.randomUUID(), fecha.atTime(9, 0),
                InterventionType.PREVENTIVO, resultado, "Limpieza y verificación eléctrica", "Ada Lovelace"));
        return this;
    }

    public UnaHojaDeVida conServicios(int cuantos) {
        IntStream.range(0, cuantos).forEach(i -> con(InterventionResult.OPERATIVO, HOY.minusDays(30L * i)));
        return this;
    }

    /** La foto del equipo, como la trae la hoja de vida desde V58: un {@code data:} URI. */
    public UnaHojaDeVida conFoto(String dataUri) {
        foto = dataUri;
        return this;
    }

    public UnaHojaDeVida sinEmpresa() {
        empresa = Optional.empty();
        return this;
    }

    public UnaHojaDeVida conLogo(CompanyLogo logo) {
        empresa = Optional.of(empresa(logo));
        return this;
    }

    public LifeSheet hoja() {
        return new LifeSheet(
                new LifeSheet.Identificacion(ID, "HV-CDN-0001", "SN-1", null, "INT-7", "Proveedor Ejemplo",
                        LocalDate.of(2024, 3, 5), 1_250_000L, "Clínica de Ejemplo", "900.111.222-3", "Sede Norte",
                        "Calle 1 # 2-3", "Ciudad de Ejemplo", "Urgencias", activo, List.of("Grace Hopper"),
                        List.of("601 000 0000"), List.of("contacto@clinica.example"), foto),
                new LifeSheet.Tecnica("Báscula", "Mide la masa de un paciente", "Electrónica",
                        "No exponer a humedad\n\n  Limpiar la pantalla con un paño seco  ", "Clínico",
                        "Paño con alcohol al 70 %", "Kern", "MS-200", "INVIMA 2020DM-0001",
                        new TechnicalSheet(RiskClass.IIA, null, "Red eléctrica", voltaje, 20, new BigDecimal("0.10"), 60),
                        IntStream.rangeClosed(1, pasos)
                                .mapToObj(i -> new LifeSheet.PasoDelProtocolo(i, "Paso " + i, "Qué se hace en el paso " + i,
                                        i == 1 ? "Alcohol isopropílico" : null))
                                .toList()),
                new LifeSheet.Fabricante("Kern & Sohn", "Alemania"),
                List.copyOf(historial));
    }

    public Optional<ServiceCompany> empresa() {
        return empresa;
    }

    public LifeSheetPrint impresa() {
        return LifeSheetPrintComposer.compose(
                hoja(), empresa, empresa.isPresent() ? CIUDAD_DE_LA_EMPRESA : null, HOY);
    }

    private static ServiceCompany empresa(CompanyLogo logo) {
        return ServiceCompany.rehydrate(UUID.randomUUID(),
                new CompanyDetails("Servicios Biomédicos de Ejemplo S.A.S.", "900.000.000-0", "Carrera 1 N.º 2-3",
                        UUID.randomUUID(), List.of("601 111 1111"), "300 000 0000", "contacto@empresa.example",
                        null, null, "FT-HV-01", null),
                logo);
    }
}
