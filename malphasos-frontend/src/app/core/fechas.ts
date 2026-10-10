import { InjectionToken } from '@angular/core';

/**
 * Fechas de calendario en la zona de quien mira, sin pasar por Greenwich.
 *
 * <p>La hoja de vida ya cayo dos veces en lo mismo: {@code toISOString()} da la fecha de Greenwich, y
 * en Bogota desde las siete de la noche es manana; y construir un {@code Date} desde «2026-10-06» lo
 * interpreta como medianoche de Greenwich, que en Bogota es el dia anterior. Aqui todo se construye
 * con el constructor local y se escribe a mano.
 */

/**
 * El dia de hoy, inyectable para que las pruebas lo fijen. Una agenda que depende del reloj sin poder
 * fijarlo solo se puede probar el dia en que se escribio la prueba.
 */
export const HOY = new InjectionToken<() => Date>('HOY', {
  providedIn: 'root',
  factory: () => () => new Date(),
});

/** «2026-10-06», en la zona local. */
export function fechaIso(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');

  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

/** El dia de «2026-10-06», a medianoche local y no de Greenwich. */
export function desdeIso(iso: string): Date {
  const [anio, mes, dia] = iso.split('-').map(Number);

  return new Date(anio!, mes! - 1, dia!);
}

export function sumarDias(fecha: Date, dias: number): Date {
  return new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate() + dias);
}

/** El lunes de la semana de esa fecha: en Colombia la semana empieza en lunes. */
export function lunesDe(fecha: Date): Date {
  // getDay() da 0 para el domingo: se lleva al final de la semana y no al principio.
  return sumarDias(fecha, -((fecha.getDay() + 6) % 7));
}
