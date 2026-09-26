import { ComponentFixture } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

/**
 * Lo que toda prueba de pantalla necesita para lidiar con consultas asincronas.
 *
 * <p><b>Ninguno de estos ayudantes usa {@code whenStable()}, y es la razon de que existan.</b> La
 * aplicacion es zoneless y una peticion HTTP sin responder cuenta como tarea pendiente: esperar la
 * estabilidad mientras hay una en vuelo no termina nunca y la prueba agota su tiempo en vez de
 * fallar diciendo algo. Se descubrio escribiendo la ficha del cliente, y estaba a punto de copiarse
 * en cada pantalla nueva.
 */

/** Cuantas vueltas se dan antes de rendirse. Veinte tics vacios son de sobra y no bloquean nada. */
const INTENTOS = 20;

/**
 * Deja pasar tics hasta que las senales de las consultas lleguen al DOM.
 *
 * <p>Se espera por tics y no por un numero de milisegundos: un retardo fijo seria una carrera, y
 * ademas lenta.
 */
export async function asentar(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let intento = 0; intento < INTENTOS; intento += 1) {
    fixture.detectChanges();
    await tic();
  }

  fixture.detectChanges();
}

/**
 * Espera a que la pantalla pida {@code url} y responde.
 *
 * <p>Se espera a que la peticion aparezca en vez de darla por hecha: una consulta sale en un
 * microtic posterior a la construccion del componente, y {@code expectOne} inmediato fallaria por
 * carrera y no por defecto.
 */
export async function responderA(
  fixture: ComponentFixture<unknown>,
  http: HttpTestingController,
  url: string,
  cuerpo: object | null,
  opciones?: { status: number; statusText: string },
): Promise<void> {
  for (let intento = 0; intento < INTENTOS; intento += 1) {
    fixture.detectChanges();
    const pendientes = http.match(url);

    if (pendientes.length) {
      if (opciones) {
        pendientes[0].flush(cuerpo, opciones);
      } else {
        pendientes[0].flush(cuerpo);
      }
      await asentar(fixture);

      return;
    }

    await tic();
  }

  throw new Error(`La pantalla no pidio ${url}`);
}

/**
 * Responde a la consulta que una escritura vuelve a disparar al invalidar la cache.
 *
 * <p>Sin esto, {@code http.verify()} del final falla por una peticion abierta y el fallo se lee como
 * si el defecto estuviera en la prueba siguiente.
 */
export async function atenderRefresco(
  fixture: ComponentFixture<unknown>,
  http: HttpTestingController,
  url: string,
  cuerpo: object | null,
): Promise<void> {
  http.match(url).forEach((peticion) => peticion.flush(cuerpo));
  await asentar(fixture);
}

function tic(): Promise<void> {
  return new Promise((seguir) => setTimeout(seguir, 0));
}
