package com.malphasos.malphasos.client.domain.client.events;

import com.malphasos.malphasos.shared.domain.events.Payload;

/** Datos de un cliente que viajan con sus eventos. El identificador va en la metadata. */
public record ClientPayload(String documento, String razonSocial) implements Payload {
}
