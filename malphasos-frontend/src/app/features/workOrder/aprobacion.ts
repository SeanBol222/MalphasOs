import { OrdenDeTrabajo } from '../../core/api/tipos';

/**
 * Desde cuándo una orden sin aprobar es algo pendiente: el día que entró la aprobación (V54). Las
 * ejecutadas antes —el historial cargado de otro sistema, cientos— nunca pudieron aprobarse, y contarlas
 * llenaba la campanita con 1158 avisos y el filtro «Por aprobar» con 1153 órdenes que nadie va a resolver
 * (2026-11-21 y 22, decidido por el usuario). Es la misma regla en los dos sitios, por eso vive aquí.
 */
export const DESDE_LA_APROBACION = '2026-11-02';

/** El día en que se ejecutó, o el programado si la orden se congeló tarde y no lo sabe. */
export function ejecutadaEl(orden: OrdenDeTrabajo): string {
  return orden.documento?.fechaEjecucion ?? orden.fechaMantenimiento ?? '';
}

/**
 * Si una orden de la lista que da el servidor como pendiente de aprobar lo está de verdad: ejecutada
 * desde que existe la aprobación.
 */
export function esperaAprobacion(orden: OrdenDeTrabajo): boolean {
  return ejecutadaEl(orden) >= DESDE_LA_APROBACION;
}
