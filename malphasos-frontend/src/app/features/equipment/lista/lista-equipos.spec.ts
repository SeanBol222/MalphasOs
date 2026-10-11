import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import { ID_TIPO, TIPOS, URL_TIPOS } from '../../../../testing/catalogo';
import { ESPERA_DEL_BUSCADOR_MS, ListaEquipos, TAMANO_DE_PAGINA } from './lista-equipos';

const API = 'http://localhost:8081/v1/api';
const URL_RESUMEN = `${API}/client-equipments/overview`;
const URL_CLIENTES = `${API}/clients`;

const CLIENTES = [
  { id: 'c1', razonSocial: 'Clínica del Norte', estadoActivo: true },
  { id: 'c2', razonSocial: 'Hospital San Rafael', estadoActivo: true },
];

const FILAS = [
  {
    id: 'ec1',
    numeroHojaVida: 'HV-CDN-0001',
    serie: 'WB1240337',
    idTipoEquipo: ID_TIPO,
    tipoEquipo: 'Balanza',
    marca: 'Kern',
    modelo: 'PCB 3500-2',
    estadoActivo: true,
    idAreaServicio: 'a1',
    areaServicio: 'Laboratorio',
    sede: 'Sede principal',
    cliente: 'Clínica del Norte',
    // De noche en Bogota: pasar por Date en UTC lo corre al dia siguiente.
    ultimoServicio: '2026-10-06T21:30:00',
    resultado: 'OPERATIVO',
  },
  {
    id: 'ec2',
    numeroHojaVida: 'HV-LAN-0008',
    serie: '0004712-01',
    tipoEquipo: 'Centrífuga',
    marca: 'Hettich',
    modelo: 'EBA 200',
    estadoActivo: true,
    idAreaServicio: 'a2',
    areaServicio: 'Hematología',
    sede: 'Planta Chía',
    cliente: 'Laboratorio Andino',
    ultimoServicio: '2026-09-12T10:00:00',
    resultado: 'FUERA_DE_SERVICIO',
  },
  {
    id: 'ec3',
    numeroHojaVida: 'HV-HSR-0042',
    serie: 'FL-0A112584',
    tipoEquipo: 'Monitor',
    marca: 'Mindray',
    modelo: 'uMEC10',
    estadoActivo: true,
    idAreaServicio: 'a3',
    areaServicio: 'Urgencias',
    sede: 'Torre A',
    cliente: 'Hospital San Rafael',
  },
];

const pagina = (filas: object[] = FILAS, total = filas.length, numero = 0) => ({
  filas,
  total,
  pagina: numero,
  tamano: TAMANO_DE_PAGINA,
});

