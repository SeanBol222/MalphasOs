package com.malphasos.malphasos.client.domain.headquarter;

/**
 * Dirección de una sede, descompuesta como en el esquema.
 *
 * <p>Los tres datos viajan siempre juntos y no tienen identidad propia: son un valor, no una
 * entidad. En el original eran tres campos sueltos de la sede, de modo que nada impedía construir
 * una con calle y sin número.
 */
public record Address(String calle, String carrera, String numero) {

    public Address {
        calle = exigir(calle, "calle");
        carrera = exigir(carrera, "carrera");
        numero = exigir(numero, "numero");
    }

    private static String exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Una direccion necesita " + campo);
        }

        return valor.trim();
    }
}
