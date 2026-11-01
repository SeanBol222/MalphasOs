package com.malphasos.malphasos.workorder.application.services.approval.commands;

import java.util.UUID;

/**
 * Aprobación en sitio: el supervisor dibuja su firma en la sesión de otro.
 *
 * @param firma los bytes del PNG que dibujó
 * @param idOperador quien tiene la sesión, por el {@code sub} de su token; nunca del cuerpo
 */
public record ApproveOnSiteCommand(UUID idOrden, UUID idSupervisor, byte[] firma, UUID idOperador) {
}
