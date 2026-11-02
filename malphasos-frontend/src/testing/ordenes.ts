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

// Supervisado por p2, el supervisor del cliente (V53): no ejecuta mantenimientos, y es quien aprueba. Su
// nombre viene con el cliente (2026-11-02), como lo manda el servidor.
export const CLIENTES = [
  {
    id: ID_CLIENTE,
    razonSocial: 'Hospital Central',
    estadoActivo: true,
    supervisores: [{ idPersona: 'p2', nombre: 'Luis Peña', cargo: 'Ingeniero biomédico' }],
  },
];

/** Lo que el servidor pone al lado de cada identificador de persona (2026-11-02). */
const NOMBRES: Readonly<Record<string, string>> = { [ID_INGENIERO]: 'Ana Ruiz', p2: 'Luis Peña' };

export const SEDE = {
  id: ID_SEDE,
  nombre: 'Sede Norte',
  idCliente: ID_CLIENTE,
  estadoActivo: true,
};

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
    // Un supervisor del cliente no ejecuta mantenimientos: no debe aparecer entre los ingenieros.
    tipoPersona: 'SUPERVISOR',
    estadoActivo: true,
  },
];

/** Una orden en el estado que se pida, con el alcance que se pida. */
export function orden(
  cambios: Partial<{
    id: string;
    fechaMantenimiento: string;
    estadoEjecucion: string;
    estadoActivo: boolean;
    tipoServicio: string;
    garantia: boolean;
    idIngeniero: string | undefined;
    equipos: { idEquipoCliente: string; idReporteServicio: string }[];
  }> = {},
) {
  return {
    id: ID_ORDEN,
    numero: '4F2A9C',
    garantia: false,
    fechaMantenimiento: '2026-10-15',
    idCliente: ID_CLIENTE,
    idSede: ID_SEDE,
    tipoServicio: 'PREVENTIVO',
    periodicidad: 'TRIMESTRAL',
    estadoEjecucion: 'CREADA',
    estadoActivo: true,
    equipos: [],
    ...cambios,
    // Con la orden viene el nombre de su ingeniero, como lo manda el servidor.
    nombreIngeniero: cambios.idIngeniero ? NOMBRES[cambios.idIngeniero] : undefined,
  };
}
