import { conteoDe, contar } from './conteos';

/**
 * La suma que junta los dos resúmenes del servidor (2026-10-11). Es lógica pura y la usan tres
 * pantallas, así que se prueba aquí y no a través de ellas.
 */
describe('Contar sedes, áreas y equipos', () => {
  const SEDES = [
    {
      id: 's1',
      idCliente: 'c1',
      idCiudad: 'bog',
      abierta: true,
      areas: [
        { id: 'a1', abierta: true },
        { id: 'a2', abierta: false },
      ],
    },
    {
      id: 's2',
      idCliente: 'c1',
      idCiudad: 'bog',
      abierta: true,
      areas: [{ id: 'a3', abierta: true }],
    },
    {
      id: 's3',
      idCliente: 'c1',
      idCiudad: 'tun',
      abierta: false,
      areas: [{ id: 'a4', abierta: true }],
    },
    { id: 's4', idCliente: 'c2', idCiudad: 'chi', abierta: true, areas: [] },
  ];
  // El más reciente va primero a propósito: si fuera el último, «quedarse con el último que llega»
  // acertaría por azar, y una mutación así sobrevivió.
  const EQUIPOS = [
    {
      idArea: 'a3',
      idSede: 's2',
      idCliente: 'c1',
      equipos: 2,
      fueraDeServicio: 0,
      ultimoServicio: '2026-10-06T21:30:00',
    },
    {
      idArea: 'a1',
      idSede: 's1',
      idCliente: 'c1',
      equipos: 3,
      fueraDeServicio: 1,
      ultimoServicio: '2026-09-01T10:00:00',
    },
  ];

  it('por cliente: solo lo abierto, ciudades sin repetir y el servicio más reciente', () => {
    const c1 = conteoDe(contar(SEDES, EQUIPOS, 'idCliente'), 'c1');

    expect(c1).toEqual({
      sedes: 2,
      areas: 2,
      equipos: 5,
      fueraDeServicio: 1,
      ultimoServicio: '2026-10-06T21:30:00',
      idsCiudades: ['bog'],
    });
  });

  it('por sede: cada una con lo suyo', () => {
    const porSede = contar(SEDES, EQUIPOS, 'id');

    expect(conteoDe(porSede, 's1')).toMatchObject({
      sedes: 1,
      areas: 1,
      equipos: 3,
      fueraDeServicio: 1,
    });
    expect(conteoDe(porSede, 's2')).toMatchObject({ areas: 1, equipos: 2 });
  });

  it('lo que no aparece cuenta cero, no falta', () => {
    expect(conteoDe(contar(SEDES, EQUIPOS, 'idCliente'), 'nadie')).toEqual({
      sedes: 0,
      areas: 0,
      equipos: 0,
      fueraDeServicio: 0,
      ultimoServicio: '',
      idsCiudades: [],
    });
    expect(conteoDe(contar(SEDES, EQUIPOS, 'idCliente'), 'c2')).toMatchObject({
      sedes: 1,
      equipos: 0,
    });
  });
});
