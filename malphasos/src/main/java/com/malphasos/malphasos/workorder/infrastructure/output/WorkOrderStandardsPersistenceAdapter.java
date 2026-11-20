package com.malphasos.malphasos.workorder.infrastructure.output;

import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderStandardsPersistencePort;
import com.malphasos.malphasos.workorder.domain.workOrderStandards.WorkOrderStandards;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda los patrones de cada orden por tipo con SQL, como los de cada verificación: es una tabla de tres
 * llaves sin nada más, y una entidad JPA no añadiría nada.
 *
 * <p>Sin borrado lógico (V60): un patrón que sale se borra. Lo que se usó queda en el documento de cada
 * reporte cerrado y en el de la orden ejecutada.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderStandardsPersistenceAdapter implements WorkOrderStandardsPersistencePort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public WorkOrderStandards find(UUID idOrdenTrabajo, UUID idTipoEquipo) {
        List<UUID> patrones = jdbc.queryForList(
                """
                SELECT k_id_equipo_patron
                FROM patron_orden_trabajo
                WHERE k_id_orden_trabajo = :orden AND k_id_tipo_equipo = :tipo
                """,
                Map.of("orden", idOrdenTrabajo, "tipo", idTipoEquipo),
                UUID.class);

        return WorkOrderStandards.rehydrate(idOrdenTrabajo, idTipoEquipo, patrones);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkOrderStandards> findByWorkOrder(UUID idOrdenTrabajo) {
        Map<UUID, List<UUID>> porTipo = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT k_id_tipo_equipo, k_id_equipo_patron
                FROM patron_orden_trabajo
                WHERE k_id_orden_trabajo = :orden
                ORDER BY k_id_tipo_equipo::text
                """,
                Map.of("orden", idOrdenTrabajo),
                fila -> {
                    porTipo.computeIfAbsent(fila.getObject("k_id_tipo_equipo", UUID.class), t -> new ArrayList<>())
                            .add(fila.getObject("k_id_equipo_patron", UUID.class));
                });

        return porTipo.entrySet().stream()
                .map(entrada -> WorkOrderStandards.rehydrate(idOrdenTrabajo, entrada.getKey(), entrada.getValue()))
                .toList();
    }

    @Override
    @Transactional
    public WorkOrderStandards save(WorkOrderStandards patrones) {
        UUID orden = patrones.getIdOrdenTrabajo();
        UUID tipo = patrones.getIdTipoEquipo();
        List<UUID> conservar = List.copyOf(patrones.getPatrones());

        if (conservar.isEmpty()) {
            jdbc.update("DELETE FROM patron_orden_trabajo WHERE k_id_orden_trabajo = :orden AND k_id_tipo_equipo = :tipo",
                    Map.of("orden", orden, "tipo", tipo));
        } else {
            jdbc.update(
                    """
                    DELETE FROM patron_orden_trabajo
                    WHERE k_id_orden_trabajo = :orden AND k_id_tipo_equipo = :tipo
                      AND k_id_equipo_patron NOT IN (:conservar)
                    """,
                    Map.of("orden", orden, "tipo", tipo, "conservar", conservar));
        }

        for (UUID patron : conservar) {
            jdbc.update(
                    """
                    INSERT INTO patron_orden_trabajo (k_id_orden_trabajo, k_id_tipo_equipo, k_id_equipo_patron)
                    VALUES (:orden, :tipo, :patron)
                    ON CONFLICT DO NOTHING
                    """,
                    Map.of("orden", orden, "tipo", tipo, "patron", patron));
        }

        return find(orden, tipo);
    }
}
