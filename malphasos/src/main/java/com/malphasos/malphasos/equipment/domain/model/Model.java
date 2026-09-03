package com.malphasos.malphasos.equipment.domain.model;

import com.malphasos.malphasos.equipment.domain.model.events.ModelCreatedEvent;
import com.malphasos.malphasos.equipment.domain.model.events.ModelDeactivatedEvent;
import com.malphasos.malphasos.equipment.domain.model.events.ModelPayload;
import com.malphasos.malphasos.equipment.domain.model.events.ModelUpdatedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Modelo concreto de equipo, producido por un fabricante.
 *
 * <p>El fabricante y la asociación marca-tipo son obligatorios y no cambian: un modelo pertenece a
 * quien lo fabrica y a la combinación que representa. En el original ambas referencias eran
 * anulables en el esquema y modificables desde el dominio, de modo que cabía un modelo que no
 * pertenecía a nada.
 *
 * <p>El registro INVIMA sí puede cambiar: se tramita después de dar de alta el modelo, y se corrige
 * si llega mal.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Model extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    /** Registro sanitario. Puede faltar mientras se tramita. */
    private String invima;

    private final UUID idFabricante;

    private final UUID idEquipo;

    private boolean estadoActivo;

    private Model(UUID id, String invima, UUID idFabricante, UUID idEquipo, boolean estadoActivo) {
        this.id = id;
        this.invima = invima;
        this.idFabricante = idFabricante;
        this.idEquipo = idEquipo;
        this.estadoActivo = estadoActivo;
    }

    public static Model create(String invima, UUID idFabricante, UUID idEquipo) {
        Model modelo = new Model(
                UUID.randomUUID(),
                normalizar(invima),
                exigir(idFabricante, "fabricante"),
                exigir(idEquipo, "equipo"),
                true);

        modelo.registerEvent(new ModelCreatedEvent(
                modelo.metadataFor(ModelCreatedEvent.TYPE), modelo.payload()));

        return modelo;
    }

    public static Model rehydrate(
            UUID id, String invima, UUID idFabricante, UUID idEquipo, boolean estadoActivo) {

        return new Model(id, invima, idFabricante, idEquipo, estadoActivo);
    }

    /** Anota o corrige el registro INVIMA. No hace nada si es el que ya tenía. */
    public void changeInvima(String invima) {
        String normalizado = normalizar(invima);

        if (java.util.Objects.equals(normalizado, this.invima)) {
            return;
        }

        this.invima = normalizado;
        registerEvent(new ModelUpdatedEvent(metadataFor(ModelUpdatedEvent.TYPE), payload()));
    }

    /** Retira el modelo sin borrarlo. Retirar dos veces no emite dos eventos. */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new ModelDeactivatedEvent(
                metadataFor(ModelDeactivatedEvent.TYPE), payload()));
    }

    @Override
    protected String aggregateType() {
        return "Model";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private ModelPayload payload() {
        return new ModelPayload(invima, idFabricante, idEquipo);
    }

    /** Un registro en blanco es lo mismo que no tenerlo. */
    private static String normalizar(String invima) {
        return invima == null || invima.isBlank() ? null : invima.trim();
    }

    private static UUID exigir(UUID valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException("Un modelo necesita su " + campo);
        }

        return valor;
    }
}
