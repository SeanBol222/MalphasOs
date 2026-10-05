package com.malphasos.malphasos.client.application.services.client.commands;

import java.util.UUID;

/** Corrige la sigla de un cliente, la que encabeza el número de sus hojas de vida. */
public record ChangeClientAcronymCommand(UUID id, String sigla) {
}
