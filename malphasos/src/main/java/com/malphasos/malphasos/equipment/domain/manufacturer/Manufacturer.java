package com.malphasos.malphasos.equipment.domain.manufacturer;

import com.malphasos.malphasos.equipment.domain.manufacturer.events.ManufacturerCreatedEvent;
import com.malphasos.malphasos.equipment.domain.manufacturer.events.ManufacturerDeactivatedEvent;
import com.malphasos.malphasos.equipment.domain.manufacturer.events.ManufacturerPayload;
import com.malphasos.malphasos.equipment.domain.manufacturer.events.ManufacturerUpdatedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Empresa que produce modelos de equipos. */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Manufacturer extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    private String nombre;

    /** País de origen. Puede faltar: el esquema lo admite nulo. */
    private UUID idPais;

    private boolean estadoActivo;

    private Manufacturer(UUID id, String nombre, UUID idPais, boolean estadoActivo) {
        this.id = id;
        this.nombre = nombre;
        this.idPais = idPais;
        this.estadoActivo = estadoActivo;
    }

    public static Manufacturer create(String nombre, UUID idPais) {
        Manufacturer fabricante =
                new Manufacturer(UUID.randomUUID(), validarNombre(nombre), idPais, true);

        fabricante.registerEvent(new ManufacturerCreatedEvent(
                fabricante.metadataFor(ManufacturerCreatedEvent.TYPE), fabricante.payload()));

        return fabricante;
    }

    public static Manufacturer rehydrate(UUID id, String nombre, UUID idPais, boolean estadoActivo) {
        return new Manufacturer(id, nombre, idPais, estadoActivo);
    }

    /** Cambia el nombre o el país. Un valor nulo deja el campo como está. */
    public void update(String nombre, UUID idPais) {
        boolean cambio = false;

        if (nombre != null) {
            String validado = validarNombre(nombre);
            if (!validado.equals(this.nombre)) {
                this.nombre = validado;
                cambio = true;
            }
        }
        if (idPais != null && !idPais.equals(this.idPais)) {
            this.idPais = idPais;
            cambio = true;
        }

        if (cambio) {
            registerEvent(new ManufacturerUpdatedEvent(
                    metadataFor(ManufacturerUpdatedEvent.TYPE), payload()));
        }
    }

    /** Retira el fabricante sin borrarlo. Retirar dos veces no emite dos eventos. */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new ManufacturerDeactivatedEvent(
                metadataFor(ManufacturerDeactivatedEvent.TYPE), payload()));
    }

    @Override
    protected String aggregateType() {
        return "Manufacturer";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private ManufacturerPayload payload() {
        return new ManufacturerPayload(nombre, idPais);
    }

    private static String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Un fabricante necesita nombre");
        }

        return nombre.trim();
    }
}
