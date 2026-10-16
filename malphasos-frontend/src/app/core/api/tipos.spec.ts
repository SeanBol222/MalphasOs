import { patronVencido } from './tipos';

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
