package com.malphasos.malphasos.location.domain.country;

import com.malphasos.malphasos.location.domain.country.events.CountryCreatedEvent;
import com.malphasos.malphasos.location.domain.country.events.CountryDeactivatedEvent;
import com.malphasos.malphasos.location.domain.country.events.CountryPayload;
import com.malphasos.malphasos.location.domain.country.events.CountryRenamedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * País de referencia. Lo consultan los clientes y los fabricantes de equipos.
 *
 * <p>Primer agregado de Generación 2 del proyecto: su estado solo cambia a través de métodos que
 * dicen qué ocurrió, y cada cambio registra su evento. No tiene setters a propósito. El original
 * anotaba estas clases con {@code @Data}, que genera un setter público por campo, de modo que
 * cualquiera podía renombrar un país sin que se emitiera nada y sin pasar por ninguna validación:
 * el agregado guardaba las reglas por la puerta principal y las dejaba abiertas por la de atrás.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Country extends AggregateRoot {

    /** Tres letras mayúsculas, igual que la restricción del esquema. */
    private static final Pattern CODIGO_ISO = Pattern.compile("^[A-Z]{3}$");

    /**
     * Identidad del agregado.
     *
     * <p>La igualdad se define solo por aquí: dos objetos que representan el mismo país lo son
     * aunque su nombre difiera, porque uno puede ser una versión más vieja del otro. El original
     * usaba {@code @EqualsAndHashCode(callSuper = true)} sobre una superclase que no redefine
     * {@code equals}, con lo que terminaba delegando en la comparación por referencia de
     * {@code Object}: dos países con exactamente los mismos datos nunca resultaban iguales.
     */
    @EqualsAndHashCode.Include
    private final UUID id;

    /**
     * Código ISO 3166-1 alfa-3.
     *
     * <p>No cambia una vez asignado. Es la referencia estable del país frente al resto del mundo;
     * si de verdad hiciera falta corregirlo, se trata como un país distinto.
     */
    private final String codigoIso;

    private String nombre;

    private boolean estadoActivo;

    private Country(UUID id, String codigoIso, String nombre, boolean estadoActivo) {
        this.id = id;
        this.codigoIso = codigoIso;
        this.nombre = nombre;
        this.estadoActivo = estadoActivo;
    }

    /** Registra un país nuevo. */
    public static Country create(String codigoIso, String nombre) {
        String codigoNormalizado = normalizarCodigo(codigoIso);
        String nombreValidado = validarNombre(nombre);

        Country pais = new Country(UUID.randomUUID(), codigoNormalizado, nombreValidado, true);
        pais.registerEvent(new CountryCreatedEvent(
                pais.metadataFor(CountryCreatedEvent.TYPE), pais.payload()));

        return pais;
    }

    /**
     * Reconstruye un país ya existente a partir de lo persistido, sin registrar ningún evento.
     *
     * <p>Recuperar algo de la base de datos no es un hecho del dominio: si esto emitiera, cada
     * lectura publicaría un evento de creación.
     */
    public static Country rehydrate(UUID id, String codigoIso, String nombre, boolean estadoActivo) {
        return new Country(id, codigoIso, nombre, estadoActivo);
    }

    /** Cambia el nombre del país. No hace nada si el nombre es el que ya tenía. */
    public void rename(String nuevoNombre) {
        String validado = validarNombre(nuevoNombre);

        if (validado.equals(this.nombre)) {
            return;
        }

        this.nombre = validado;
        registerEvent(new CountryRenamedEvent(metadataFor(CountryRenamedEvent.TYPE), payload()));
    }

    /**
     * Retira el país sin borrarlo, conservando el historial.
     *
     * <p>Retirar dos veces no emite dos eventos: el segundo intento no cambia nada, y anunciar un
     * cambio que no ocurrió obligaría a cada consumidor a defenderse de duplicados.
     */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new CountryDeactivatedEvent(
                metadataFor(CountryDeactivatedEvent.TYPE), payload()));
    }

    @Override
    protected String aggregateType() {
        return "Country";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private CountryPayload payload() {
        return new CountryPayload(codigoIso, nombre);
    }

    private static String normalizarCodigo(String codigoIso) {
        if (codigoIso == null || codigoIso.isBlank()) {
            throw new IllegalArgumentException("Un pais necesita su codigo ISO");
        }

        String normalizado = codigoIso.trim().toUpperCase(Locale.ROOT);
        if (!CODIGO_ISO.matcher(normalizado).matches()) {
            throw new IllegalArgumentException(
                    "El codigo ISO son tres letras, y se recibio: " + codigoIso);
        }

        return normalizado;
    }

    private static String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Un pais necesita nombre");
        }

        return nombre.trim();
    }
}
