import { algunPatronVencido, patronVencido, verificacionesSinPatron } from './tipos';

describe('La vigencia de un patrón (2026-10-14)', () => {
  const patron = (vencimiento: string) => ({
    id: 'p',
    nombre: 'Pesas',
    serie: 'S',
    certificado: 'C',
    vencimiento,
  });

  it('el último día todavía vale: vence al terminar, como en el servidor', () => {
    expect(patronVencido(patron('2026-10-14'), '2026-10-14')).toBe(false);
  });

  it('al día siguiente ya no', () => {
    expect(patronVencido(patron('2026-10-14'), '2026-10-15')).toBe(true);
  });

  it('sin patrón no hay nada vencido: que falte es otra regla', () => {
    expect(patronVencido(null, '2026-10-15')).toBe(false);
  });
});

describe('Los patrones de un reporte al cerrar (V30, 2026-10-16)', () => {
  const lectura = (idVerificacion: string) => ({ idVerificacion, secuencia: 1 });
  const usado = (idVerificacion: string, vencimiento = '2099-01-01') => ({
    idVerificacion,
    id: 'p',
    nombre: 'Pesas',
    serie: 'S',
    certificado: 'C',
    vencimiento,
  });

  it('cada verificación medida necesita el suyo, y una medida dos veces se cuenta una', () => {
    const reporte = {
      lecturas: [lectura('temperatura'), lectura('temperatura'), lectura('humedad')],
      patrones: [usado('temperatura')],
    };

    expect(verificacionesSinPatron(reporte)).toEqual(['humedad']);
  });

  it('un patrón en una verificación sin lecturas no hace falta, ni sobra', () => {
    expect(verificacionesSinPatron({ lecturas: [], patrones: [usado('humedad')] })).toEqual([]);
    expect(verificacionesSinPatron(undefined)).toEqual([]);
  });

  it('basta uno vencido para que no se cierre, sea de la verificación que sea', () => {
    const reporte = { patrones: [usado('temperatura'), usado('humedad', '2026-10-15')] };

    expect(algunPatronVencido(reporte, '2026-10-15')).toBe(false);
    expect(algunPatronVencido(reporte, '2026-10-16')).toBe(true);
  });
});
