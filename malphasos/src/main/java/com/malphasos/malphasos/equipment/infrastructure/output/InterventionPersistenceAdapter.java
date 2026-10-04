package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.InterventionPersistencePort;
import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.InterventionEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.InterventionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el almacén de intervenciones sobre JPA.
 *
 * <p>El mapeo va a mano y en esta clase, sin mapper aparte, porque son seis campos planos sin
 * colecciones: un mapper propio para esto sería un archivo que solo añade un salto al leerlo. Los dos
 * enumerados viajan como texto, igual que en el resto del esquema, y es la base la que fija su
 * vocabulario con un {@code CHECK}.
 *
 * <p>Sin {@code @Transactional} en la lectura por una vez justificada: no hay ninguna colección
 * perezosa que recorrer, que es lo que obliga a ponerlo en los otros cinco adaptadores.
 */
@Component
@RequiredArgsConstructor
public class InterventionPersistenceAdapter implements InterventionPersistencePort {

    private final InterventionRepository interventionRepository;

    @Override
    public List<Intervention> findByEquipment(UUID idEquipoCliente) {
        return interventionRepository
                .findByIdEquipoClienteAndEstadoActivoTrueOrderByFechaServicioDesc(idEquipoCliente)
                .stream()
                .map(InterventionPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public boolean existsByReport(UUID idReporteServicio) {
        return interventionRepository.existsByIdReporteServicio(idReporteServicio);
    }

    @Override
    @Transactional
    public Intervention save(Intervention intervention) {
        return toDomain(interventionRepository.save(toEntity(intervention)));
    }

    private static InterventionEntity toEntity(Intervention intervention) {
        InterventionEntity entity = new InterventionEntity();
        entity.setId(intervention.id());
        entity.setIdEquipoCliente(intervention.idEquipoCliente());
        entity.setIdReporteServicio(intervention.idReporteServicio());
        entity.setFechaServicio(intervention.fechaServicio());
        entity.setTipoServicio(intervention.tipoServicio().name());
        entity.setResultado(intervention.resultado().name());
        entity.setEstadoActivo(intervention.estadoActivo());

        return entity;
    }

    private static Intervention toDomain(InterventionEntity entity) {
        return Intervention.rehydrate(
                entity.getId(),
                entity.getIdEquipoCliente(),
                entity.getIdReporteServicio(),
                entity.getFechaServicio(),
                InterventionType.valueOf(entity.getTipoServicio()),
                InterventionResult.valueOf(entity.getResultado()),
                entity.isEstadoActivo());
    }
}
