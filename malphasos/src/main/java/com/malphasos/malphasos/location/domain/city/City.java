package com.malphasos.malphasos.location.domain.city;

import com.malphasos.malphasos.location.domain.city.events.CityCreatedEvent;
import com.malphasos.malphasos.location.domain.city.events.CityDeactivatedEvent;
import com.malphasos.malphasos.location.domain.city.events.CityPayload;
import com.malphasos.malphasos.location.domain.city.events.CityRelocatedEvent;
import com.malphasos.malphasos.location.domain.city.events.CityRenamedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Ciudad donde un cliente puede tener sedes. Pertenece siempre a un país.
 *
 * <p>El original derivaba la llave primaria del nombre, tomando sus dos primeras letras en
 * mayúsculas ({@code createIdFromName}). Bogotá y Boyacá producían ambas {@code "BO"}, igual que
 * Medellín y Melgar producían {@code "ME"}: la segunda ciudad que empezara por las mismas dos
 * letras chocaba contra la llave primaria de la primera. Aquí la identidad es un UUID y el nombre
 * es solo un atributo, único dentro de su país.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class City extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    private String nombre;

    private UUID idPais;

    private boolean estadoActivo;

    private City(UUID id, String nombre, UUID idPais, boolean estadoActivo) {
        this.id = id;
        this.nombre = nombre;
        this.idPais = idPais;
        this.estadoActivo = estadoActivo;
    }

    /** Registra una ciudad nueva dentro de un país. */
    public static City create(String nombre, UUID idPais) {
        City ciudad = new City(UUID.randomUUID(), validarNombre(nombre), validarPais(idPais), true);
        ciudad.registerEvent(new CityCreatedEvent(
                ciudad.metadataFor(CityCreatedEvent.TYPE), ciudad.payload()));

        return ciudad;
    }

    /** Reconstruye una ciudad ya existente desde lo persistido, sin registrar eventos. */
    public static City rehydrate(UUID id, String nombre, UUID idPais, boolean estadoActivo) {
        return new City(id, nombre, idPais, estadoActivo);
    }

    /** Cambia el nombre de la ciudad. No hace nada si es el que ya tenía. */
    public void rename(String nuevoNombre) {
        String validado = validarNombre(nuevoNombre);

        if (validado.equals(this.nombre)) {
            return;
        }

        this.nombre = validado;
        registerEvent(new CityRenamedEvent(metadataFor(CityRenamedEvent.TYPE), payload()));
    }

    /**
     * Traslada la ciudad a otro país.
     *
     * <p>Es un hecho aparte del cambio de nombre porque afecta a otros: mueve la cobertura
     * geográfica de todas las sedes que hay en ella.
     */
    public void relocateTo(UUID nuevoPais) {
        UUID validado = validarPais(nuevoPais);

        if (validado.equals(this.idPais)) {
            return;
        }

        this.idPais = validado;
        registerEvent(new CityRelocatedEvent(metadataFor(CityRelocatedEvent.TYPE), payload()));
    }

    /** Retira la ciudad sin borrarla. Retirar dos veces no emite dos eventos. */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new CityDeactivatedEvent(metadataFor(CityDeactivatedEvent.TYPE), payload()));
    }

    @Override
    protected String aggregateType() {
        return "City";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private CityPayload payload() {
        return new CityPayload(nombre, idPais);
    }

    private static String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Una ciudad necesita nombre");
        }

        return nombre.trim();
    }

    private static UUID validarPais(UUID idPais) {
        if (idPais == null) {
            throw new IllegalArgumentException("Una ciudad pertenece siempre a un pais");
        }

        return idPais;
    }
}
