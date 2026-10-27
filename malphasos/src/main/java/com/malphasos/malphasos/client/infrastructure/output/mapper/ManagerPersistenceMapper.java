package com.malphasos.malphasos.client.infrastructure.output.mapper;

import com.malphasos.malphasos.client.domain.manager.Manager;
import com.malphasos.malphasos.client.domain.manager.ManagerAssignment;
import com.malphasos.malphasos.client.domain.manager.ManagerType;
import com.malphasos.malphasos.client.infrastructure.output.entities.ManagerHeadquarterEntity;
import com.malphasos.malphasos.client.infrastructure.output.entities.ManagerServiceAreaEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Traduce entre el agregado y sus filas.
 *
 * <p>Las dos formas no se corresponden fila a fila: el agregado es la persona con todas sus
 * asignaciones, y la base las reparte en dos tablas según el tipo (`V41`, 2026-10-27). Traducir en un
 * sentido es reunir por persona las filas de las dos; en el otro, mandar cada asignación a la suya.
 * (Hasta ese día el agregado guardaba una asignación y la tabla tenía dos columnas de las que solo una
 * valía, y este mapper elegía la columna según el tipo.)
 */
@Component
public class ManagerPersistenceMapper {

    /** Un encargado por persona, en el orden en que aparece por primera vez. */
    public List<Manager> toDomainList(
            List<ManagerHeadquarterEntity> sedes, List<ManagerServiceAreaEntity> areas) {

        Map<UUID, List<ManagerAssignment>> porPersona = new LinkedHashMap<>();

        sedes.forEach(fila -> porPersona.computeIfAbsent(fila.getPersona(), p -> new ArrayList<>())
                .add(new ManagerAssignment(ManagerType.HEADQUARTER, fila.getSede(), fila.isEstadoActivo())));
        areas.forEach(fila -> porPersona.computeIfAbsent(fila.getPersona(), p -> new ArrayList<>())
                .add(new ManagerAssignment(ManagerType.SERVICE_AREA, fila.getArea(), fila.isEstadoActivo())));

        return porPersona.entrySet().stream()
                .map(entrada -> Manager.rehydrate(entrada.getKey(), entrada.getValue()))
                .toList();
    }

    public List<ManagerHeadquarterEntity> toHeadquarterEntities(Manager encargado) {
        return encargado.getAsignaciones().stream()
                .filter(asignacion -> asignacion.tipo() == ManagerType.HEADQUARTER)
                .map(asignacion -> new ManagerHeadquarterEntity(
                        encargado.getIdPersona(), asignacion.idAsignacion(), asignacion.estadoActivo()))
                .toList();
    }

    public List<ManagerServiceAreaEntity> toServiceAreaEntities(Manager encargado) {
        return encargado.getAsignaciones().stream()
                .filter(asignacion -> asignacion.tipo() == ManagerType.SERVICE_AREA)
                .map(asignacion -> new ManagerServiceAreaEntity(
                        encargado.getIdPersona(), asignacion.idAsignacion(), asignacion.estadoActivo()))
                .toList();
    }
}
