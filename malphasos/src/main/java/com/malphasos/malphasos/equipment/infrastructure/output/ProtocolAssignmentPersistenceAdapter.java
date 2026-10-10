package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.ProtocolAssignmentPersistencePort;
import com.malphasos.malphasos.equipment.domain.protocol.AssignedProtocol;
import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda los protocolos de cada tipo de equipo con SQL y no con JPA, y la razón es el orden.
 *
 * <p>La prioridad es única por tipo entre las filas vigentes, con un índice <b>parcial</b>, que en
 * PostgreSQL no se puede declarar diferido. Reordenar fila a fila choca a mitad de camino —subir la 2 a
 * la 1 mientras la 1 sigue siendo la 1—, y Hibernate además vacía los {@code INSERT} antes que los
 * {@code UPDATE}. Así que se hace en dos pasos dentro de la misma transacción: se retiran todas las
 * filas vigentes del tipo, que libera todas las prioridades, y se vuelven a activar con la suya. Una
 * fila que deja de estar no se borra: queda retirada, como todo en este esquema.
 */
@Component
@RequiredArgsConstructor
public class ProtocolAssignmentPersistenceAdapter implements ProtocolAssignmentPersistencePort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public ProtocolAssignment findByType(UUID idTipoEquipo) {
        return ProtocolAssignment.rehydrate(
                idTipoEquipo,
                jdbcTemplate.query(
                        """
                        SELECT k_id_protocolo, i_prioridad, t_indicacion
                        FROM protocolo_tipo_equipo
                        WHERE k_id_tipo_equipo = ? AND b_estado_activo
                        ORDER BY i_prioridad
                        """,
                        (fila, numero) -> new AssignedProtocol(
                                fila.getObject("k_id_protocolo", UUID.class),
                                fila.getInt("i_prioridad"),
                                fila.getString("t_indicacion")),
                        idTipoEquipo));
    }

    @Override
    @Transactional
    public ProtocolAssignment save(ProtocolAssignment asignacion) {
        jdbcTemplate.update(
                """
                UPDATE protocolo_tipo_equipo
                SET b_estado_activo = false
                WHERE k_id_tipo_equipo = ? AND b_estado_activo
                """,
                asignacion.getIdTipoEquipo());

        for (AssignedProtocol asignado : asignacion.getProtocolos()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO protocolo_tipo_equipo
                        (k_id_tipo_equipo, k_id_protocolo, i_prioridad, t_indicacion, b_estado_activo)
                    VALUES (?, ?, ?, ?, true)
                    ON CONFLICT (k_id_tipo_equipo, k_id_protocolo) DO UPDATE
                        SET i_prioridad     = EXCLUDED.i_prioridad,
                            t_indicacion    = EXCLUDED.t_indicacion,
                            b_estado_activo = true
                    """,
                    asignacion.getIdTipoEquipo(),
                    asignado.idProtocolo(),
                    asignado.prioridad(),
                    asignado.indicacion());
        }

        return findByType(asignacion.getIdTipoEquipo());
    }
}
