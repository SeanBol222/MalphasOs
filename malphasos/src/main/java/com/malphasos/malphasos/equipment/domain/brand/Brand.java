package com.malphasos.malphasos.equipment.domain.brand;

import com.malphasos.malphasos.equipment.domain.brand.events.BrandCreatedEvent;
import com.malphasos.malphasos.equipment.domain.brand.events.BrandDeactivatedEvent;
import com.malphasos.malphasos.equipment.domain.brand.events.BrandPayload;
import com.malphasos.malphasos.equipment.domain.brand.events.BrandRenamedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Marca bajo la que se comercializa un equipo.
 *
 * <p>El nombre es obligatorio: es lo único que una marca tiene. En el esquema original la columna
 * era anulable.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Brand extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    private String nombre;

    private boolean estadoActivo;

    private Brand(UUID id, String nombre, boolean estadoActivo) {
        this.id = id;
        this.nombre = nombre;
        this.estadoActivo = estadoActivo;
    }

    public static Brand create(String nombre) {
        Brand marca = new Brand(UUID.randomUUID(), validarNombre(nombre), true);
        marca.registerEvent(new BrandCreatedEvent(
                marca.metadataFor(BrandCreatedEvent.TYPE), marca.payload()));

        return marca;
    }

    public static Brand rehydrate(UUID id, String nombre, boolean estadoActivo) {
        return new Brand(id, nombre, estadoActivo);
    }

    /** Cambia el nombre. No hace nada si es el que ya tenía. */
    public void rename(String nuevoNombre) {
        String validado = validarNombre(nuevoNombre);

        if (validado.equals(this.nombre)) {
            return;
        }

        this.nombre = validado;
        registerEvent(new BrandRenamedEvent(metadataFor(BrandRenamedEvent.TYPE), payload()));
    }

    /** Retira la marca sin borrarla. Retirar dos veces no emite dos eventos. */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new BrandDeactivatedEvent(
                metadataFor(BrandDeactivatedEvent.TYPE), payload()));
    }

    @Override
    protected String aggregateType() {
        return "Brand";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private BrandPayload payload() {
        return new BrandPayload(nombre);
    }

    private static String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Una marca necesita nombre");
        }

        return nombre.trim();
    }
}
