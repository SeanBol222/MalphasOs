package com.malphasos.malphasos.equipment.application.model;

import com.malphasos.malphasos.equipment.domain.protocol.Protocol;

/**
 * Un protocolo de un tipo de equipo con todo lo que hace falta para pintarlo: el protocolo entero, su
 * prioridad en ese tipo y la indicación propia del tipo.
 *
 * <p>Existe porque la asignación solo guarda identificadores, y quien la lee —la pantalla del tipo, la
 * hoja de vida, el reporte que la copia— necesita el nombre y la descripción.
 */
public record AssignedProtocolDetail(Protocol protocolo, int prioridad, String indicacion) {
}
