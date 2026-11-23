import { OrdenDeTrabajo } from '../../core/api/tipos';

/** El día en que se ejecutó, o el programado si la orden se congeló tarde y no lo sabe. */
export function ejecutadaEl(orden: OrdenDeTrabajo): string {
  return orden.documento?.fechaEjecucion ?? orden.fechaMantenimiento ?? '';
}

/**
 * Si una orden ejecutada espera aprobación, dado el día desde el que se aprueba en esta instalación.
 *
 * <p>El corte lo decide el servidor —`app.approval.since`, ver `ApprovalStart`— y ya no lo lleva la
 * pantalla (2026-11-22): la fecha fija del 2026-11-02 servía solo para la base de desarrollo, y en un
 * servidor nuevo el historial cargado de otro sistema habría salido «por aprobar». Las pendientes que
 * da el servidor ya vienen cortadas; esto lo usa el listado para no decir «Aprobada» de una orden del
 * historial, que no lo está. Sin corte, toda ejecutada espera.
 */
export function esperaAprobacion(orden: OrdenDeTrabajo, desde: string | null | undefined): boolean {
  return !desde || ejecutadaEl(orden) >= desde;
}
