import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import { ListaClientes } from './lista-clientes';

const API = 'http://localhost:8081/v1/api';
const URL = `${API}/clients`;
const URL_RESUMEN_SEDES = `${API}/headquarters/summary`;
const URL_CONTEOS = `${API}/client-equipments/summary`;
const URL_CIUDADES = `${API}/cities`;

const CLIENTES = [
  {
    id: 'c2',
    razonSocial: 'Hospital San Rafael',
    sigla: 'HSR',
    documento: '860015888',
    tipoIdentificacion: 'NIT_JURIDICO',
    estadoActivo: true,
  },
  {
    id: 'c1',
    razonSocial: 'Clínica del Norte',
    sigla: 'CDN',
    documento: '900123456',
    tipoIdentificacion: 'NIT_JURIDICO',
    estadoActivo: true,
  },
  {
    id: 'c3',
    razonSocial: 'Consultorio Viejo',
    sigla: 'COV',
    documento: '79845123',
    tipoIdentificacion: 'CC',
    estadoActivo: false,
  },
];

const RESUMEN_SEDES = [
  {
    id: 's1',
    idCliente: 'c1',
    idCiudad: 'bog',
    abierta: true,
    areas: [{ id: 'a1', abierta: true }],
  },
  { id: 's2', idCliente: 'c1', idCiudad: 'chi', abierta: true, areas: [] },
  // Cerrada: no cuenta ni como sede ni por su ciudad.
  { id: 's3', idCliente: 'c1', idCiudad: 'tun', abierta: false, areas: [] },
  { id: 's4', idCliente: 'c2', idCiudad: 'tun', abierta: true, areas: [] },
];

const CONTEOS = [
  {
    idArea: 'a1',
    idSede: 's1',
    idCliente: 'c1',
    equipos: 7,
    fueraDeServicio: 1,
    ultimoServicio: '2026-10-06T21:30:00',
  },
  {
    idArea: 'a9',
    idSede: 's1',
    idCliente: 'c1',
    equipos: 2,
    fueraDeServicio: 0,
    ultimoServicio: '2026-09-01T08:00:00',
  },
];

const CIUDADES = [
  { id: 'bog', nombre: 'Bogotá' },
  { id: 'chi', nombre: 'Chía' },
  { id: 'tun', nombre: 'Tunja' },
];

