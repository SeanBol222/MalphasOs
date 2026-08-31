package com.malphasos.malphasos.client.domain.client;

/**
 * Documento con el que se identifica un cliente.
 *
 * <p>En el original era un {@code String} y los valores admitidos vivían solo en la restricción
 * {@code CHK_tipo_identifiacion} de la tabla: nada impedía escribir "nit" o "Pasaporte", y el error
 * aparecía al insertar. Es el mismo tratamiento que recibió {@code tipoPersona}.
 */
public enum IdentificationType {

    /** NIT de persona jurídica. */
    NIT_JURIDICO("NIT_juridico"),

    /** NIT de persona natural. */
    NIT_NATURAL("NIT_natural"),

    /** Cédula de ciudadanía. */
    CC("CC"),

    /** Cédula de extranjería. */
    CE("CE");

    private final String valorEnEsquema;

    IdentificationType(String valorEnEsquema) {
        this.valorEnEsquema = valorEnEsquema;
    }

    /**
     * Valor tal como lo admite la restricción de la tabla.
     *
     * <p>No coincide con el nombre de la constante porque el esquema usa {@code NIT_juridico} en
     * minúsculas. Se conserva el valor del esquema en vez de renombrar la columna, para no
     * inventar una diferencia con los datos que ya existen en el sistema original.
     */
    public String valorEnEsquema() {
        return valorEnEsquema;
    }

    public static IdentificationType desdeEsquema(String valor) {
        for (IdentificationType tipo : values()) {
            if (tipo.valorEnEsquema.equals(valor)) {
                return tipo;
            }
        }

        throw new IllegalArgumentException("Tipo de identificacion desconocido: " + valor);
    }
}
