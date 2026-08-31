package com.malphasos.malphasos.client.domain.manager;

import com.malphasos.malphasos.client.domain.manager.events.ManagerAssignedEvent;
import com.malphasos.malphasos.client.domain.manager.events.ManagerDeactivatedEvent;
import com.malphasos.malphasos.client.domain.manager.events.ManagerPayload;
import com.malphasos.malphasos.client.domain.manager.events.ManagerReassignedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Persona responsable de una sede o de un área de servicio de un cliente.
 *
 * <p><b>Su identidad es la de la persona.</b> No hay un identificador propio: en el esquema, la
 * llave primaria de {@code encargado} es a la vez la clave foránea a {@code persona}. Un encargado
 * <i>es</i> una persona con ese rol, no un objeto que la referencia, y por eso no puede haber dos
 * encargados sobre la misma. En el original la relación existía en la base y en el orden en que un
 * método privado hacía dos llamadas, pero el modelo de dominio no la expresaba en ningún sitio.
 *
 * <p>Se distingue de la representación legal, que sí es de muchos a muchos: una persona puede
 * representar a varios clientes, pero solo puede encargarse de un sitio.
 *
 * <p>La regla que el agregado protege, y que el esquema original no ataba: <b>el tipo declara de
 * qué se encarga y la asignación tiene que respaldarlo</b>. Allí las dos columnas eran anulables
 * sin nada que las relacionara con el tipo, de modo que cabía un encargado de sede sin sede, con
 * área, con las dos o con ninguna.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Manager extends AggregateRoot {

    /** Identificador de la persona. Es también el del encargado. */
    @EqualsAndHashCode.Include
    private final UUID idPersona;

    private ManagerType tipo;

    /** Sede o área de la que se encarga, según diga {@link #tipo}. */
    private UUID idAsignacion;

    private boolean estadoActivo;

    private Manager(UUID idPersona, ManagerType tipo, UUID idAsignacion, boolean estadoActivo) {
        this.idPersona = idPersona;
        this.tipo = tipo;
        this.idAsignacion = idAsignacion;
        this.estadoActivo = estadoActivo;
    }

    /** Pone a una persona al frente de una sede. */
    public static Manager forHeadquarter(UUID idPersona, UUID idSede) {
        return assign(idPersona, ManagerType.HEADQUARTER, idSede);
    }

    /** Pone a una persona al frente de un área de servicio. */
    public static Manager forServiceArea(UUID idPersona, UUID idArea) {
        return assign(idPersona, ManagerType.SERVICE_AREA, idArea);
    }

    private static Manager assign(UUID idPersona, ManagerType tipo, UUID idAsignacion) {
        if (idPersona == null) {
            throw new IllegalArgumentException("Un encargado es una persona: falta su identificador");
        }
        if (idAsignacion == null) {
            throw new IllegalArgumentException(
                    "Un encargado de tipo " + tipo + " necesita la asignacion que le corresponde");
        }

        Manager encargado = new Manager(idPersona, tipo, idAsignacion, true);
        encargado.registerEvent(new ManagerAssignedEvent(
                encargado.metadataFor(ManagerAssignedEvent.TYPE), encargado.payload()));

        return encargado;
    }

    /** Reconstruye un encargado ya existente desde lo persistido, sin registrar eventos. */
    public static Manager rehydrate(
            UUID idPersona, ManagerType tipo, UUID idAsignacion, boolean estadoActivo) {

        return new Manager(idPersona, tipo, idAsignacion, estadoActivo);
    }

    /**
     * Lo pone al frente de otra sede o de otra área.
     *
     * <p>Cambiar de tipo es válido: un encargado de sede puede pasar a serlo de un área. Lo que no
     * puede es quedar sin asignación, ni tener una que no corresponda a su tipo.
     */
    public void reassignTo(ManagerType nuevoTipo, UUID nuevaAsignacion) {
        if (nuevoTipo == null || nuevaAsignacion == null) {
            throw new IllegalArgumentException("Una reasignacion necesita tipo y destino");
        }
        if (nuevoTipo == this.tipo && nuevaAsignacion.equals(this.idAsignacion)) {
            return;
        }

        this.tipo = nuevoTipo;
        this.idAsignacion = nuevaAsignacion;

        registerEvent(new ManagerReassignedEvent(
                metadataFor(ManagerReassignedEvent.TYPE), payload()));
    }

    /** Lo releva del cargo sin borrarlo. Relevar dos veces no emite dos eventos. */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new ManagerDeactivatedEvent(
                metadataFor(ManagerDeactivatedEvent.TYPE), payload()));
    }

    /** La sede de la que se encarga, o {@code null} si se encarga de un área. */
    public UUID getIdSede() {
        return tipo == ManagerType.HEADQUARTER ? idAsignacion : null;
    }

    /** El área de la que se encarga, o {@code null} si se encarga de una sede. */
    public UUID getIdAreaServicio() {
        return tipo == ManagerType.SERVICE_AREA ? idAsignacion : null;
    }

    @Override
    protected String aggregateType() {
        return "Manager";
    }

    @Override
    protected String aggregateId() {
        return idPersona.toString();
    }

    private ManagerPayload payload() {
        return new ManagerPayload(tipo, idAsignacion);
    }
}
