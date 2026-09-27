/**
 * Lo que se ha elegido antes en este navegador, por campo.
 *
 * <p><b>Es local a la maquina y no del servidor</b>, por decision del usuario el 2026-09-26. La
 * alternativa era derivarlo de los datos —paises con algun cliente, ciudades con alguna sede—, que se
 * comparte entre companeros pero cuesta consultas: hoy no hay forma de pedir todas las sedes de una
 * vez. Esto es instantaneo, empieza vacio y se pierde al limpiar el navegador, y esas tres cosas son
 * aceptables para una lista de sugerencias.
 *
 * <p><b>No es un dato del que dependa nada.</b> Si el almacenamiento falla o viene vacio, el campo
 * sigue funcionando: se escribe y se busca en el catalogo completo. Por eso cada acceso va envuelto:
 * en una ventana privada, con las cookies bloqueadas o con el almacenamiento lleno, leer o escribir
 * lanza, y una excepcion ahi no puede tumbar un formulario.
 */

/** Cuantas sugerencias se guardan por campo. Mas de ocho no caben en pantalla sin desplazar. */
const MAXIMO = 8;

const PREFIJO = 'malphasos.historial.';

/** Los identificadores elegidos antes en este campo, del mas reciente al mas antiguo. */
export function historialDe(campo: string): readonly string[] {
  try {
    const guardado = localStorage.getItem(PREFIJO + campo);
    const leido: unknown = guardado ? JSON.parse(guardado) : [];

    return Array.isArray(leido) ? leido.filter((id) => typeof id === 'string') : [];
  } catch {
    return [];
  }
}

/** Anota un identificador como el ultimo usado, sin repetirlo y sin pasar del maximo. */
export function anotarEnHistorial(campo: string, id: string): void {
  if (!id) {
    return;
  }

  try {
    const sinRepetir = [id, ...historialDe(campo).filter((anterior) => anterior !== id)];

    localStorage.setItem(PREFIJO + campo, JSON.stringify(sinRepetir.slice(0, MAXIMO)));
  } catch {
    // Que no se pueda recordar la eleccion no es motivo para romper el formulario.
  }
}
