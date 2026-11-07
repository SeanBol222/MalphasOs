package com.malphasos.malphasos.dataload.application.model;

/**
 * Qué nombra una {@code ref}. Todas comparten un solo espacio en la carga —una ref es única entre los
 * tres archivos—, y la clase dice si el que la usa nombra lo que espera: un modelo y no una marca.
 */
public enum RefKind {
    MARCA("una marca"),
    FABRICANTE("un fabricante"),
    PROTOCOLO("un protocolo"),
    TIPO_EQUIPO("un tipo de equipo"),
    VERIFICACION("una verificación de un tipo de equipo"),
    MODELO("un modelo"),
    PATRON("un equipo patrón"),
    PERSONA("una persona"),
    CLIENTE("un cliente"),
    SEDE("una sede"),
    AREA("un área de servicio"),
    EQUIPO("un equipo"),
    ORDEN("una orden"),
    REPORTE("un reporte");

    private final String descripcion;

    RefKind(String descripcion) {
        this.descripcion = descripcion;
    }

    public String descripcion() {
        return descripcion;
    }
}
