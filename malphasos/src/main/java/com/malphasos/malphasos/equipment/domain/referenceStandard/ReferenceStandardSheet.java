package com.malphasos.malphasos.equipment.domain.referenceStandard;

import java.time.LocalDate;

/**
 * La ficha de un equipo patrón: lo que los informes impresos dicen de él en su tabla de trazabilidad,
 * además de nombre, marca, serie y certificado (2026-10-16).
 *
 * <p>Todo opcional: los patrones registrados antes no lo tienen, y uno hecho en casa no tiene modelo ni
 * lo calibra una empresa. Los textos llegan recortados y uno en blanco es uno que falta. Rango y
 * resolución son texto porque un patrón cubre a veces dos magnitudes y así se imprimen.
 */
public record ReferenceStandardSheet(
        String modelo, String codigoInterno, String rango, String resolucion, LocalDate fechaCalibracion,
        String empresaCalibradora) {

    static final int LARGO_MODELO = 80;
    static final int LARGO_CODIGO_INTERNO = 40;
    static final int LARGO_RANGO = 120;
    static final int LARGO_RESOLUCION = 80;
    static final int LARGO_EMPRESA = 120;

    /** La de un patrón del que no se sabe nada más. */
    public static final ReferenceStandardSheet VACIA = new ReferenceStandardSheet(null, null, null, null, null, null);

    public ReferenceStandardSheet {
        modelo = ReferenceStandard.opcional(modelo, "modelo", LARGO_MODELO);
        codigoInterno = ReferenceStandard.opcional(codigoInterno, "código interno", LARGO_CODIGO_INTERNO);
        rango = ReferenceStandard.opcional(rango, "rango", LARGO_RANGO);
        resolucion = ReferenceStandard.opcional(resolucion, "resolución", LARGO_RESOLUCION);
        empresaCalibradora = ReferenceStandard.opcional(empresaCalibradora, "empresa calibradora", LARGO_EMPRESA);
    }
}
