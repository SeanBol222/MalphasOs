package com.malphasos.malphasos.report.application.model;

import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import java.time.LocalDate;

/**
 * La aprobación vigente de la orden, ya resuelta para imprimirse (V54, 2026-11-02): quién la aprobó, con
 * qué cargo supervisa al cliente, su firma y el día.
 *
 * <p><b>No se congela</b>, como la firma de quien recibe: llega después de cerrar los reportes y de
 * ejecutar la orden, y se lee de {@code aprobacion_orden} cada vez que se compone un documento. Sustituir un
 * reporte la retira, y entonces el documento vuelve a dejar la línea para firmar.
 *
 * @param cargo nulo si el supervisor no lo tiene
 * @param firma nula si su imagen no está en el almacén
 */
public record OrderApproval(String nombre, String cargo, DocumentImage firma, LocalDate fecha) {
}
