package com.malphasos.malphasos.equipment.application.services.referenceStandard;

import com.malphasos.malphasos.equipment.application.ports.input.ReferenceStandardServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ReferenceStandardPersistencePort;
import com.malphasos.malphasos.equipment.application.services.referenceStandard.commands.DeactivateReferenceStandardCommand;
import com.malphasos.malphasos.equipment.application.services.referenceStandard.commands.SaveReferenceStandardCommand;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardSerialTakenException;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El catálogo de equipos patrón. Una regla vive aquí y no en el agregado, porque exige mirar a los
 * demás: que la serie no se repita entre los vigentes. La base la impide también, con un índice único
 * parcial; aquí se comprueba antes para responder «esa serie ya la tiene tal patrón» y no un conflicto
 * de integridad genérico.
 *
 * <p>Retirar un patrón no mira si algún reporte lo usó: los reportes copian lo que tenía, y retirarlo
 * solo impide elegirlo en adelante.
 */
@Service
@RequiredArgsConstructor
public class ReferenceStandardService implements ReferenceStandardServicePort {

    private final ReferenceStandardPersistencePort referenceStandardPersistencePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<ReferenceStandard> findAll() {
        return referenceStandardPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public ReferenceStandard findById(UUID id) {
        return referenceStandardPersistencePort.findById(id)
                .orElseThrow(() -> new ReferenceStandardNotFoundException(id));
    }

    @Override
    @Transactional
    public ReferenceStandard create(SaveReferenceStandardCommand command) {
        ReferenceStandard patron = ReferenceStandard.create(command.nombre(), command.marca(), command.serie(),
                command.certificado(), command.vencimientoCertificado());
        exigirSerieLibre(patron);

        return persistAndPublish(patron);
    }

    @Override
    @Transactional
    public ReferenceStandard update(SaveReferenceStandardCommand command) {
        ReferenceStandard patron = findById(command.id());
        patron.update(command.nombre(), command.marca(), command.serie(), command.certificado(),
                command.vencimientoCertificado());
        exigirSerieLibre(patron);

        return persistAndPublish(patron);
    }

    @Override
    @Transactional
    public void deactivate(DeactivateReferenceStandardCommand command) {
        ReferenceStandard patron = findById(command.id());
        patron.deactivate();
        persistAndPublish(patron);
    }

    /** Otro vigente con la misma serie, que no sea este mismo. */
    private void exigirSerieLibre(ReferenceStandard patron) {
        if (!patron.isEstadoActivo()) {
            return;
        }

        referenceStandardPersistencePort.findActiveBySerie(patron.getSerie())
                .filter(otro -> !otro.getId().equals(patron.getId()))
                .ifPresent(otro -> {
                    throw new ReferenceStandardSerialTakenException(patron.getSerie(), otro.getNombre());
                });
    }

    private ReferenceStandard persistAndPublish(ReferenceStandard patron) {
        ReferenceStandard guardado = referenceStandardPersistencePort.save(patron);
        eventDispatcherPort.dispatchAll(patron.pullEvents());

        return guardado;
    }
}
