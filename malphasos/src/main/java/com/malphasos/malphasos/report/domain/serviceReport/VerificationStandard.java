package com.malphasos.malphasos.report.domain.serviceReport;

import java.util.UUID;

/**
 * Un patrón usado en una verificación de un reporte (V30, 2026-10-16).
 *
 * <p>Decidido por el usuario: una verificación se mide a veces contra varios patrones —una balanza con
 * tres juegos de pesas— y un termohigrómetro verifica temperatura y humedad contra patrones distintos,
 * así que el patrón deja de ser uno por reporte y pasa a ser una lista por verificación.
 *
 * <p>No es un agregado: vive y muere con su reporte, como las lecturas. <b>Quitarlo lo retira y volver
 * a ponerlo lo reactiva</b>, con el mismo identificador: así nunca compiten dos filas activas por el
 * mismo sitio, que es la trampa de Hibernate con los índices únicos parciales.
 */
public record VerificationStandard(UUID id, UUID idVerificacion, UsedReferenceStandard patron, boolean estadoActivo) {

    public VerificationStandard {
        if (id == null || idVerificacion == null || patron == null) {
            throw new IllegalArgumentException(
                    "Un patron de una verificacion necesita su identificador, su verificacion y el patron");
        }
    }

    static VerificationStandard of(UUID idVerificacion, UsedReferenceStandard patron) {
        return new VerificationStandard(UUID.randomUUID(), idVerificacion, patron, true);
    }

    /** Si es este patrón en esta verificación, esté activo o retirado. */
    boolean ocupa(UUID verificacion, UUID idPatron) {
        return idVerificacion.equals(verificacion) && patron.id().equals(idPatron);
    }

    VerificationStandard withPatron(UsedReferenceStandard copia) {
        return new VerificationStandard(id, idVerificacion, copia, true);
    }

    VerificationStandard deactivated() {
        return new VerificationStandard(id, idVerificacion, patron, false);
    }
}
