package com.malphasos.malphasos.report.application.model;

/**
 * La línea del supervisor en un documento impreso, ya escrita (V54, 2026-11-02): la del informe, la del
 * resumen y la del cronograma de la orden son la misma.
 *
 * @param nombre quien aprobó
 * @param detalle «Supervisor responsable · Ingeniero biomédico · Aprobó el 2 nov 2026»
 * @param firma su firma como data: URI, o nula
 */
public record ApprovalLine(String nombre, String detalle, String firma) {
}
