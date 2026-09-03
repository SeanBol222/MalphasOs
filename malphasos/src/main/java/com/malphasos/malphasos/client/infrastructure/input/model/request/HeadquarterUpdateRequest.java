package com.malphasos.malphasos.client.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Cambio sobre una sede. Un campo ausente deja el valor como está.
 *
 * <p>La dirección va entera o no va: sus tres partes forman un valor único, y cambiar solo la calle
 * dejaría una dirección a medias.
 */
@Schema(name = "HeadquarterUpdateRequest")
public record HeadquarterUpdateRequest(
        String nombre, String calle, String carrera, String numero, UUID idCiudad) {
}
