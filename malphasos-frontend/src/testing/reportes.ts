import { ReporteDeServicio } from '../app/core/api/tipos';

/**
 * Lo que un reporte de servicio necesita alrededor para poder pintarse.
 *
 * <p>Un reporte trae el identificador de su orden y el de su equipo, y nada mas: la serie del equipo
 * sale de la lista de equipos, como en todas las pantallas de este proyecto. Cliente, sede y
 * responsables NO los trae, y eso no es una carencia — se consultan por la orden, que es lo que RF-11
 * llama autocompletar.
 */

const API = 'http://localhost:8081/v1/api';

export const URL_REPORTES = `${API}/reports`;
export const urlReporte = (id: string) => `${URL_REPORTES}/${id}`;
export const urlAbrirReporte = (idOrden: string) => `${URL_REPORTES}/work-orders/${idOrden}`;
export const urlCerrarReporte = (id: string) => `${URL_REPORTES}/${id}/finish`;
export const urlVerificacion = (id: string) => `${URL_REPORTES}/${id}/verification`;

/**
 * Los reportes de una orden.
 *
 * <p>La URL lleva el filtro, que el API exige: pedir {@code /reports} a secas responde 400 porque un
 * reporte no se consulta suelto. Las pruebas lo piden con filtro porque la pantalla lo pide con filtro.
 */
export const urlReportesDeOrden = (idOrden: string) =>
  `${URL_REPORTES}?idOrdenTrabajo=${idOrden}`;

export const urlReportesDeEquipo = (idEquipo: string) =>
  `${URL_REPORTES}?idEquipoCliente=${idEquipo}`;

export const ID_REPORTE = 'r1';

/** Un reporte en el estado que se pida, sobre el equipo que se pida. */
export function reporte(cambios: Partial<ReporteDeServicio> = {}): ReporteDeServicio {
  return {
    id: ID_REPORTE,
    idOrdenTrabajo: 'o1',
    idEquipoCliente: 'ec1',
    estado: 'BORRADOR',
    estadoActivo: true,
    lecturas: [],
    ...cambios,
  };
}

/** Un reporte al que solo le falta cerrarse: tiene los dos campos que el cierre exige. */
export function reporteCerrable(cambios: Partial<ReporteDeServicio> = {}): ReporteDeServicio {
  return reporte({
    procedimientos: 'Limpieza y calibracion',
    resultado: 'OPERATIVO',
    ...cambios,
  });
}
