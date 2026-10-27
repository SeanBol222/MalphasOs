package com.malphasos.malphasos.client.domain.manager;

import com.malphasos.malphasos.client.domain.manager.events.ManagerAssignedEvent;
import com.malphasos.malphasos.client.domain.manager.events.ManagerDeactivatedEvent;
import com.malphasos.malphasos.client.domain.manager.events.ManagerPayload;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Persona responsable de sedes o áreas de servicio de un cliente.
 *
 * <p><b>Su identidad es la de la persona.</b> No hay un identificador propio: un encargado <i>es</i>
 * una persona con ese rol, no un objeto que la referencia. En el original la relación existía en la
 * base y en el orden en que un método privado hacía dos llamadas, pero el modelo de dominio no la
 * expresaba en ningún sitio.
 *
 * <p><b>Desde el 2026-10-27 está a cargo de varias cosas</b> (`V41`): hasta entonces la llave de
 * {@code encargado} era la persona, y cada persona podía estar a cargo de una sola sede o de una sola
 * área. El usuario confirmó que en sus clientes un encargado cubre varias. Cada asignación se da y se
 * quita por separado, y quitarla no la borra: queda inactiva, y volver a darla la reactiva.
 *
 * <p>La regla de que el tipo y el destino casen, que el esquema original no ataba —cabía un encargado
 * de sede sin sede, con área, con las dos o con ninguna—, la sostiene ahora {@link ManagerAssignment}
 * por su forma, y en la base, que cada tipo tenga su tabla. La de que todas sean <b>del mismo
 * cliente</b> la comprueba el servicio: cuál es el cliente de una sede o de un área no lo sabe este
 * agregado.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Manager extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID idPersona;

    @Getter(lombok.AccessLevel.NONE)
    private final List<ManagerAssignment> asignaciones;

    private Manager(UUID idPersona, List<ManagerAssignment> asignaciones) {
        this.idPersona = idPersona;
        this.asignaciones = new ArrayList<>(asignaciones);
    }

    public static Manager forHeadquarter(UUID idPersona, UUID idSede) {
        return assign(idPersona, ManagerType.HEADQUARTER, idSede);
    }

    public static Manager forServiceArea(UUID idPersona, UUID idArea) {
        return assign(idPersona, ManagerType.SERVICE_AREA, idArea);
    }

    private static Manager assign(UUID idPersona, ManagerType tipo, UUID idAsignacion) {
        if (idPersona == null) {
            throw new IllegalArgumentException("Un encargado es una persona: falta su identificador");
        }
        exigirDestino(tipo, idAsignacion);

        Manager encargado = new Manager(idPersona, List.of());
        encargado.assignTo(tipo, idAsignacion);

        return encargado;
    }

    public static Manager rehydrate(UUID idPersona, List<ManagerAssignment> asignaciones) {
        return new Manager(idPersona, asignaciones);
    }

    /**
     * Lo pone también al frente de esa sede o de esa área.
     *
     * <p>Si ya lo está, no cambia nada y no emite evento. Si lo estuvo y se le retiró, la asignación
     * se reactiva en lugar de duplicarse: en la base su llave es el par (persona, destino).
     */
    public void assignTo(ManagerType tipo, UUID idAsignacion) {
        exigirDestino(tipo, idAsignacion);

        Optional<ManagerAssignment> existente = buscar(tipo, idAsignacion);

        if (existente.filter(ManagerAssignment::estadoActivo).isPresent()) {
            return;
        }
        existente.ifPresent(asignaciones::remove);
        asignaciones.add(new ManagerAssignment(tipo, idAsignacion, true));

        registerEvent(new ManagerAssignedEvent(
                metadataFor(ManagerAssignedEvent.TYPE), new ManagerPayload(tipo, idAsignacion)));
    }

    /**
     * Lo releva de esa sede o de esa área, y solo de esa.
     *
     * <p>Relevarlo de algo de lo que ya no estaba a cargo no cambia nada. De algo de lo que nunca lo
     * estuvo es un error de quien llama, y se dice.
     *
     * @return si estaba a cargo de eso, activo o no; {@code false} si nunca lo estuvo
     */
    public boolean relieveFrom(ManagerType tipo, UUID idAsignacion) {
        exigirDestino(tipo, idAsignacion);

        Optional<ManagerAssignment> existente = buscar(tipo, idAsignacion);

        if (existente.isEmpty()) {
            return false;
        }
        if (existente.get().estadoActivo()) {
            relevar(existente.get());
        }

        return true;
    }

    /** Lo releva de todo. No lo borra: cada asignación queda inactiva. */
    public void deactivate() {
        List.copyOf(asignaciones).stream().filter(ManagerAssignment::estadoActivo).forEach(this::relevar);
    }

    /** Si está a cargo de algo: con todo retirado sigue existiendo, pero no responde por nada. */
    public boolean isEstadoActivo() {
        return asignaciones.stream().anyMatch(ManagerAssignment::estadoActivo);
    }

    /** Todas sus asignaciones, activas o no, en una copia inmutable. */
    public List<ManagerAssignment> getAsignaciones() {
        return List.copyOf(asignaciones);
    }

    /** Las sedes de las que está a cargo ahora. */
    public List<UUID> getIdsSede() {
        return activas(ManagerType.HEADQUARTER);
    }

    /** Las áreas de las que está a cargo ahora. */
    public List<UUID> getIdsAreaServicio() {
        return activas(ManagerType.SERVICE_AREA);
    }

    private List<UUID> activas(ManagerType tipo) {
        return asignaciones.stream()
                .filter(asignacion -> asignacion.estadoActivo() && asignacion.tipo() == tipo)
                .map(ManagerAssignment::idAsignacion)
                .toList();
    }

    private void relevar(ManagerAssignment asignacion) {
        asignaciones.set(asignaciones.indexOf(asignacion), asignacion.conEstado(false));

        registerEvent(new ManagerDeactivatedEvent(
                metadataFor(ManagerDeactivatedEvent.TYPE),
                new ManagerPayload(asignacion.tipo(), asignacion.idAsignacion())));
    }

    private Optional<ManagerAssignment> buscar(ManagerType tipo, UUID idAsignacion) {
        return asignaciones.stream().filter(asignacion -> asignacion.es(tipo, idAsignacion)).findFirst();
    }

    private static void exigirDestino(ManagerType tipo, UUID idAsignacion) {
        if (tipo == null) {
            throw new IllegalArgumentException("Un encargado necesita saber de que se encarga");
        }
        if (idAsignacion == null) {
            throw new IllegalArgumentException(
                    "Un encargado de tipo " + tipo + " necesita la asignacion que le corresponde");
        }
    }

    @Override
    protected String aggregateType() {
        return "Manager";
    }

    @Override
    protected String aggregateId() {
        return idPersona.toString();
    }
}
