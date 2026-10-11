package com.malphasos.malphasos.client.application.model;

import java.util.UUID;

/**
 * Dónde está un área de servicio, en palabras: el área, su sede y su cliente.
 *
 * <p>Es lo que un listado de otro módulo necesita para pintar «Clínica del Norte · Sede principal ·
 * Laboratorio» sin conocer las tres tablas ni pedir el agregado de cada una.
 */
public record ServiceAreaLocation(
        UUID idArea,
        String area,
        UUID idSede,
        String sede,
        UUID idCliente,
        String cliente,
        String sigla) {
}
