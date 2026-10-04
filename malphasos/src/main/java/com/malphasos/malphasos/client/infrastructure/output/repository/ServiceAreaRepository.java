package com.malphasos.malphasos.client.infrastructure.output.repository;

import com.malphasos.malphasos.client.infrastructure.output.entities.ServiceAreaEntity;
import com.malphasos.malphasos.client.infrastructure.output.entities.HeadquarterEntity;
import java.util.List;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceAreaRepository extends JpaRepository<ServiceAreaEntity, UUID> {

    List<ServiceAreaEntity> findByIdSede(UUID idSede);

    /**
     * Áreas de un conjunto de clientes, uniendo por la sede.
     *
     * <p>Es una unión explícita en el {@code where} y no por relación, porque {@code idSede} es un
     * UUID suelto: este proyecto referencia entre agregados por identificador y no por objeto, de
     * modo que no hay {@code @ManyToOne} que recorrer. Las dos entidades son de este módulo, así que
     * la consulta no cruza ninguna frontera.
     *
     * <p><b>No mira el estado activo de nada</b>, y es deliberado: el alcance dice de quién son las
     * cosas, no si están vigentes. Los listados devuelven lo retirado a todo el mundo —el borrado es
     * lógico y el historial se conserva—, así que esconderle a un representante el equipo de un área
     * que cerró le daría una vista distinta de la del ingeniero que la cerró. Es la misma razón por
     * la que un cliente retirado sigue en el alcance de su representante: lo que decide el acceso es
     * que el nombramiento siga vivo, no que el dato siga activo.
     */
    @Query("""
            select a.id
            from ServiceAreaEntity a, HeadquarterEntity s
            where a.idSede = s.id
              and s.idCliente in :idsClientes
            """)
    Set<UUID> findIdsByClients(@Param("idsClientes") Collection<UUID> idsClientes);
}
