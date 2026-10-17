import { conSigno, graficaDeError, LIENZO } from './grafica-de-error';

/** La gráfica de error con banda de tolerancia de la página 2 (2026-10-16). */
describe('La gráfica de error', () => {
  it('la banda es simétrica alrededor del cero y no toca los bordes', () => {
    const { banda, ceroY } = graficaDeError([{ error: 0.2, conforme: true }], 0.5);

    expect(banda).not.toBeNull();
    expect(ceroY - banda!.y).toBeCloseTo(banda!.y + banda!.alto - ceroY);
    expect(banda!.y).toBeGreaterThan(LIENZO.arriba);
    expect(banda!.y + banda!.alto).toBeLessThan(LIENZO.abajo);
  });

  it('una lectura fuera de tolerancia cae fuera de la banda, y una dentro, dentro', () => {
    const { banda, puntos } = graficaDeError(
      [
        { error: 0.9, conforme: false },
        { error: -0.3, conforme: true },
      ],
      0.5,
    );

    expect(puntos[0].y).toBeLessThan(banda!.y);
    expect(puntos[1].y).toBeGreaterThan(banda!.y);
    expect(puntos[1].y).toBeLessThan(banda!.y + banda!.alto);
    expect(puntos.map((punto) => punto.conforme)).toEqual([false, true]);
  });

  it('el borde de la tolerancia cae justo en el borde de la banda', () => {
    const { banda, puntos } = graficaDeError([{ error: 0.5, conforme: true }], 0.5);

    expect(puntos[0].y).toBeCloseTo(banda!.y);
  });

  it('las lecturas se reparten a lo ancho, en su orden', () => {
    const { puntos } = graficaDeError(
      [0.1, 0.2, 0.3, 0.4].map((error) => ({ error, conforme: true })),
      1,
    );

    expect(puntos.map((punto) => punto.x)).toEqual([86, 162, 238, 314]);
  });

  it('las marcas dicen la tolerancia con su signo, y el cero', () => {
    expect(graficaDeError([], 0.5).marcas.map((marca) => marca.texto)).toEqual([
      '+0,5',
      '0',
      '−0,5',
    ]);
  });

  it('sin tolerancia no hay banda, y la escala la da el mayor error', () => {
    const grafica = graficaDeError([{ error: -2, conforme: null }], null);

    expect(grafica.banda).toBeNull();
    expect(grafica.marcas.map((marca) => marca.texto)).toEqual(['+2', '0', '−2']);
    expect(grafica.puntos[0].y).toBeLessThan(LIENZO.abajo);
  });

  it('sin tolerancia ni error, una sola marca: el cero', () => {
    expect(graficaDeError([{ error: 0, conforme: null }], null).marcas.map((m) => m.texto)).toEqual(
      ['0'],
    );
  });

  it('el signo menos es el de verdad, no un guion', () => {
    expect(conSigno(-0.3)).toBe('−0,3');
    expect(conSigno(0.25)).toBe('+0,25');
    expect(conSigno(0)).toBe('0');
  });
});
