package com.malphasos.malphasos.client.domain.serviceArea;

import com.malphasos.malphasos.client.domain.serviceArea.events.ServiceAreaCreatedEvent;
import com.malphasos.malphasos.client.domain.serviceArea.events.ServiceAreaDeactivatedEvent;
import com.malphasos.malphasos.client.domain.serviceArea.events.ServiceAreaPayload;
import com.malphasos.malphasos.client.domain.serviceArea.events.ServiceAreaRenamedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Área de servicio dentro de una sede: la unidad donde vive el equipo del cliente.
 *
 * <p>Un área no se traslada entre sedes. Si el equipo se mueve a otra parte, lo que corresponde es
 * cerrar el área y abrir otra, no rebautizar la existente: los equipos y los mantenimientos que
 * cuelgan de ella pertenecen a la sede donde ocurrieron.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ServiceArea extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    private String nombre;

    private final UUID idSede;

    private boolean estadoActivo;

    private ServiceArea(UUID id, String nombre, UUID idSede, boolean estadoActivo) {
        this.id = id;
        this.nombre = nombre;
        this.idSede = idSede;
        this.estadoActivo = estadoActivo;
    }

    public static ServiceArea create(String nombre, UUID idSede) {
        if (idSede == null) {
            throw new IllegalArgumentException("Un area de servicio pertenece siempre a una sede");
        }

        ServiceArea area = new ServiceArea(UUID.randomUUID(), validarNombre(nombre), idSede, true);
        area.registerEvent(new ServiceAreaCreatedEvent(
                area.metadataFor(ServiceAreaCreatedEvent.TYPE), area.payload()));

        return area;
    }

    public static ServiceArea rehydrate(UUID id, String nombre, UUID idSede, boolean estadoActivo) {
        return new ServiceArea(id, nombre, idSede, estadoActivo);
    }

    /** Cambia el nombre. No hace nada si es el que ya tenía. */
    public void rename(String nuevoNombre) {
        String validado = validarNombre(nuevoNombre);

        if (validado.equals(this.nombre)) {
            return;
        }

        this.nombre = validado;
        registerEvent(new ServiceAreaRenamedEvent(metadataFor(ServiceAreaRenamedEvent.TYPE), payload()));
    }

    /** Cierra el área sin borrarla. Cerrar dos veces no emite dos eventos. */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new ServiceAreaDeactivatedEvent(
                metadataFor(ServiceAreaDeactivatedEvent.TYPE), payload()));
    }

    @Override
    protected String aggregateType() {
        return "ServiceArea";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private ServiceAreaPayload payload() {
        return new ServiceAreaPayload(nombre, idSede);
    }

    private static String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Un area de servicio necesita nombre");
        }

        return nombre.trim();
    }
}
