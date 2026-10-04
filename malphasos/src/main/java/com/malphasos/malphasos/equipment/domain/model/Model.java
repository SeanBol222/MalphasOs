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
 * <p><b>El nombre es lo que distingue un modelo de otro de la misma marca</b>: si la marca es Lenovo y
 * el tipo «portátil», el nombre es «IdeaPad 3». Entró el 2026-10-04, y hasta entonces lo único legible
 * que un modelo llevaba era su registro INVIMA — que es un número de trámite, es anulable y se obtiene
 * <i>después</i> de dar de alta el modelo, de modo que un modelo podía existir sin nada que escribir en
 * una pantalla. Ahora es obligatorio.
 *
 * <p>El registro INVIMA sí puede faltar y sí puede cambiar: se tramita después de dar de alta el
 * modelo, y se corrige si llega mal. <b>El nombre también se corrige</b> —una errata es una errata—
 * pero por su propia operación, igual que una marca se renombra.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Model extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    /** Nombre comercial: «IdeaPad 3». Obligatorio. */
    private String nombre;

    /** Registro sanitario. Puede faltar mientras se tramita. */
    private String invima;

    private final UUID idFabricante;

    private final UUID idEquipo;

    private boolean estadoActivo;

    private Model(
            UUID id,
            String nombre,
            String invima,
            UUID idFabricante,
            UUID idEquipo,
            boolean estadoActivo) {

        this.id = id;
        this.nombre = nombre;
        this.invima = invima;
        this.idFabricante = idFabricante;
        this.idEquipo = idEquipo;
        this.estadoActivo = estadoActivo;
    }

    public static Model create(String nombre, String invima, UUID idFabricante, UUID idEquipo) {
        Model modelo = new Model(
                UUID.randomUUID(),
                exigirNombre(nombre),
                normalizar(invima),
                exigir(idFabricante, "fabricante"),
                exigir(idEquipo, "equipo"),
                true);

        modelo.registerEvent(new ModelCreatedEvent(
                modelo.metadataFor(ModelCreatedEvent.TYPE), modelo.payload()));

        return modelo;
    }

    public static Model rehydrate(
            UUID id,
            String nombre,
            String invima,
            UUID idFabricante,
            UUID idEquipo,
            boolean estadoActivo) {

        // Sin validar: leer de la base no es un hecho del dominio, y una fila vieja que ya no cumple
        // una regla nueva tiene que poder cargarse para poder corregirla.
        return new Model(id, nombre, invima, idFabricante, idEquipo, estadoActivo);
    }

    /** Corrige el nombre. No hace nada si es el que ya tenía. */
    public void rename(String nombre) {
        String nuevo = exigirNombre(nombre);

        if (nuevo.equals(this.nombre)) {
            // Un cambio que no cambia nada no emite evento.
            return;
        }

        this.nombre = nuevo;
        registerEvent(new ModelUpdatedEvent(metadataFor(ModelUpdatedEvent.TYPE), payload()));
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
        return new ModelPayload(nombre, invima, idFabricante, idEquipo);
    }

    /**
     * El nombre es obligatorio y no admite blancos.
     *
     * <p>Al contrario que el INVIMA, donde un texto en blanco **significa** «no tiene»: un modelo sin
     * registro sanitario es un estado normal mientras se tramita, y un modelo sin nombre no es nada.
     */
    private static String exigirNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                    "Un modelo necesita su nombre: es lo que lo distingue de los otros de su marca");
        }

        return nombre.trim();
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
