package com.malphasos.malphasos.equipment.domain.brand.events;

import com.malphasos.malphasos.shared.domain.events.Payload;

/** Datos de una marca que viajan con sus eventos. */
public record BrandPayload(String nombre) implements Payload {
}
