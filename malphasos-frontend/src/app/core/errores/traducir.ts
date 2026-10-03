import { HttpErrorResponse } from '@angular/common/http';
import {
  CATALOGO_CLIENT,
  CATALOGO_EQUIPMENT,
  CATALOGO_LOCATION,
  CATALOGO_PERSON,
  CATALOGO_SERVICE_REPORT,
  CATALOGO_WORK_ORDER,
  MENSAJE_DE_RESPALDO,
} from './catalogos';

/** La forma en que todos los modulos del backend devuelven un error. */
interface ErrorDelApi {
  readonly code?: string;
  readonly details?: readonly string[];
}

/**
 * Convierte un fallo de HTTP en una frase.
 *
 * <p>Prioriza el codigo del catalogo sobre el mensaje del servidor a proposito: el mensaje viene en
 * ingles y describe el fallo para quien programa —"Invalid client data"—, no para quien rellena un
 * formulario.
 *
 * <p>Un fallo sin codigo tampoco se queda mudo: puede ser la red, un 500 o un proxy por medio, y en
 * todos esos casos hay que decir algo. Devolver la cadena vacia dejaria una pantalla que falla sin
 * explicar nada.
 */
export function traducirError(fallo: unknown): string {
  if (!(fallo instanceof HttpErrorResponse)) {
    return MENSAJE_DE_RESPALDO;
  }

  const codigo = (fallo.error as ErrorDelApi | null)?.code;

  return (
    (codigo &&
      (CATALOGO_CLIENT[codigo] ??
        CATALOGO_LOCATION[codigo] ??
        CATALOGO_EQUIPMENT[codigo] ??
        CATALOGO_WORK_ORDER[codigo] ??
        CATALOGO_SERVICE_REPORT[codigo] ??
        CATALOGO_PERSON[codigo])) ||
    MENSAJE_DE_RESPALDO
  );
}

/**
 * Los detalles que el backend adjunta, si los hay.
 *
 * <p>Traen la causa concreta —que campo, que valor— y sirven para el bloque de detalle de un
 * formulario. Van aparte del mensaje porque el mensaje tiene que valer por si solo.
 */
export function detallesDe(fallo: unknown): readonly string[] {
  if (!(fallo instanceof HttpErrorResponse)) {
    return [];
  }

  return (fallo.error as ErrorDelApi | null)?.details ?? [];
}