describe('Listado de clientes', () => {
  let fixture: ComponentFixture<ListaClientes>;
  let http: HttpTestingController;

  function montar(
    autoridades: readonly string[] = ['client.read', 'equipment.read', 'location.read'],
  ): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(ListaClientes);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent?.replace(/\s+/g, ' ') ?? '';
  const filas = () => [...raiz().querySelectorAll<HTMLTableRowElement>('tbody tr')];
  const celdas = (fila: HTMLTableRowElement) =>
    [...fila.querySelectorAll('td')].map((td) => td.textContent?.replace(/\s+/g, ' ').trim());
  const boton = (etiqueta: string) =>
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === etiqueta)!;

  /** Responde a lo que la pantalla pida, sea lo que sea, y espera a que llegue al DOM. */
  async function abrir(
    clientes: object = CLIENTES,
    opciones?: { status: number; statusText: string },
  ): Promise<void> {
    fixture.detectChanges();
    await asentar(fixture);
    http.match(URL).forEach((p) => (opciones ? p.flush(clientes, opciones) : p.flush(clientes)));
    http.match((r) => r.url === URL_RESUMEN_SEDES).forEach((p) => p.flush(RESUMEN_SEDES));
    http.match((r) => r.url === URL_CONTEOS).forEach((p) => p.flush(CONTEOS));
    http.match(URL_CIUDADES).forEach((p) => p.flush(CIUDADES));
    await asentar(fixture);
  }

  it('mientras carga lo dice, y lo dice a un lector de pantalla', () => {
    montar();
    fixture.detectChanges();

    expect(raiz().querySelector('[role="status"]')?.textContent).toContain('Cargando');
    http.match(() => true).forEach((p) => p.flush([]));
  });

  it('una fila por cliente activo, por nombre, con sus sedes, ciudades, equipos y último servicio', async () => {
    montar();
    await abrir();

    expect(filas().map((f) => f.getAttribute('data-cliente'))).toEqual(['c1', 'c2']);
    expect(celdas(filas()[0])).toEqual([
      'CDN Clínica del Norte',
      '900123456 NIT jurídico',
      'Bogotá · Chía',
      '2',
      '9',
      '06 oct 2026',
    ]);
    // Sin equipos ni servicios, ceros y raya, no huecos.
    expect(celdas(filas()[1]).slice(3)).toEqual(['1', '0', '—']);
  });

  it('la cabecera resume lo que hay: clientes activos, sedes abiertas y equipos', async () => {
    montar();
    await abrir();

    expect(texto()).toContain('2 clientes activos · 3 sedes · 9 equipos instalados');
  });

  it('la sigla sale en su caja al lado del nombre, que enlaza a la ficha', async () => {
    montar();
    await abrir();

    const primera = filas()[0];
    expect(primera.querySelector('[aria-hidden="true"]')?.textContent?.trim()).toBe('CDN');
    expect(primera.querySelector('a')?.getAttribute('href')).toBe('/clientes/c1');
  });

  it('busca por razón social, sigla o documento sin tildes ni mayúsculas', async () => {
    montar();
    await abrir();
    const entrada = raiz().querySelector<HTMLInputElement>('#buscarClientes')!;

    for (const [buscado, esperado] of [
      ['clinica', ['c1']],
      ['hsr', ['c2']],
      ['8600', ['c2']],
    ] as const) {
      entrada.value = buscado;
      entrada.dispatchEvent(new Event('input'));
      await asentar(fixture);
      expect(filas().map((f) => f.getAttribute('data-cliente'))).toEqual(esperado);
    }

    entrada.value = 'zzz';
    entrada.dispatchEvent(new Event('input'));
    await asentar(fixture);
    expect(texto()).toContain('Ningún cliente activo coincide');
  });

  it('«Retirados» enseña solo los retirados, y se marca pulsado', async () => {
    montar();
    await abrir();
    boton('Retirados').click();
    await asentar(fixture);

    expect(filas().map((f) => f.getAttribute('data-cliente'))).toEqual(['c3']);
    expect(boton('Retirados').getAttribute('aria-pressed')).toBe('true');
    expect(boton('Activos').getAttribute('aria-pressed')).toBe('false');
  });

  it('sin equipment.read ni location.read no pide ni pinta esas columnas', async () => {
    montar(['client.read']);
    fixture.detectChanges();
    await asentar(fixture);
    // Antes de responder nada: abrir() respondería a los conteos si se pidieran, y la prueba no vería
    // que salieron. La primera versión pasó así con la guarda quitada.
    expect(http.match((r) => r.url === URL_CONTEOS)).toHaveLength(0);
    expect(http.match(URL_CIUDADES)).toHaveLength(0);
    await abrir();

    const cabeceras = [...raiz().querySelectorAll('th')].map((th) => th.textContent?.trim());
    expect(cabeceras).toEqual(['Cliente', 'Documento', 'Sedes']);
    expect(texto()).not.toContain('equipos instalados');
  });

  it('sin clientes no enseña una tabla vacía, sino que dice por dónde empezar', async () => {
    montar(['client.read', 'client.write', 'equipment.read', 'location.read']);
    await abrir([]);

    expect(raiz().querySelector('table')).toBeNull();
    expect(texto()).toContain('Empiece dando de alta el primero');
    expect(texto()).toContain('Nuevo cliente');
  });

  describe('Quien solo consulta, como el representante de un cliente (2026-10-26)', () => {
    it('no tiene «Nuevo cliente», que el servidor le rechazaría', async () => {
      montar(['client.read', 'equipment.read']);
      await abrir([]);

      expect(raiz().querySelector('a[href="/clientes/nuevo"]')).toBeNull();
    });

    it('sin clientes le dice que espere a que le asignen uno, no que dé de alta el primero', async () => {
      montar(['client.read', 'equipment.read']);
      await abrir([]);

      expect(texto()).toContain('Todavía no tiene ningún cliente asignado');
      expect(texto()).not.toContain('Empiece dando de alta');
    });
  });

  it('un fallo del API se enseña traducido, con el código real del backend', async () => {
    montar();
    await abrir(
      { code: 'ERR_CLIENT_001', message: 'x', details: [] },
      { status: 404, statusText: 'Not Found' },
    );

    expect(raiz().querySelector('[role="alert"]')?.textContent).toContain('Ese cliente no existe');
  });
});
