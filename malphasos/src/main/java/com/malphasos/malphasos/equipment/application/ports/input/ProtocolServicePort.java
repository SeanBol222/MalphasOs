package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.model.AssignedProtocolDetail;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.AssignProtocolsCommand;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.CreateProtocolCommand;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.DeactivateProtocolCommand;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.UpdateProtocolCommand;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casos de uso sobre los protocolos de mantenimiento preventivo (RF-14): el catálogo, y qué protocolos
 * lleva cada tipo de equipo y en qué orden.
 */
public interface ProtocolServicePort {

    List<Protocol> findAll();

    Protocol findById(UUID id);

    Protocol create(CreateProtocolCommand command);

    Protocol update(UpdateProtocolCommand command);

    void deactivate(DeactivateProtocolCommand command);

    /** Los protocolos de un tipo, en orden de prioridad, con su nombre y su descripción. */
    List<AssignedProtocolDetail> protocolsOf(UUID idTipoEquipo);

    /** Fija los protocolos de un tipo en el orden en que llegan. Devuelve cómo quedaron. */
    List<AssignedProtocolDetail> assign(AssignProtocolsCommand command);

    /**
     * Los protocolos de cada tipo que tiene alguno, en orden de prioridad (2026-10-11): lo que el
     * listado de tipos del catálogo resume. Un tipo sin protocolos no aparece.
     */
    Map<UUID, List<AssignedProtocolDetail>> protocolsByType();
}
