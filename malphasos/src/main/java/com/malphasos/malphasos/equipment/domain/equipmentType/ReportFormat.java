package com.malphasos.malphasos.equipment.domain.equipmentType;

/**
 * El formato con que se imprime el reporte de servicio de un tipo de equipo (V32, 2026-10-16): los tres
 * que la empresa usa hoy, que se imprimen como {@code BB-ING-MP0}, {@code MP1} y {@code MP2}.
 */
public enum ReportFormat {

    /** Sin verificación metrológica: una sola página. Solo para un tipo que no se verifica. */
    MP0,

    /** Con verificación. Solo para un tipo que se verifica. */
    MP1,

    /** Con verificación. Solo para un tipo que se verifica; el que se pone por defecto. */
    MP2;

    /** Si este formato sirve para un tipo que se verifica, o para uno que no. */
    public boolean sirvePara(boolean verificable) {
        return verificable != (this == MP0);
    }

    /** El que se pone sin que nadie elija: el mismo que V32 dio a los tipos que ya existían. */
    static ReportFormat porDefecto(boolean verificable) {
        return verificable ? MP2 : MP0;
    }
}
