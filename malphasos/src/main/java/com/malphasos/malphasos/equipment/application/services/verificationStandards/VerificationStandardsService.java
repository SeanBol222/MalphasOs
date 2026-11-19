package com.malphasos.malphasos.equipment.application.services.verificationStandards;

import com.malphasos.malphasos.equipment.application.ports.input.VerificationStandardsServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ReferenceStandardPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.VerificationStandardsPersistencePort;
import com.malphasos.malphasos.equipment.application.services.verificationStandards.commands.AssignVerificationStandardsCommand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentTypeNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.VerificationNotFoundException;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los patrones de cada verificación. Las reglas que exigen mirar fuera del agregado viven aquí: que la
 * verificación sea de ese tipo y esté vigente, que el tipo lo esté, y que cada patrón exista y —si es
 * nuevo en la lista— esté vigente.
 *
 * <p>Un patrón retirado que ya estaba puede seguir: retirarlo no lo quita de las verificaciones, y exigir
 * quitarlo antes de guardar cualquier otro cambio haría que la pantalla no pudiera guardar nada. Elegirlo
 * en un reporte ya está prohibido por estar retirado.
 */
@Service
@RequiredArgsConstructor
public class VerificationStandardsService implements VerificationStandardsServicePort {

    private final VerificationStandardsPersistencePort verificationStandardsPersistencePort;
    private final EquipmentTypePersistencePort equipmentTypePersistencePort;
    private final ReferenceStandardPersistencePort referenceStandardPersistencePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<VerificationStandards> standardsOf(UUID idTipoEquipo) {
        EquipmentType tipo = exigirTipo(idTipoEquipo);

        return verificationStandardsPersistencePort.findByVerifications(
                tipo.verificacionesActivas().stream().map(TypeVerification::id).toList());
    }

    @Override
    @Transactional
    public VerificationStandards assign(AssignVerificationStandardsCommand command) {
        EquipmentType tipo = exigirTipo(command.idTipoEquipo());

        if (!tipo.isEstadoActivo()) {
            throw new IllegalArgumentException(
                    "El tipo de equipo «" + tipo.getNombre() + "» está retirado: no se le asignan patrones");
        }

        TypeVerification verificacion = tipo.verificacionPorId(command.idVerificacion())
                .orElseThrow(() -> new VerificationNotFoundException(tipo.getId(), command.idVerificacion()));

        if (!verificacion.estadoActivo()) {
            throw new IllegalArgumentException(
                    "La verificación de " + verificacion.magnitud().nombre() + " está retirada: no se le"
                            + " asignan patrones");
        }

        VerificationStandards patrones = verificationStandardsPersistencePort.findByVerification(verificacion.id());
        exigirPatronesVigentes(command.patrones(), patrones);
        patrones.assign(command.patrones());

        VerificationStandards guardados = verificationStandardsPersistencePort.save(patrones);
        eventDispatcherPort.dispatchAll(patrones.pullEvents());

        return guardados;
    }

    private EquipmentType exigirTipo(UUID idTipoEquipo) {
        return equipmentTypePersistencePort.findById(idTipoEquipo)
                .orElseThrow(() -> new EquipmentTypeNotFoundException(idTipoEquipo));
    }

    /** Cada patrón pedido existe —si no, 404 con su identificador— y, si no estaba ya, está vigente. */
    private void exigirPatronesVigentes(List<UUID> pedidos, VerificationStandards actuales) {
        if (pedidos == null) {
            return;
        }

        for (UUID id : pedidos) {
            if (id == null) {
                continue;
            }

            ReferenceStandard patron = referenceStandardPersistencePort.findById(id)
                    .orElseThrow(() -> new ReferenceStandardNotFoundException(id));

            if (!patron.isEstadoActivo() && !actuales.admite(id)) {
                throw new IllegalArgumentException(
                        "El equipo patrón «" + patron.getNombre() + "» está retirado: no se asigna a una"
                                + " verificación");
            }
        }
    }
}
