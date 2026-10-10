package com.malphasos.malphasos.equipment.domain.protocol;

import com.malphasos.malphasos.equipment.domain.protocol.events.ProtocolCreatedEvent;
import com.malphasos.malphasos.equipment.domain.protocol.events.ProtocolDeactivatedEvent;
import com.malphasos.malphasos.equipment.domain.protocol.events.ProtocolPayload;
import com.malphasos.malphasos.equipment.domain.protocol.events.ProtocolUpdatedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Una actividad de mantenimiento preventivo: «Limpieza y desinfección», «Verificación del sistema
 * eléctrico». RF-14.
 *
 * <p><b>Es una actividad y no la lista de un tipo de equipo</b>, decidido por el usuario el
 * 2026-10-10: la misma limpieza se repite en muchos tipos, así que se escribe una vez y se asigna a los
 * que la necesitan —ver {@link ProtocolAssignment}—. Por eso su texto es general; lo propio de cada tipo
 * va en la indicación de la asignación.
 *
 * <p><b>El nombre no se repite</b>, sin distinguir mayúsculas ni tildes: pedido expresamente, para que
 * no sea fácil duplicar protocolos. La comparación la hace {@link #clave(String)}, que se guarda en su
 * propia columna con un índice único.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Protocol extends AggregateRoot {

    static final int LARGO_NOMBRE = 120;
    static final int LARGO_DESCRIPCION = 500;

    @EqualsAndHashCode.Include
    private final UUID id;

    private String nombre;

    private String descripcion;

    private boolean estadoActivo;

    private Protocol(UUID id, String nombre, String descripcion, boolean estadoActivo) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.estadoActivo = estadoActivo;
    }

    public static Protocol create(String nombre, String descripcion) {
        Protocol protocolo = new Protocol(
                UUID.randomUUID(), validarNombre(nombre), validarDescripcion(descripcion), true);
        protocolo.registerEvent(new ProtocolCreatedEvent(
                protocolo.metadataFor(ProtocolCreatedEvent.TYPE), protocolo.payload()));

        return protocolo;
    }

    public static Protocol rehydrate(UUID id, String nombre, String descripcion, boolean estadoActivo) {
        return new Protocol(id, nombre, descripcion, estadoActivo);
    }

    /**
     * Cambia el nombre y la descripción. No hace nada si los dos son los que ya tenía.
     *
     * <p>Los reportes que ya copiaron este protocolo no cambian: guardan el texto que tenía ese día.
     */
    public void update(String nuevoNombre, String nuevaDescripcion) {
        String nombreValidado = validarNombre(nuevoNombre);
        String descripcionValidada = validarDescripcion(nuevaDescripcion);

        if (nombreValidado.equals(nombre) && descripcionValidada.equals(descripcion)) {
            return;
        }

        this.nombre = nombreValidado;
        this.descripcion = descripcionValidada;
        registerEvent(new ProtocolUpdatedEvent(metadataFor(ProtocolUpdatedEvent.TYPE), payload()));
    }

    /**
     * Retira el protocolo sin borrarlo. Retirar dos veces no emite dos eventos.
     *
     * <p>Que no esté asignado a ningún tipo vigente lo comprueba el servicio antes de llamar aquí: el
     * agregado no sabe a qué tipos está asignado.
     */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new ProtocolDeactivatedEvent(
                metadataFor(ProtocolDeactivatedEvent.TYPE), payload()));
    }

    /** La clave de este protocolo, con la que se comprueba que su nombre no se repita. */
    public String getClave() {
        return clave(nombre);
    }

    /**
     * Un nombre sin mayúsculas, sin tildes y con los espacios colapsados: «Limpieza y desinfección» y
     * «limpieza  y DESINFECCION» dan lo mismo. Es lo que guarda la columna {@code n_clave}.
     */
    public static String clave(String nombre) {
        return Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    @Override
    protected String aggregateType() {
        return "Protocol";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private ProtocolPayload payload() {
        return new ProtocolPayload(nombre, descripcion);
    }

    private static String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Un protocolo necesita nombre");
        }

        String limpio = nombre.trim().replaceAll("\\s+", " ");

        if (limpio.length() > LARGO_NOMBRE) {
            throw new IllegalArgumentException(
                    "El nombre de un protocolo tiene como mucho " + LARGO_NOMBRE + " caracteres");
        }

        return limpio;
    }

    private static String validarDescripcion(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException(
                    "Un protocolo necesita una descripción: es lo que dice qué hacer");
        }

        String limpia = descripcion.trim();

        if (limpia.length() > LARGO_DESCRIPCION) {
            throw new IllegalArgumentException(
                    "La descripción de un protocolo tiene como mucho " + LARGO_DESCRIPCION + " caracteres");
        }

        return limpia;
    }
}
