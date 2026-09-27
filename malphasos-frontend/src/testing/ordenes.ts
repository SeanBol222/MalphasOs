/**
 * Lo que una orden de trabajo necesita alrededor para poder pintarse.
 *
 * <p>Una fila de orden trae identificadores y nada mas: el nombre del cliente sale de su lista, el de la
 * sede de una consulta por sede, el del ingeniero de la lista de personas y la serie de cada equipo de la
 * lista de equipos. Cuatro cruces para una pantalla, que es la ausencia ya anotada tres veces: las
 * respuestas no traen nombres.
 */

const API = 'http://localhost:8081/v1/api';

export const URL_ORDENES = `${API}/work-orders`;
export const URL_CLIENTES = `${API}/clients`;
export const URL_PERSONAS = `${API}/persons`;
export const URL_EQUIPOS_DE_CLIENTE = `${API}/client-equipments`;
export const urlSede = (id: string) => `${API}/headquarters/${id}`;
export const urlAreasDeSede = (id: string) => `${API}/headquarters/${id}/service-areas`;
export const urlArea = (id: string) => `${API}/service-areas/${id}`;
export const urlEquiposDeArea = (id: string) => `${API}/service-areas/${id}/equipments`;

export const ID_ORDEN = 'o1';
export const ID_CLIENTE = 'c1';
export const ID_SEDE = 's1';
export const ID_AREA = 'a1';
export const ID_EQUIPO = 'ec1';
export const ID_INGENIERO = 'p1';

export const CLIENTES = [
  { id: ID_CLIENTE, razonSocial: 'Hospital Central', estadoActivo: true },
];

export const SEDE = { id: ID_SEDE, nombre: 'Sede Norte', idCliente: ID_CLIENTE, estadoActivo: true };

export const AREAS = [
  { id: ID_AREA, nombre: 'Urgencias', idSede: ID_SEDE, estadoActivo: true },
  { id: 'a2', nombre: 'Bodega cerrada', idSede: ID_SEDE, estadoActivo: false },
];

export const EQUIPOS = [
  {
    id: ID_EQUIPO,
    serie: 'SN-0001',
    numeroInventario: 'INV-77',
    idModelo: 'mo1',
    idAreaServicio: ID_AREA,
    estadoActivo: true,
  },
  {
    id: 'ec2',
    serie: 'SN-0002',
    idModelo: 'mo1',
    idAreaServicio: ID_AREA,
    estadoActivo: true,
  },
];

export const PERSONAS = [
  {
    identificador: ID_INGENIERO,
    primerNombre: 'Ana',
    primerApellido: 'Ruiz',
    cedula: '123',
    tipoPersona: 'ENGINEER',
    estadoActivo: true,
  },
  {
    identificador: 'p2',
    primerNombre: 'Luis',
    primerApellido: 'Peña',
    cedula: '456',
    // Un encargado de sede no ejecuta mantenimientos: no debe aparecer entre los ingenieros.
    tipoPersona: 'MANAGER',
    estadoActivo: true,
  },
];

/** Una orden en el estado que se pida, con el alcance que se pida. */
export function orden(
  cambios: Partial<{
    id: string;
    estadoEjecucion: string;
    estadoActivo: boolean;
    idIngeniero: string | undefined;
    equipos: { idEquipoCliente: string; idAreaServicio: string }[];
  }> = {},
) {
  return {
    id: ID_ORDEN,
    fechaMantenimiento: '2026-10-15',
    idCliente: ID_CLIENTE,
    idSede: ID_SEDE,
    tipoServicio: 'PREVENTIVO',
    periodicidad: 'TRIMESTRAL',
    estadoEjecucion: 'CREADA',
    estadoActivo: true,
    equipos: [],
    ...cambios,
  };
}
