import { EquiposDeArea, ResumenDeSede } from '../../core/api/tipos';

/** Lo que se cuenta de un cliente o de una sede en los listados del rediseño (2026-10-11). */
export interface Conteo {
  readonly sedes: number;
  readonly areas: number;
  readonly equipos: number;
  readonly fueraDeServicio: number;
  /** El más reciente de los últimos servicios, en ISO; vacío si ninguno tuvo. */
  readonly ultimoServicio: string;
  readonly idsCiudades: readonly string[];
}

const VACIO: Conteo = {
  sedes: 0,
  areas: 0,
  equipos: 0,
  fueraDeServicio: 0,
  ultimoServicio: '',
  idsCiudades: [],
};

/**
 * Suma por la clave que se le pida —el cliente o la sede— lo que dicen los dos resúmenes del servidor:
 * el de sedes, que es de {@code client}, y el de equipos por área, que es de {@code equipment}. Se
 * juntan aquí porque el backend no puede: {@code client} no conoce a {@code equipment}.
 *
 * <p>Solo cuenta lo abierto: una sede cerrada no es una sede del cliente hoy, y sus áreas tampoco. Los
 * equipos sí se cuentan donde estén, porque el resumen ya trae solo los que están en servicio.
 */
export function contar(
  sedes: readonly ResumenDeSede[],
  equipos: readonly EquiposDeArea[],
  clave: 'idCliente' | 'id',
): ReadonlyMap<string, Conteo> {
  const conteos = new Map<string, Conteo>();
  const sumar = (id: string | undefined, cambio: (actual: Conteo) => Conteo) => {
    if (id) {
      conteos.set(id, cambio(conteos.get(id) ?? VACIO));
    }
  };

  for (const sede of sedes) {
    if (!sede.abierta) {
      continue;
    }
    sumar(sede[clave], (actual) => ({
      ...actual,
      sedes: actual.sedes + 1,
      areas: actual.areas + (sede.areas ?? []).filter((area) => area.abierta).length,
      idsCiudades:
        sede.idCiudad && !actual.idsCiudades.includes(sede.idCiudad)
          ? [...actual.idsCiudades, sede.idCiudad]
          : actual.idsCiudades,
    }));
  }

  for (const area of equipos) {
    sumar(clave === 'id' ? area.idSede : area.idCliente, (actual) => ({
      ...actual,
      equipos: actual.equipos + (area.equipos ?? 0),
      fueraDeServicio: actual.fueraDeServicio + (area.fueraDeServicio ?? 0),
      ultimoServicio:
        area.ultimoServicio && area.ultimoServicio > actual.ultimoServicio
          ? area.ultimoServicio
          : actual.ultimoServicio,
    }));
  }

  return conteos;
}

export function conteoDe(conteos: ReadonlyMap<string, Conteo>, id: string | undefined): Conteo {
  return (id && conteos.get(id)) || VACIO;
}
