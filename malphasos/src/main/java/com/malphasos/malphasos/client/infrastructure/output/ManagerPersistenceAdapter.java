package com.malphasos.malphasos.client.infrastructure.output;

import com.malphasos.malphasos.client.application.ports.output.ManagerPersistencePort;
import com.malphasos.malphasos.client.domain.manager.Manager;
import com.malphasos.malphasos.client.infrastructure.output.entities.ManagerHeadquarterEntity;
import com.malphasos.malphasos.client.infrastructure.output.entities.ManagerServiceAreaEntity;
import com.malphasos.malphasos.client.infrastructure.output.mapper.ManagerPersistenceMapper;
import com.malphasos.malphasos.client.infrastructure.output.repository.ManagerHeadquarterRepository;
import com.malphasos.malphasos.client.infrastructure.output.repository.ManagerServiceAreaRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el almacén de encargados sobre JPA, con una tabla para sus sedes y otra para sus áreas
 * (`V41`, 2026-10-27).
 *
 * <p>Un encargado se lee siempre <b>entero</b>: las filas de las dos tablas de cada persona. Quien
 * pregunta quién responde por una sede recibe a esas personas con todo lo que tienen a cargo, no solo
 * la fila de esa sede, porque es eso lo que el agregado es. Lleva {@code @Transactional} porque guardar
 * son varias filas y leer, dos consultas que tienen que ver el mismo estado.
 */
@Component
@RequiredArgsConstructor
public class ManagerPersistenceAdapter implements ManagerPersistencePort {

    private final ManagerHeadquarterRepository sedes;
    private final ManagerServiceAreaRepository areas;
    private final ManagerPersistenceMapper managerPersistenceMapper;

    @Override
    @Transactional(readOnly = true)
    public List<Manager> findAll() {
        return managerPersistenceMapper.toDomainList(sedes.findAll(), areas.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Manager> findByPerson(UUID idPersona) {
        return deLasPersonas(List.of(idPersona)).stream().findFirst();
    }

    /** Quienes están a cargo de esa sede ahora; los relevados de ella no cuentan. */
    @Override
    @Transactional(readOnly = true)
    public List<Manager> findByHeadquarter(UUID idSede) {
        return deLasPersonas(sedes.findBySedeAndEstadoActivoTrue(idSede).stream()
                .map(ManagerHeadquarterEntity::getPersona)
                .toList());
    }

    /** Quienes están a cargo de esa área ahora. */
    @Override
    @Transactional(readOnly = true)
    public List<Manager> findByServiceArea(UUID idArea) {
        return deLasPersonas(areas.findByAreaAndEstadoActivoTrue(idArea).stream()
                .map(ManagerServiceAreaEntity::getPersona)
                .toList());
    }

    /**
     * Guarda cada asignación en su tabla. Ninguna se borra: relevar es guardarla inactiva, y el
     * agregado no pierde ninguna, de modo que basta con escribir las que trae.
     */
    @Override
    @Transactional
    public Manager save(Manager manager) {
        sedes.saveAll(managerPersistenceMapper.toHeadquarterEntities(manager));
        areas.saveAll(managerPersistenceMapper.toServiceAreaEntities(manager));

        return findByPerson(manager.getIdPersona()).orElseThrow();
    }

    private List<Manager> deLasPersonas(Collection<UUID> personas) {
        if (personas.isEmpty()) {
            return List.of();
        }
        Set<UUID> unicas = new LinkedHashSet<>(personas);

        return managerPersistenceMapper.toDomainList(
                sedes.findByPersonaIn(unicas), areas.findByPersonaIn(unicas));
    }
}
