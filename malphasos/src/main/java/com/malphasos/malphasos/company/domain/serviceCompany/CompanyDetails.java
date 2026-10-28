package com.malphasos.malphasos.company.domain.serviceCompany;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que se escribe de la empresa: sus datos de contacto y los textos de sus documentos (2026-10-17).
 * Solo el nombre es obligatorio; lo que falte, el documento no lo imprime.
 *
 * <p>Se recorta todo, y lo que queda en blanco es que no hay: un NIT de espacios no es un NIT.
 *
 * @param idCiudad la ciudad, del catálogo (V44); nula si no se ha dicho. Que exista lo comprueba el
 *     servicio, que es quien conoce el catálogo
 * @param telefonos fijos; nunca nula. Sin los que estén en blanco, en el orden en que llegan
 */
public record CompanyDetails(
        String nombre,
        String nit,
        String direccion,
        UUID idCiudad,
        List<String> telefonos,
        String movil,
        String correo,
        String garantia,
        String aclaracion,
        String codigoHojaDeVida,
        String codigoInforme) {

    static final int LARGO_NOMBRE = 160;
    static final int LARGO_NIT = 30;
    static final int LARGO_DIRECCION = 200;
    static final int LARGO_TELEFONO = 30;
    static final int MAXIMO_TELEFONOS = 5;
    static final int LARGO_CORREO = 120;
    static final int LARGO_TEXTO = 2000;
    static final int LARGO_CODIGO = 40;

    public CompanyDetails {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("La empresa necesita un nombre: es lo que encabeza sus documentos");
        }
        nombre = acotado(nombre.trim(), "nombre", LARGO_NOMBRE);
        nit = opcional(nit, "NIT", LARGO_NIT);
        direccion = opcional(direccion, "dirección", LARGO_DIRECCION);
        telefonos = telefonos == null ? List.of() : telefonos.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(telefono -> !telefono.isEmpty())
                .map(telefono -> acotado(telefono, "teléfono", LARGO_TELEFONO))
                .toList();
        if (telefonos.size() > MAXIMO_TELEFONOS) {
            throw new IllegalArgumentException(
                    "La empresa tiene como mucho " + MAXIMO_TELEFONOS + " teléfonos fijos");
        }
        movil = opcional(movil, "móvil", LARGO_TELEFONO);
        correo = opcional(correo, "correo", LARGO_CORREO);
        garantia = opcional(garantia, "garantía", LARGO_TEXTO);
        aclaracion = opcional(aclaracion, "aclaración", LARGO_TEXTO);
        codigoHojaDeVida = opcional(codigoHojaDeVida, "código de la hoja de vida", LARGO_CODIGO);
        codigoInforme = opcional(codigoInforme, "código del informe", LARGO_CODIGO);
    }

    private static String opcional(String valor, String campo, int largo) {
        return valor == null || valor.isBlank() ? null : acotado(valor.trim(), campo, largo);
    }

    private static String acotado(String valor, String campo, int largo) {
        if (valor.length() > largo) {
            throw new IllegalArgumentException(
                    "El campo " + campo + " de la empresa tiene como mucho " + largo + " caracteres");
        }

        return valor;
    }
}