describe('Listado de equipos', () => {
  let fixture: ComponentFixture<ListaEquipos>;
  let http: HttpTestingController;

  function montar(
    autoridades: readonly string[] = ['equipment.read', 'equipment.assign', 'client.read'],
  ): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(ListaEquipos);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent?.replace(/\s+/g, ' ') ?? '';
  const filas = () => [...raiz().querySelectorAll<HTMLTableRowElement>('tbody tr')];
  const celdas = (fila: HTMLTableRowElement) =>
    [...fila.querySelectorAll('td')].map((td) => td.textContent?.replace(/\s+/g, ' ').trim());
  const boton = (etiqueta: string) =>
    [...raiz().querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === etiqueta,
    ) as HTMLButtonElement;

  const delResumen = (peticion: { url: string }) => peticion.url === URL_RESUMEN;

  /** Responde la consulta del listado que este pendiente y devuelve sus parametros. */
  async function responderResumen(cuerpo: object = pagina()): Promise<Record<string, string>> {
    let peticion: TestRequest | undefined;
    for (let intento = 0; intento < 20 && !peticion; intento += 1) {
      fixture.detectChanges();
      peticion = http.match(delResumen).at(-1);
      if (!peticion) {
        await new Promise((r) => setTimeout(r));
      }
    }
    if (!peticion) {
      throw new Error('La pantalla no pidio el listado');
    }
    const parametros = Object.fromEntries(
      peticion.request.params.keys().map((clave) => [clave, peticion!.request.params.get(clave)!]),
    );
    peticion.flush(cuerpo);
    await asentar(fixture);

    return parametros;
  }

  async function abrir(cuerpo: object = pagina()): Promise<Record<string, string>> {
    fixture.detectChanges();
    const parametros = await responderResumen(cuerpo);
    await responderA(fixture, http, URL_CLIENTES, CLIENTES);
    await responderA(fixture, http, URL_TIPOS, TIPOS);

    return parametros;
  }

  async function elegir(id: string, valor: string): Promise<void> {
    const select = raiz().querySelector<HTMLSelectElement>(`#${id}`)!;
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    await asentar(fixture);
  }

  async function pulsar(etiqueta: string): Promise<void> {
    boton(etiqueta).click();
    await asentar(fixture);
  }

  describe('Lo que pinta', () => {
    beforeEach(() => montar());

    it('pide la primera página sin filtros, en una sola petición', async () => {
      const parametros = await abrir();

      expect(parametros).toEqual({ pagina: '0', tamano: String(TAMANO_DE_PAGINA) });
    });

    it('una fila por equipo, con la hoja de vida primero y el equipo, la serie y dónde está', async () => {
      await abrir();

      expect(filas().length).toBe(3);
      expect(celdas(filas()[0])).toEqual([
        'HV-CDN-0001',
        'Balanza Kern · PCB 3500-2',
        'WB1240337',
        'Clínica del Norte Sede principal · Laboratorio',
        '06 oct 2026',
        'Operativo',
      ]);
    });

    it('la fecha es la del día del servicio, aunque se cerrara de noche', async () => {
      await abrir();

      expect(celdas(filas()[0])[4]).toBe('06 oct 2026');
    });

    it('el resultado se marca por peso tipográfico, más pesado cuanto más grave', async () => {
      await abrir();

      const resultado = (i: number) => filas()[i].querySelector('td:last-child > span')!;
      expect(resultado(0).classList).toContain('font-medium');
      expect(resultado(1).classList).toContain('font-extrabold');
      expect(resultado(1).textContent).toContain('Fuera de servicio');
    });

    it('un equipo sin servicios lo dice, con la raya en la fecha', async () => {
      await abrir();

      expect(celdas(filas()[2]).slice(4)).toEqual(['—', 'Sin servicios']);
    });

    it('la hoja de vida, el historial y el área son enlaces', async () => {
      await abrir();

      const enlaces = [...filas()[0].querySelectorAll('a')].map((a) => a.getAttribute('href'));
      expect(enlaces).toEqual(['/equipos/ec1/hoja-de-vida', '/areas/a1', '/equipos/ec1/historial']);
    });

    it('sin equipos y sin filtros lo explica, sin tabla', async () => {
      await abrir(pagina([]));

      expect(raiz().querySelector('table')).toBeNull();
      expect(texto()).toContain('Todavía no hay equipos registrados');
    });

    it('con uno solo el recuento va en singular', async () => {
      await abrir(pagina([FILAS[0]]));

      // Solo el recuento: el titulo oculto de la tabla dice «Equipos instalados».
      expect(raiz().querySelector('[aria-live="polite"]')?.textContent?.trim()).toBe(
        '1 equipo instalado',
      );
    });

    it('cuenta los equipos que hay, no los de la página', async () => {
      await abrir(pagina(FILAS, 312));

      expect(texto()).toContain('312 equipos instalados');
      expect(texto()).not.toContain('con estos filtros');
      expect(texto()).toContain(`1–${TAMANO_DE_PAGINA} de 312`);
    });
  });

  describe('Los filtros van al servidor', () => {
    beforeEach(() => montar());

    it('el buscador espera a que se deje de escribir y manda el texto', async () => {
      await abrir();
      const entrada = raiz().querySelector<HTMLInputElement>('#buscarEquipos')!;
      entrada.value = 'bal';
      entrada.dispatchEvent(new Event('input'));
      entrada.value = 'balanza ';
      entrada.dispatchEvent(new Event('input'));
      await asentar(fixture);

      // Antes de la espera no ha salido nada: una peticion por palabra, no por tecla.
      expect(http.match(delResumen).length).toBe(0);

      await new Promise((r) => setTimeout(r, ESPERA_DEL_BUSCADOR_MS + 50));
      const parametros = await responderResumen();

      expect(parametros['q']).toBe('balanza');
    });

    it('el cliente y el tipo se mandan por su identificador', async () => {
      await abrir();

      await elegir('filtroCliente', 'c2');
      expect((await responderResumen())['idCliente']).toBe('c2');

      await elegir('filtroTipo', ID_TIPO);
      const parametros = await responderResumen();
      expect(parametros['idCliente']).toBe('c2');
      expect(parametros['idTipoEquipo']).toBe(ID_TIPO);
    });

    it('«Fuera de servicio» filtra por el resultado del último servicio y se marca pulsado', async () => {
      await abrir();
      await pulsar('Fuera de servicio');

      expect((await responderResumen())['resultado']).toBe('FUERA_DE_SERVICIO');
      expect(boton('Fuera de servicio').getAttribute('aria-pressed')).toBe('true');
    });

    it('«Dados de baja» pide los dados de baja, y el recuento lo dice', async () => {
      await abrir();
      await pulsar('Dados de baja');

      expect((await responderResumen(pagina([FILAS[0]], 1)))['dadosDeBaja']).toBe('true');
      expect(texto()).toContain('1 equipo dado de baja con estos filtros');
    });

    it('sin resultados con filtros, ofrece quitarlos y quitarlos vuelve a pedir sin ellos', async () => {
      await abrir();
      await pulsar('Fuera de servicio');
      await responderResumen(pagina([]));

      expect(texto()).toContain('Ningún equipo cumple estos filtros');
      await pulsar('Quitar los filtros');

      expect(await responderResumen()).toEqual({ pagina: '0', tamano: String(TAMANO_DE_PAGINA) });
    });

    it('sin client.read no ofrece el filtro de cliente ni pide la lista de clientes', async () => {
      TestBed.resetTestingModule();
      montar(['equipment.read']);
      fixture.detectChanges();
      await responderResumen();
      await responderA(fixture, http, URL_TIPOS, TIPOS);

      expect(raiz().querySelector('#filtroCliente')).toBeNull();
      http.expectNone(URL_CLIENTES);
    });
  });

  describe('Las páginas', () => {
    beforeEach(() => montar());

    it('«Siguiente» pide la página siguiente y «Anterior» vuelve', async () => {
      await abrir(pagina(FILAS, 60));
      expect(boton('Anterior').disabled).toBe(true);

      await pulsar('Siguiente');
      expect((await responderResumen(pagina(FILAS, 60, 1)))['pagina']).toBe('1');
      expect(texto()).toContain(`${TAMANO_DE_PAGINA + 1}–${2 * TAMANO_DE_PAGINA} de 60`);

      await pulsar('Anterior');
      expect((await responderResumen(pagina(FILAS, 60, 0)))['pagina']).toBe('0');
    });

    it('en la última página no hay siguiente', async () => {
      await abrir(pagina(FILAS, 3));

      expect(boton('Siguiente').disabled).toBe(true);
    });

    it('cambiar un filtro vuelve a la primera página', async () => {
      await abrir(pagina(FILAS, 60));
      await pulsar('Siguiente');
      await responderResumen(pagina(FILAS, 60, 1));

      await pulsar('Fuera de servicio');
      expect((await responderResumen())['pagina']).toBe('0');
    });
  });

  describe('Permisos', () => {
    it('sin equipment.assign no ofrece registrar', async () => {
      montar(['equipment.read', 'client.read']);
      await abrir();

      expect(texto()).not.toContain('Registrar equipo');
    });

    it('con equipment.assign enlaza al alta', async () => {
      montar();
      await abrir();

      expect(raiz().querySelector('a[href="/equipos/nuevo"]')).not.toBeNull();
    });
  });
});
