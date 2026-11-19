package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.VerificationStandardsPersistencePort;
import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda los patrones de cada verificación con SQL, como los protocolos de un tipo: es una tabla de dos
 * llaves sin nada más, y una entidad JPA no añadiría nada.
 *
 * <p>Sin borrado lógico (V60): un par que sale se borra. Lo que un reporte usó queda en su documento, y
 * un par retirado no tendría a nadie que lo leyera.
 */
@Component
@RequiredArgsConstructor
public class VerificationStandardsPersistenceAdapter implements VerificationStandardsPersistencePort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public VerificationStandards findByVerification(UUID idVerificacion) {
        return findByVerifications(List.of(idVerificacion)).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VerificationStandards> findByVerifications(Collection<UUID> idsVerificacion) {
        if (idsVerificacion.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<UUID>> porVerificacion = new LinkedHashMap<>();
        idsVerificacion.forEach(id -> porVerificacion.put(id, new ArrayList<>()));
        jdbc.query(
                """
                SELECT k_id_verificacion, k_id_equipo_patron
                FROM patron_verificacion
                WHERE k_id_verificacion IN (:ids)
                """,
                Map.of("ids", idsVerificacion),
                fila -> {
                    porVerificacion.get(fila.getObject("k_id_verificacion", UUID.class))
                            .add(fila.getObject("k_id_equipo_patron", UUID.class));
                });

        return porVerificacion.entrySet().stream()
                .map(entrada -> VerificationStandards.rehydrate(entrada.getKey(), entrada.getValue()))
                .toList();
    }

    @Override
    @Transactional
    public VerificationStandards save(VerificationStandards patrones) {
        UUID verificacion = patrones.getIdVerificacion();
        List<UUID> conservar = List.copyOf(patrones.getPatrones());

        if (conservar.isEmpty()) {
            jdbc.update("DELETE FROM patron_verificacion WHERE k_id_verificacion = :verificacion",
                    Map.of("verificacion", verificacion));
        } else {
            jdbc.update(
                    """
                    DELETE FROM patron_verificacion
                    WHERE k_id_verificacion = :verificacion AND k_id_equipo_patron NOT IN (:conservar)
                    """,
                    Map.of("verificacion", verificacion, "conservar", conservar));
        }

        for (UUID patron : conservar) {
            jdbc.update(
                    """
                    INSERT INTO patron_verificacion (k_id_verificacion, k_id_equipo_patron)
                    VALUES (:verificacion, :patron)
                    ON CONFLICT DO NOTHING
                    """,
                    Map.of("verificacion", verificacion, "patron", patron));
        }

        return findByVerification(verificacion);
    }
}
