import { HttpErrorResponse } from '@angular/common/http';
import { detallesDe, traducirError } from './traducir';
import { MENSAJE_DE_RESPALDO } from './catalogo-client';

/** Un fallo con la forma exacta en que el backend devuelve sus errores. */
function fallo(code: string | undefined, details: string[] = [], status = 400): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: { code, message: 'Invalid client data', details, timestamp: '2026-09-13T00:00:00' },
  });
}

describe('Traducir errores del API', () => {
  it.each([
    ['ERR_CLIENT_001', 'Ese cliente no existe o fue retirado.'],
    ['ERR_CLIENT_005', 'Revise los datos del formulario.'],
    ['ERR_CLIENT_006', 'Esa ciudad no existe.'],
  ])('%s se convierte en una frase que una persona lee', (codigo, esperado) => {
    expect(traducirError(fallo(codigo))).toBe(esperado);
  });

  it('distingue "no existe" de "datos invalidos", que es lo que el backend se molesto en separar', () => {
    // Fundirlos en un "ha ocurrido un error" tiraria a la basura esa decision del servidor.
    expect(traducirError(fallo('ERR_CLIENT_001'))).not.toBe(traducirError(fallo('ERR_CLIENT_005')));
  });

  it('prefiere el catalogo al mensaje del servidor, que viene en ingles y es para quien programa', () => {
    expect(traducirError(fallo('ERR_CLIENT_005'))).not.toContain('Invalid');
  });

  it.each([
    ['un codigo desconocido', fallo('ERR_CLIENT_999')],
    ['un fallo sin codigo', fallo(undefined)],
    ['algo que no es una respuesta HTTP', new Error('se cayo la red')],
  ])('%s no deja la pantalla muda', (_caso, entrada) => {
    // Devolver cadena vacia dejaria una pantalla que falla sin explicar nada.
    expect(traducirError(entrada)).toBe(MENSAJE_DE_RESPALDO);
  });

  it('los detalles del backend viajan aparte del mensaje', () => {
    // El mensaje tiene que valer por si solo; el detalle dice que campo fallo.
    expect(detallesDe(fallo('ERR_CLIENT_005', ['El documento es obligatorio']))).toEqual([
      'El documento es obligatorio',
    ]);
    expect(detallesDe(new Error('nada'))).toEqual([]);
  });
});
