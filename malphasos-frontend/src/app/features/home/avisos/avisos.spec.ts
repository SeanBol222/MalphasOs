import { avisosDeOrdenes, avisosDePatrones } from './avisos';

const HOY = new Date(2026, 10, 21, 22, 30);

const patron = (id: string, vence: string, cambios: object = {}) => ({
  id,
  nombre: `Patrón ${id}`,
  certificado: `C-${id}`,
  vencimientoCertificado: vence,
  estadoActivo: true,
  ...cambios,
});

describe('Avisos', () => {
  it('avisa de los patrones vencidos y de los que vencen en 30 días, el más urgente primero', () => {
    const avisos = avisosDePatrones(
      [
        patron('lejos', '2026-12-22'),
        patron('treinta', '2026-12-21'),
        patron('nueve', '2026-11-30'),
        patron('vencido', '2026-11-15'),
      ],
      HOY,
    );

    expect(avisos.map((a) => [a.etiqueta, a.nivel])).toEqual([
      ['Patrón vencido', 'vencido'],
      ['Vence en 9 días', 'pronto'],
      ['Vence en 30 días', 'despues'],
    ]);
    expect(avisos[0]!.detalle).toBe('Venció el 15 nov 2026, hace 6 días');
  });

  it('cuenta los días en la fecha local: a las diez de la noche, mañana sigue siendo mañana', () => {
    const [manana, hoy] = avisosDePatrones(
      [patron('a', '2026-11-22'), patron('b', '2026-11-21')],
      HOY,
    ).sort((x, y) => x.etiqueta.localeCompare(y.etiqueta));

    expect(manana!.etiqueta).toBe('Vence en 1 día');
    expect(hoy!.etiqueta).toBe('Vence hoy');
    expect(hoy!.nivel).toBe('pronto');
  });

  it('un patrón retirado no avisa', () => {
    expect(avisosDePatrones([patron('r', '2026-11-01', { estadoActivo: false })], HOY)).toEqual([]);
  });

  it('el aviso cambia de clave al vencer: lo leído cuando estaba por vencer vuelve a salir', () => {
    const antes = avisosDePatrones([patron('x', '2026-11-25')], new Date(2026, 10, 21))[0]!;
    const despues = avisosDePatrones([patron('x', '2026-11-25')], new Date(2026, 10, 26))[0]!;

    expect(antes.clave).not.toBe(despues.clave);
  });

  it('una orden por aprobar lleva a su ficha y dice de qué cliente es', () => {
    const [aviso] = avisosDeOrdenes(
      [{ id: 'o1', numero: '4F2A9C', idCliente: 'c1', fechaMantenimiento: '2026-11-18' }],
      new Map([['c1', 'Laboratorio Demo']]),
    );

    expect(aviso!.titulo).toBe('Laboratorio Demo · 18 nov 2026');
    expect(aviso!.ruta).toEqual(['/ordenes', 'o1']);
  });

  it('ordena por el día de ejecución, la que más lleva esperando primero', () => {
    // El historial cargado ya no llega: el servidor lo deja fuera por su corte (2026-11-22).
    const avisos = avisosDeOrdenes(
      [
        {
          id: 'tardia',
          fechaMantenimiento: '2026-10-30',
          documento: { fechaEjecucion: '2026-11-08' },
        },
        { id: 'nueva', fechaMantenimiento: '2026-11-05' },
      ],
      new Map(),
    );

    expect(avisos.map((a) => a.clave)).toEqual(['orden:nueva', 'orden:tardia']);
  });
});
