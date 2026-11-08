package com.malphasos.malphasos.dataload.infrastructure.output.writer;

import com.malphasos.malphasos.dataload.application.model.LoadDocument;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que es cada ref de la carga en MalphasOS, a medida que se escribe: el identificador del registro
 * nuevo, o el del que ya estaba. Vive lo que dura una carga: las refs no se recuerdan de una a otra.
 */
final class LoadRefs {

    /** Una verificación de la carga: la del tipo, con sus puntos, nueva o la que el tipo ya tenía. */
    record VerificationRef(UUID idTipo, TypeVerification verificacion) {}

    /** Un patrón, con los certificados de la carga: cada reporte usa el vigente el día del servicio. */
    record StandardRef(UUID id, List<LoadDocument.Certificate> certificados) {}

    private final Map<String, UUID> ids = new HashMap<>();
    private final Map<String, VerificationRef> verificaciones = new HashMap<>();
    private final Map<String, StandardRef> patrones = new HashMap<>();

    void put(String ref, UUID id) {
        ids.put(ref, id);
    }

    /** El identificador de una ref. La validación garantiza que todas tienen destino. */
    UUID id(String ref) {
        UUID id = ids.get(ref);
        if (id == null) {
            throw new IllegalStateException("La ref «" + ref + "» no se escribió antes de usarse");
        }
        return id;
    }

    void putVerification(String ref, VerificationRef verificacion) {
        verificaciones.put(ref, verificacion);
    }

    VerificationRef verification(String ref) {
        return verificaciones.get(ref);
    }

    void putStandard(String ref, StandardRef patron) {
        ids.put(ref, patron.id());
        patrones.put(ref, patron);
    }

    StandardRef standard(String ref) {
        return patrones.get(ref);
    }
}
