package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.ExternalInterventionPersistencePort;
import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalIntervention;
import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalServiceType;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.ExternalInterventionEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.ExternalInterventionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Las intervenciones externas sobre JPA, con el mapeo a mano como en todo agregado. */
@Component
@RequiredArgsConstructor
public class ExternalInterventionPersistenceAdapter implements ExternalInterventionPersistencePort {

    private final ExternalInterventionRepository repository;

    @Override
    public Optional<ExternalIntervention> findById(UUID id) {
        return repository.findById(id).map(ExternalInterventionPersistenceAdapter::toDomain);
    }

    @Override
    public List<ExternalIntervention> findActiveByEquipment(UUID idEquipoCliente) {
        return repository.findByIdEquipoClienteAndEstadoActivoTrueOrderByRealizadaDescRegistradaDesc(idEquipoCliente)
                .stream().map(ExternalInterventionPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<ExternalIntervention> findActiveByEquipments(Collection<UUID> idsEquiposCliente) {
        if (idsEquiposCliente.isEmpty()) {
            return List.of();
        }
        return repository.findByIdEquipoClienteInAndEstadoActivoTrue(idsEquiposCliente).stream()
                .map(ExternalInterventionPersistenceAdapter::toDomain).toList();
    }

    @Override
    public ExternalIntervention save(ExternalIntervention intervencion) {
        // saveAndFlush: la fecha de registro la pone la base, y solo vuelve tras escribir.
        return toDomain(repository.saveAndFlush(new ExternalInterventionEntity(
                intervencion.getId(), intervencion.getIdEquipoCliente(), intervencion.getRealizada(),
                intervencion.getTipoServicio().name(), intervencion.getDescripcion(), intervencion.getEmpresa(),
                intervencion.getResultado() == null ? null : intervencion.getResultado().name(),
                intervencion.getDocumento(), intervencion.getRegistrada(), intervencion.isEstadoActivo())));
    }

    private static ExternalIntervention toDomain(ExternalInterventionEntity entity) {
        return ExternalIntervention.rehydrate(entity.getId(), entity.getIdEquipoCliente(), entity.getRealizada(),
                ExternalServiceType.valueOf(entity.getTipoServicio()), entity.getDescripcion(), entity.getEmpresa(),
                entity.getResultado() == null ? null : InterventionResult.valueOf(entity.getResultado()),
                entity.getDocumento(), entity.getRegistrada(), entity.isEstadoActivo());
    }
}
