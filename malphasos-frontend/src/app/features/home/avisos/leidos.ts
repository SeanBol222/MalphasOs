/**
 * Qué avisos ha leído quien usa este navegador (2026-11-21).
 *
 * <p><b>Es local a la máquina, por decisión del usuario</b>: no pide tabla ni migración, y lo que se
 * pierde —que en otro aparato vuelva a salir sin leer— es poco, porque un aviso deja de existir en
 * cuanto se resuelve lo que lo causa. Como el historial del buscador, cada acceso va envuelto: en una
 * ventana privada o con el almacenamiento bloqueado leer o escribir lanza, y la campanita no puede
 * caerse por no poder recordar.
 *
 * <p>Se guarda por sesión de usuario, porque dos personas pueden compartir un computador de la oficina.
 */

const PREFIJO = 'malphasos.avisos-leidos.';

export function leidosDe(usuario: string): ReadonlySet<string> {
  try {
    const guardado = localStorage.getItem(PREFIJO + usuario);
    const leido: unknown = guardado ? JSON.parse(guardado) : [];

    return new Set(Array.isArray(leido) ? leido.filter((c) => typeof c === 'string') : []);
  } catch {
    return new Set();
  }
}

/**
 * Guarda lo leído, recortado a los avisos que siguen existiendo: un patrón renovado o una orden aprobada
 * no tienen por qué quedarse para siempre en el navegador.
 */
export function guardarLeidos(
  usuario: string,
  leidos: ReadonlySet<string>,
  vigentes: readonly string[],
): void {
  try {
    const conservar = vigentes.filter((clave) => leidos.has(clave));

    localStorage.setItem(PREFIJO + usuario, JSON.stringify(conservar));
  } catch {
    // Que no se pueda recordar no es motivo para romper la campanita.
  }
}
