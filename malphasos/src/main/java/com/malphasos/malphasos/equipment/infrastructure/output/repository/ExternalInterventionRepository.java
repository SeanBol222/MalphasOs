package com.malphasos.malphasos.equipment.infrastructure.output.repository;

import com.malphasos.malphasos.equipment.infrastructure.output.entities.ExternalInterventionEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalInterventionRepository extends JpaRepository<ExternalInterventionEntity, UUID> {

    List<ExternalInterventionEntity> findByIdEquipoClienteAndEstadoActivoTrueOrderByRealizadaDescRegistradaDesc(
            UUID idEquipoCliente);

    List<ExternalInterventionEntity> findByIdEquipoClienteInAndEstadoActivoTrue(Collection<UUID> idsEquiposCliente);
}
