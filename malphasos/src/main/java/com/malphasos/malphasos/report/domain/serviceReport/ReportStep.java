package com.malphasos.malphasos.report.domain.serviceReport;

import java.util.UUID;

/**
 * Un paso del protocolo en el reporte, y si se hizo.
 *
 * <p><b>Guarda solo lo que es del reporte</b> desde {@code V51} (2026-10-28): de qué protocolo salió, en qué
 * estado quedó y la nota. Hasta entonces copiaba también el nombre, la descripción, la indicación y la
 * posición que el protocolo tenía al abrir el reporte. Decidido por el usuario con el análisis del esquema:
 * mientras el reporte es borrador todo eso se lee en vivo del protocolo y del tipo, y al cerrarse queda en
 * su documento. Un reporte no repite protocolo, como un tipo tampoco: por eso el protocolo lo identifica.
 *
 * <p>No es un agregado: no tiene vida fuera de su reporte, y se marca a través de él.
 */
public record ReportStep(UUID id, UUID idProtocolo, StepState estado, String nota) {

    static final int LARGO_NOTA = 300;

    /** Un paso de un protocolo, pendiente y sin nota: como nace al abrir el borrador. */
    public static ReportStep pending(UUID idProtocolo) {
        if (idProtocolo == null) {
            throw new IllegalArgumentException("Un paso del protocolo necesita su protocolo");
        }

        return new ReportStep(UUID.randomUUID(), idProtocolo, StepState.PENDIENTE, null);
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
                    "Para marcar un paso como que no aplica hay que decir por qué en la nota");
        }

        return new ReportStep(id, idProtocolo, nuevoEstado, nota);
    }

    public boolean marcado() {
        return estado != StepState.PENDIENTE;
    }
}
