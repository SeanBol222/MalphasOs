import { Patron, OrdenDeTrabajo } from '../../../core/api/tipos';
import { desdeIso, fechaCorta } from '../../../core/fechas';
import { ejecutadaEl, esperaAprobacion } from '../../workOrder/aprobacion';

/**
 * Los avisos de la campanita (2026-11-21, propuesta 2 de tres, elegida por el usuario sobre un
 * prototipo interactivo): lo que pide atención sin que nadie lo busque.
 *
 * <p><b>No hay tabla de avisos.</b> Cada uno se deduce de un dato que ya existe —el vencimiento del
 * certificado de un patrón, una orden ejecutada sin aprobar—, así que desaparece solo cuando se resuelve
 * lo que lo causa: renovar el certificado o aprobar la orden. Lo único que se guarda es qué ha leído
 * cada quien, y en su navegador: ver {@link ./leidos}.
 */

/** Cuántos días antes del vencimiento empieza a avisar un patrón. Decidido por el usuario. */
export const DIAS_DE_ANTICIPACION = 30;

/** Hasta aquí un vencimiento es urgente y se dice en rojo; de aquí a los 30, en gris. */
export const DIAS_URGENTES = 15;

/**
 * Qué tan grave es: la marca lo distingue por forma y no solo por color —lleno, hueco, gris, tinta—,
 * porque el manual de marca no admite colores nuevos para los estados.
 */
export type NivelDeAviso = 'vencido' | 'pronto' | 'despues' | 'orden';

export type TipoDeAviso = 'patron' | 'orden';

export interface Aviso {
  /**
   * Lo que se recuerda como leído. Lleva el nivel y la fecha del patrón: si vence o se renueva con otra
   * fecha, es otro aviso, y vuelve a salir sin leer.
   */
  readonly clave: string;
  readonly tipo: TipoDeAviso;
  readonly nivel: NivelDeAviso;
  readonly etiqueta: string;
  readonly titulo: string;
  readonly detalle: string;
  /** Adónde lleva al tocarlo. */
  readonly ruta: readonly string[];
}

const DIA = 24 * 60 * 60 * 1000;

/** Días enteros de hoy a esa fecha: negativo si ya pasó. Con medianoche local, sin Greenwich. */
function diasHasta(iso: string, hoy: Date): number {
  const inicio = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate());

  return Math.round((desdeIso(iso).getTime() - inicio.getTime()) / DIA);
}

function enDias(dias: number): string {
  return dias === 1 ? '1 día' : `${dias} días`;
}

/** Los patrones vigentes que vencen en los próximos treinta días o ya vencieron, el más urgente primero. */
export function avisosDePatrones(patrones: readonly Patron[], hoy: Date): Aviso[] {
  return patrones
    .filter((patron) => patron.estadoActivo !== false && !!patron.vencimientoCertificado)
    .map((patron) => ({ patron, dias: diasHasta(patron.vencimientoCertificado!, hoy) }))
    .filter(({ dias }) => dias <= DIAS_DE_ANTICIPACION)
    .sort((a, b) => a.dias - b.dias)
    .map(({ patron, dias }): Aviso => {
      const nivel: NivelDeAviso =
        dias < 0 ? 'vencido' : dias <= DIAS_URGENTES ? 'pronto' : 'despues';
      const fecha = fechaCorta(patron.vencimientoCertificado);
      const nombre = [patron.nombre, patron.codigoInterno].filter(Boolean).join(' ');

      return {
        clave: `patron:${patron.id}:${nivel}:${patron.vencimientoCertificado}`,
        tipo: 'patron',
        nivel,
        etiqueta:
          dias < 0 ? 'Patrón vencido' : dias === 0 ? 'Vence hoy' : `Vence en ${enDias(dias)}`,
        titulo: nombre || 'Patrón sin nombre',
        detalle:
          dias < 0
            ? `Venció el ${fecha}, hace ${enDias(-dias)}`
            : patron.certificado
              ? `Certificado ${patron.certificado} · ${fecha}`
              : fecha,
        ruta: ['/catalogo', 'patrones'],
      };
    });
}

/**
 * Las órdenes ejecutadas desde que existe la aprobación y que la esperan, la más antigua primero: es la
 * que más lleva esperando.
 */
export function avisosDeOrdenes(
  ordenes: readonly OrdenDeTrabajo[],
  clientes: ReadonlyMap<string, string>,
): Aviso[] {
  return ordenes
    .filter(esperaAprobacion)
    .sort((a, b) => ejecutadaEl(a).localeCompare(ejecutadaEl(b)))
    .map((orden): Aviso => ({
      clave: `orden:${orden.id}`,
      tipo: 'orden',
      nivel: 'orden',
      etiqueta: 'Orden por aprobar',
      titulo: `${clientes.get(orden.idCliente ?? '') || `Orden ${orden.numero ?? ''}`.trim()} · ${fechaCorta(orden.fechaMantenimiento)}`,
      detalle: `Orden ${orden.numero ?? ''} · espera la aprobación`,
      ruta: ['/ordenes', orden.id ?? ''],
    }));
}
