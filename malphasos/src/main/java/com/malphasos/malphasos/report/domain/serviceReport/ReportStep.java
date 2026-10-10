package com.malphasos.malphasos.report.domain.serviceReport;

import java.util.UUID;

/**
 * Un protocolo del preventivo copiado en el reporte, y si se hizo.
 *
 * <p><b>Es una copia, no una referencia</b>: nombre, descripción e indicación son los que el protocolo
 * tenía el día que se abrió el reporte. Editar el protocolo después no cambia lo que dice un reporte
 * viejo, decidido por el usuario el 2026-10-10. {@code idProtocolo} se guarda solo para saber de cuál
 * salió.
 *
 * <p>No es un agregado: no tiene vida fuera de su reporte, y se marca a través de él.
 *
 * @param orden la prioridad que el protocolo tenía en el tipo del equipo, 1 el primero
 */
public record ReportStep(
        UUID id,
        int orden,
        UUID idProtocolo,
        String nombre,
        String descripcion,
        String indicacion,
        StepState estado,
        String nota) {

    static final int LARGO_NOTA = 300;

    /** Un paso recién copiado del protocolo: pendiente y sin nota. */
    public static ReportStep copied(int orden, UUID idProtocolo, String nombre, String descripcion, String indicacion) {
        if (orden < 1 || idProtocolo == null || nombre == null || nombre.isBlank()
                || descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException(
                    "Un paso del protocolo necesita su orden, su protocolo, su nombre y su descripción");
        }

        return new ReportStep(UUID.randomUUID(), orden, idProtocolo, nombre, descripcion,
                indicacion == null || indicacion.isBlank() ? null : indicacion, StepState.PENDIENTE, null);
    }

    /**
     * El mismo paso con otro estado y otra nota. {@code NO_APLICA} exige nota: saltarse un paso del
     * protocolo queda justificado, decidido por el usuario el 2026-10-10.
     */
    ReportStep marked(StepState nuevoEstado, String nuevaNota) {
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("Un paso se marca con un estado");
        }

        String nota = nuevaNota == null || nuevaNota.isBlank() ? null : nuevaNota.trim();

        if (nota != null && nota.length() > LARGO_NOTA) {
            throw new IllegalArgumentException("La nota de un paso tiene como mucho " + LARGO_NOTA + " caracteres");
        }
        if (nuevoEstado == StepState.NO_APLICA && nota == null) {
            throw new IllegalArgumentException(
                    "Para marcar «" + nombre + "» como que no aplica hay que decir por qué en la nota");
        }

        return new ReportStep(id, orden, idProtocolo, nombre, descripcion, indicacion, nuevoEstado, nota);
    }

    public boolean marcado() {
        return estado != StepState.PENDIENTE;
    }
}
