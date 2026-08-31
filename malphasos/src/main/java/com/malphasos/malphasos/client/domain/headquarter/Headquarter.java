package com.malphasos.malphasos.client.domain.headquarter;

import com.malphasos.malphasos.client.domain.headquarter.events.HeadquarterCreatedEvent;
import com.malphasos.malphasos.client.domain.headquarter.events.HeadquarterDeactivatedEvent;
import com.malphasos.malphasos.client.domain.headquarter.events.HeadquarterPayload;
import com.malphasos.malphasos.client.domain.headquarter.events.HeadquarterUpdatedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Sede de un cliente, en una ciudad concreta.
 *
 * <p>Agregado propio, no parte de {@link com.malphasos.malphasos.client.domain.client.Client}: el
 * cliente y la ciudad se referencian por identificador. Sus áreas de servicio son a su vez
 * agregados aparte, de modo que abrir un área no bloquea la sede.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Headquarter extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    private String nombre;

    private Address direccion;

    /** Cliente dueño de la sede. No cambia: una sede no se traspasa entre clientes. */
    private final UUID idCliente;

    private UUID idCiudad;

    private boolean estadoActivo;

    private Headquarter(
            UUID id, String nombre, Address direccion, UUID idCliente, UUID idCiudad, boolean estadoActivo) {

        this.id = id;
        this.nombre = nombre;
        this.direccion = direccion;
        this.idCliente = idCliente;
        this.idCiudad = idCiudad;
        this.estadoActivo = estadoActivo;
    }

    public static Headquarter create(String nombre, Address direccion, UUID idCliente, UUID idCiudad) {
        Headquarter sede = new Headquarter(
                UUID.randomUUID(),
                validarNombre(nombre),
                exigir(direccion, "direccion"),
                exigir(idCliente, "cliente"),
                exigir(idCiudad, "ciudad"),
                true);

        sede.registerEvent(new HeadquarterCreatedEvent(
                sede.metadataFor(HeadquarterCreatedEvent.TYPE), sede.payload()));

        return sede;
    }

    public static Headquarter rehydrate(
            UUID id, String nombre, Address direccion, UUID idCliente, UUID idCiudad, boolean estadoActivo) {

        return new Headquarter(id, nombre, direccion, idCliente, idCiudad, estadoActivo);
    }

    /** Cambia nombre, dirección o ciudad. Un valor nulo deja el campo como está. */
    public void update(String nombre, Address direccion, UUID idCiudad) {
        boolean cambio = false;

        if (nombre != null) {
            String validado = validarNombre(nombre);
            if (!validado.equals(this.nombre)) {
                this.nombre = validado;
                cambio = true;
            }
        }
        if (direccion != null && !direccion.equals(this.direccion)) {
            this.direccion = direccion;
            cambio = true;
        }
        if (idCiudad != null && !idCiudad.equals(this.idCiudad)) {
            this.idCiudad = idCiudad;
            cambio = true;
        }

        if (cambio) {
            registerEvent(new HeadquarterUpdatedEvent(
                    metadataFor(HeadquarterUpdatedEvent.TYPE), payload()));
        }
    }

    /** Cierra la sede sin borrarla. Cerrar dos veces no emite dos eventos. */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new HeadquarterDeactivatedEvent(
                metadataFor(HeadquarterDeactivatedEvent.TYPE), payload()));
    }

    @Override
    protected String aggregateType() {
        return "Headquarter";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private HeadquarterPayload payload() {
        return new HeadquarterPayload(nombre, idCliente, idCiudad);
    }

    private static String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Una sede necesita nombre");
        }

        return nombre.trim();
    }

    private static <T> T exigir(T valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException("Una sede necesita " + campo);
        }

        return valor;
    }
}
