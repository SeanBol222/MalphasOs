import { ESCALA_MAXIMA, ESCALA_MINIMA, escalaEntre } from './encuadre-de-foto';

/**
 * Lo que el encuadre decide por su cuenta (2026-11-08). El recorte lo hace ngx-image-cropper con un
 * lienzo, que el entorno de pruebas no tiene: lo que se fija aquí es hasta dónde se acerca y se aleja.
 */
describe('La escala del encuadre', () => {
  it('no se aleja más que la foto entera ni se acerca más de cuatro veces', () => {
    expect(ESCALA_MINIMA).toBe(1);
    expect(ESCALA_MAXIMA).toBe(4);
    expect(escalaEntre(0.5)).toBe(1);
    expect(escalaEntre(9)).toBe(4);
  });

  it('dentro de ese rango, la que se pida, redondeada a centésimas', () => {
    expect(escalaEntre(1.75)).toBe(1.75);
    expect(escalaEntre(1 / 3 + 2)).toBe(2.33);
  });
});
