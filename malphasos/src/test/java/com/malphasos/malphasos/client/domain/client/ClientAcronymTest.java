package com.malphasos.malphasos.client.domain.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * La regla de la sigla de un cliente, con los ejemplos que se acordaron con el usuario el 2026-10-05.
 * La lista es la misma que usa la prueba que la compara con su gemela en SQL.
 */
class ClientAcronymTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "Clínica Dermatológica del Norte S.A.S. | CDN",
        "Hospital Universitario San Ignacio     | HUSI",
        "Bolívar Bioingeniería Ltda.            | BBI",
        "Dermacenter S.A.S.                     | DER",
        "Clínica del Dolor y Neurología S.A.    | CDN",
        "Uñas & Spa Ltda                        | USP",
        "IPS 24 Horas                           | I2H",
        "S.A.S.                                 | SAS",
        "X                                      | XCC",
        "Fundación Hospital Infantil de la Sabana de Bogotá Norte | FHISBN"
    })
    @DisplayName("la sigla sale de la razon social: sin forma juridica, sin palabras vacias, con iniciales")
    void base(String razonSocial, String sigla) {
        assertThat(ClientAcronym.base(razonSocial)).isEqualTo(sigla);
    }

    @Test
    @DisplayName("el desempate añade un numero, y si no cabe recorta la base y no el numero")
    void desempate() {
        assertThat(ClientAcronym.conDesempate("CDN", 1)).isEqualTo("CDN");
        assertThat(ClientAcronym.conDesempate("CDN", 2)).isEqualTo("CDN2");
        assertThat(ClientAcronym.conDesempate("FHISBN", 2)).isEqualTo("FHISB2");
        assertThat(ClientAcronym.conDesempate("FHISBN", 12)).isEqualTo("FHIS12");
    }

    @Test
    @DisplayName("una sigla escrita a mano se normaliza a mayusculas y se rechaza si no tiene el formato")
    void validar() {
        assertThat(ClientAcronym.validar(" cdn2 ")).isEqualTo("CDN2");
        assertThatThrownBy(() -> ClientAcronym.validar("CD")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientAcronym.validar("2CDN")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientAcronym.validar("CDNORTE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientAcronym.validar("C-DN")).isInstanceOf(IllegalArgumentException.class);
    }
}
