package com.malphasos.malphasos.report.domain.serviceReport;

/**
 * Quién recibió el equipo al entregarlo (V31, 2026-10-16). En texto y no como persona del sistema,
 * decidido por el usuario: quien recibe muchas veces no está registrado —la jefa de enfermería de
 * turno—.
 *
 * @param cedula opcional
 * @param cargo opcional
 */
public record Handover(String nombre, String cedula, String cargo) {

    static final int LARGO_NOMBRE = 120;
    static final int LARGO_CEDULA = 20;
    static final int LARGO_CARGO = 80;

    public Handover {
        nombre = recortar(nombre);
        cedula = recortar(cedula);
        cargo = recortar(cargo);

        if (nombre == null) {
            throw new IllegalArgumentException("Hay que decir quien recibio el equipo");
        }
        exigirLargo(nombre, "nombre", LARGO_NOMBRE);
        exigirLargo(cedula, "cedula", LARGO_CEDULA);
        exigirLargo(cargo, "cargo", LARGO_CARGO);
    }

    private static String recortar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static void exigirLargo(String valor, String campo, int largo) {
        if (valor != null && valor.length() > largo) {
            throw new IllegalArgumentException(
                    "El " + campo + " de quien recibe tiene como mucho " + largo + " caracteres");
        }
    }
}
