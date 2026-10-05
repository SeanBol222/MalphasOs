import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, responderA } from '../../../../testing/pantalla';
import {
  EQUIPOS,
  ID_EQUIPO,
  ID_FABRICANTE,
  ID_MARCA,
  ID_MODELO,
  ID_TIPO,
  MARCAS,
  MODELOS,
  responderAlCatalogo,
  TIPOS,
} from '../../../../testing/catalogo';
import { NuevoEquipo } from './nuevo-equipo';

const ID_AREA = 'a1';
const URL_AREA = `http://localhost:8081/v1/api/service-areas/${ID_AREA}`;
const URL_EQUIPOS_DEL_AREA = `${URL_AREA}/equipments`;

const AREA = { id: ID_AREA, nombre: 'Urgencias', idSede: 's1', estadoActivo: true };

const API = 'http://localhost:8081/v1/api';
const ID_CLIENTE = 'c1';
const ID_SEDE = 's1';
const CLIENTES = [{ id: ID_CLIENTE, razonSocial: 'Hospital Central', estadoActivo: true }];
const SEDES = [
  { id: ID_SEDE, nombre: 'Sede Norte', idCliente: ID_CLIENTE, estadoActivo: true },
  { id: 's2', nombre: 'Sede cerrada', idCliente: ID_CLIENTE, estadoActivo: false },
];
const AREAS = [
  AREA,
  { id: 'a2', nombre: 'Bodega cerrada', idSede: ID_SEDE, estadoActivo: false },
];

@Component({ selector: 'app-ficha-area-falsa', template: '' })
class FichaAreaFalsa {}

describe('Registro de un equipo en un area', () => {
  let fixture: ComponentFixture<NuevoEquipo>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'areas/:id', component: FichaAreaFalsa }]),
      ],
    });
    fixture = TestBed.createComponent(NuevoEquipo);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_AREA);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(datos: Parameters<typeof responderAlCatalogo>[2] = {}): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_AREA, AREA);
    await responderAlCatalogo(fixture, http, datos);
  }

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  /** Elige el modelo por la cascada, que es la unica forma de que su opcion exista. */
  function elegirModelo(): void {
    escribir('idTipoElegido', ID_TIPO);
    escribir('idMarcaElegida', ID_MARCA);
    escribir('idModelo', ID_MODELO);
  }

  const opcionesDe = (id: string) =>
    [...raiz().querySelectorAll<HTMLOptionElement>(`#${id} option`)].map((o) => o.textContent?.trim());

  async function enviar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);
  }

  it('dice en que area se instala', async () => {
    await abrir();

    expect(texto()).toContain('Urgencias');
  });

  describe('El modelo se elige de lo general a lo concreto: tipo, marca y modelo', () => {
    // Decidido el 2026-10-04. Antes era una sola lista de modelos etiquetada «tipo · marca ·
    // fabricante», sin el nombre del modelo: dos modelos de la misma marca y el mismo tipo salian como
    // dos opciones identicas, y quien registra un equipo no sabe de memoria como se llama cada uno.

    /** Un catalogo con dos marcas del mismo tipo, una sin modelos, y un modelo retirado. */
    const CATALOGO = {
      marcas: [...MARCAS, { id: 'm3', nombre: 'Omron', estadoActivo: true }],
      equipos: [
        ...EQUIPOS,
        { id: 'e3', idTipoEquipo: ID_TIPO, idMarca: 'm3', estadoActivo: true },
      ],
      modelos: [
        ...MODELOS,
        {
          id: 'mo2',
          nombre: 'ProBP 3400',
          idEquipo: ID_EQUIPO,
          idFabricante: ID_FABRICANTE,
          estadoActivo: true,
        },
        // Omron tiene la combinacion con el tipo, pero su unico modelo esta retirado.
        { id: 'mo3', nombre: 'Retirado', idEquipo: 'e3', idFabricante: ID_FABRICANTE, estadoActivo: false },
      ],
    };

    it('al abrir solo pide el tipo; marca y modelo esperan a lo de arriba', async () => {
      await abrir(CATALOGO);

      expect(opcionesDe('idTipoElegido')).toEqual(['Elija un tipo', 'Tensiómetro']);
      expect(opcionesDe('idMarcaElegida')).toEqual(['Elija primero el tipo']);
      expect(opcionesDe('idModelo')).toEqual(['Elija primero la marca']);
    });

    it('con el tipo elegido ofrece solo las marcas que tienen algun modelo vigente de ese tipo', async () => {
      // Omron tiene la combinacion, pero su modelo esta retirado: ofrecerla llevaria a una lista de
      // modelos vacia, que es un callejon sin salida.
      await abrir(CATALOGO);
      escribir('idTipoElegido', ID_TIPO);

      expect(opcionesDe('idMarcaElegida')).toEqual(['Elija una marca', 'Welch Allyn']);
    });

    it('los modelos salen por su nombre, con el fabricante, y dos de la misma marca se distinguen', async () => {
      await abrir(CATALOGO);
      escribir('idTipoElegido', ID_TIPO);
      escribir('idMarcaElegida', ID_MARCA);

      expect(opcionesDe('idModelo')).toEqual([
        'Elija un modelo',
        'Connex Spot · Medtronic',
        'ProBP 3400 · Medtronic',
      ]);
    });

    it('cambiar de tipo borra la marca y el modelo elegidos', async () => {
      await abrir(CATALOGO);
      elegirModelo();

      escribir('idTipoElegido', '');

      const formulario = fixture.componentInstance['formulario'];
      expect(formulario.getRawValue().idMarcaElegida).toBe('');
      expect(formulario.getRawValue().idModelo).toBe('');
    });

    it('cambiar de marca borra el modelo elegido', async () => {
      await abrir(CATALOGO);
      elegirModelo();

      escribir('idMarcaElegida', '');

      expect(fixture.componentInstance['formulario'].getRawValue().idModelo).toBe('');
    });

    it('un tipo sin modelos lo dice y apunta al boton de crear', async () => {
      await abrir({
        ...CATALOGO,
        tipos: [...TIPOS, { id: 't2', nombre: 'Desfibrilador', estadoActivo: true }],
      });
      escribir('idTipoElegido', 't2');

      expect(texto()).toContain('Todavía no hay modelos de este tipo');
    });

    it('lo que se manda al servidor es el modelo: tipo y marca no viajan', async () => {
      await abrir(CATALOGO);
      elegirModelo();
      escribir('serie', 'SN-0001');
      await enviar();

      const alta = http.expectOne({ method: 'POST', url: URL_EQUIPOS_DEL_AREA });
      expect(alta.request.body).toEqual({ idModelo: ID_MODELO, serie: 'SN-0001' });
      alta.flush({ id: 'ec1' });
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });
  });

  it('sin modelos en el catalogo lo explica y enlaza al catalogo', async () => {
    // Un desplegable vacio dejaria a quien lo abre sin saber que hacer, y la causa no esta en esta
    // pantalla sino en la cadena del catalogo.
    await abrir({ modelos: [] });

    expect(texto()).toContain('No hay modelos en el catálogo');
    expect([...raiz().querySelectorAll('a')].map((a) => a.getAttribute('href'))).toContain(
      '/catalogo',
    );
  });

  it('manda el modelo y la serie, que es lo unico obligatorio', async () => {
    await abrir();
    elegirModelo();
    escribir('serie', 'SN-0001');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_EQUIPOS_DEL_AREA });

    expect(alta.request.body).toEqual({ idModelo: ID_MODELO, serie: 'SN-0001' });
    alta.flush({ id: 'ec1' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('los datos opcionales vacios no viajan', async () => {
    // Una cadena vacia no es un numero de inventario y una fecha vacia no es una fecha de compra:
    // mandarlas haria que el backend guardara un dato inventado.
    await abrir();
    elegirModelo();
    escribir('serie', 'SN-0001');
    escribir('numeroInventario', '');
    escribir('fechaCompra', '');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_EQUIPOS_DEL_AREA });

    expect(alta.request.body).not.toHaveProperty('numeroInventario');
    expect(alta.request.body).not.toHaveProperty('fechaCompra');
    expect(alta.request.body).not.toHaveProperty('valorCompra');
    expect(alta.request.body).not.toHaveProperty('codigoInterno');
    expect(alta.request.body).not.toHaveProperty('proveedor');
    alta.flush({ id: 'ec1' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('con los opcionales rellenos, los manda tal como el contrato los declara', async () => {
    await abrir();
    elegirModelo();
    escribir('serie', 'SN-0001');
    escribir('numeroInventario', 'INV-77');
    escribir('fechaCompra', '2026-01-15');
    escribir('valorCompra', '1500000');
    escribir('codigoInterno', ' MON-03 ');
    escribir('proveedor', 'Distribuidora Médica');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_EQUIPOS_DEL_AREA });

    expect(alta.request.body).toEqual({
      idModelo: ID_MODELO,
      serie: 'SN-0001',
      numeroInventario: 'INV-77',
      fechaCompra: '2026-01-15',
      valorCompra: 1500000,
      // Entraron el 2026-10-05 para la hoja de vida impresa; recortados como el resto.
      codigoInterno: 'MON-03',
      proveedor: 'Distribuidora Médica',
    });
    alta.flush({ id: 'ec1' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('sin serie no llega al servidor, y dice por que importa', async () => {
    await abrir();
    elegirModelo();
    await enviar();

    http.expectNone({ method: 'POST', url: URL_EQUIPOS_DEL_AREA });
    expect(texto()).toContain('es lo que identifica a esta máquina');
  });

  it('al guardar vuelve a la ficha del area', async () => {
    await abrir();
    elegirModelo();
    escribir('serie', 'SN-0001');
    await enviar();
    http.expectOne({ method: 'POST', url: URL_EQUIPOS_DEL_AREA }).flush({ id: 'ec1' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(TestBed.inject(Router).url).toBe(`/areas/${ID_AREA}`);
  });

  it('un fallo del API se ensena traducido, con el codigo real del modulo', async () => {
    await abrir();
    elegirModelo();
    escribir('serie', 'SN-0001');
    await enviar();
    http.expectOne({ method: 'POST', url: URL_EQUIPOS_DEL_AREA }).flush(
      { code: 'ERR_EQUIPMENT_009', message: 'Service area not found', details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    await asentar(fixture);

    const aviso = raiz().querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Esa área de servicio no existe');
  });

  describe('Lo que WCAG exige y no se ve', () => {
    it('cada campo tiene su etiqueta asociada', async () => {
      await abrir();

      for (const id of [
        'idTipoElegido',
        'idMarcaElegida',
        'idModelo',
        'serie',
        'numeroInventario',
        'fechaCompra',
        'valorCompra',
        'codigoInterno',
        'proveedor',
      ]) {
        expect(raiz().querySelector(`label[for="${id}"]`)).toBeTruthy();
      }
    });

    it('los controles respetan el area tactil minima', async () => {
      await abrir();

      for (const control of raiz().querySelectorAll('input, select, button, form a')) {
        expect(control.className).toContain('min-h-tactil');
      }
    });
  });

  describe('Entrando desde el listado de equipos, sin area fijada', () => {
    beforeEach(() => {
      // Se vuelve a montar sin el parametro de ruta: es el otro camino de entrada, y la pantalla es
      // la misma a proposito. Duplicarla habria sido la via directa a que uno de los dos se quedara
      // sin un arreglo.
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          ...proveerApiSimulado(),
          provideRouter([{ path: 'areas/:id', component: FichaAreaFalsa }]),
        ],
      });
      fixture = TestBed.createComponent(NuevoEquipo);
      http = TestBed.inject(HttpTestingController);
    });

    /** Abre la pantalla en modo encadenado y responde al catalogo y a la lista de clientes. */
    async function abrirEncadenado(): Promise<void> {
      fixture.detectChanges();
      await responderA(fixture, http, `${API}/clients`, CLIENTES);
      await responderAlCatalogo(fixture, http);
    }

    it('pregunta cliente, sede y area, porque quien llega aqui no viene de un cliente', async () => {
      await abrirEncadenado();

      for (const id of ['idCliente', 'idSede', 'idAreaElegida']) {
        expect(raiz().querySelector(`#${id}`)).toBeTruthy();
      }
    });

    it('la sede no se consulta hasta elegir cliente, y el area hasta elegir sede', async () => {
      // Sin esto se pediria /clients//headquarters con un identificador vacio, que es un 404 seguro.
      await abrirEncadenado();

      http.expectNone(`${API}/clients/${ID_CLIENTE}/headquarters`);

      escribir('idCliente', ID_CLIENTE);
      await asentar(fixture);
      await responderA(fixture, http, `${API}/clients/${ID_CLIENTE}/headquarters`, SEDES);

      http.expectNone(`${API}/headquarters/${ID_SEDE}/service-areas`);

      escribir('idSede', ID_SEDE);
      await asentar(fixture);
      await responderA(fixture, http, `${API}/headquarters/${ID_SEDE}/service-areas`, AREAS);

      expect([...raiz().querySelectorAll<HTMLOptionElement>('#idAreaElegida option')].length).toBe(2);
    });

    it('solo ofrece sedes y areas abiertas: el backend rechaza una cerrada', async () => {
      await abrirEncadenado();
      escribir('idCliente', ID_CLIENTE);
      await asentar(fixture);
      await responderA(fixture, http, `${API}/clients/${ID_CLIENTE}/headquarters`, SEDES);
      escribir('idSede', ID_SEDE);
      await asentar(fixture);
      await responderA(fixture, http, `${API}/headquarters/${ID_SEDE}/service-areas`, AREAS);

      expect(texto()).not.toContain('Sede cerrada');
      expect(texto()).not.toContain('Bodega cerrada');
    });

    it('cambiar de cliente borra la sede y el area elegidas', async () => {
      // Sin esto quedaria una sede de otro cliente seleccionada y el alta iria a un area ajena.
      await abrirEncadenado();
      escribir('idCliente', ID_CLIENTE);
      await asentar(fixture);
      await responderA(fixture, http, `${API}/clients/${ID_CLIENTE}/headquarters`, SEDES);
      escribir('idSede', ID_SEDE);
      await asentar(fixture);
      await responderA(fixture, http, `${API}/headquarters/${ID_SEDE}/service-areas`, AREAS);
      escribir('idAreaElegida', ID_AREA);
      await asentar(fixture);
      // Al fijarse el area, la pantalla consulta su ficha para poder nombrarla.
      http.match(`${API}/service-areas/${ID_AREA}`).forEach((p) => p.flush(AREA));

      escribir('idCliente', '');
      await asentar(fixture);

      expect(raiz().querySelector<HTMLSelectElement>('#idSede')!.value).toBe('');
      expect(raiz().querySelector<HTMLSelectElement>('#idAreaElegida')!.value).toBe('');
    });

    it('sin area elegida no llega al servidor, y lo dice', async () => {
      await abrirEncadenado();
      elegirModelo();
      escribir('serie', 'SN-0001');
      await enviar();

      http.expectNone({ method: 'POST', url: `${API}/service-areas/${ID_AREA}/equipments` });
      expect(texto()).toContain('Elija el área en la que se instala');
    });

    it('registra en el area elegida, y vuelve a su ficha', async () => {
      await abrirEncadenado();
      escribir('idCliente', ID_CLIENTE);
      await asentar(fixture);
      await responderA(fixture, http, `${API}/clients/${ID_CLIENTE}/headquarters`, SEDES);
      escribir('idSede', ID_SEDE);
      await asentar(fixture);
      await responderA(fixture, http, `${API}/headquarters/${ID_SEDE}/service-areas`, AREAS);
      escribir('idAreaElegida', ID_AREA);
      await asentar(fixture);
      // Al fijarse el area, la pantalla consulta su detalle igual que en el otro camino.
      http.match(`${API}/service-areas/${ID_AREA}`).forEach((p) => p.flush(AREA));
      elegirModelo();
      escribir('serie', 'SN-0001');
      await enviar();

      const alta = http.expectOne({
        method: 'POST',
        url: `${API}/service-areas/${ID_AREA}/equipments`,
      });

      expect(alta.request.body).toEqual({ idModelo: ID_MODELO, serie: 'SN-0001' });
      alta.flush({ id: 'ec1' });
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);

      expect(TestBed.inject(Router).url).toBe(`/areas/${ID_AREA}`);
    });
  });

  describe('Crear el modelo sin salir', () => {
    it('ofrece crearlo aqui mismo, en vez de mandar al catalogo y perder lo escrito', async () => {
      // Se entra con area fijada, que es el caso simple: el otro camino ya esta probado arriba.
      await abrir();

      const boton = [...raiz().querySelectorAll('button')].find((b) =>
        (b.textContent ?? '').includes('crearlo aquí'),
      );

      expect(boton).toBeTruthy();

      boton!.click();
      fixture.detectChanges();
      await asentar(fixture);

      // El panel repite las consultas del catalogo y pide paises para el fabricante nuevo. Las repite
      // porque la cache de estas pruebas se vacia al instante -gcTime en cero-, para que el orden de
      // ejecucion no cambie el resultado.
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);

      expect(texto()).toContain('Crear un modelo');
      expect(raiz().querySelector('#idTipo')).toBeTruthy();
    });

    it('lo creado en el panel queda elegido en la cascada: tipo, marca y modelo', async () => {
      // Con solo el modelo, el desplegable no lo ofreceria: se filtra por el tipo y la marca elegidos.
      await abrir();

      fixture.componentInstance['usarModelo']({ idModelo: ID_MODELO, idTipo: ID_TIPO, idMarca: ID_MARCA });
      fixture.detectChanges();

      expect(fixture.componentInstance['formulario'].getRawValue()).toEqual(
        expect.objectContaining({
          idTipoElegido: ID_TIPO,
          idMarcaElegida: ID_MARCA,
          idModelo: ID_MODELO,
        }),
      );
      expect(raiz().querySelector<HTMLSelectElement>('#idModelo')!.value).toBe(ID_MODELO);
      expect(texto()).not.toContain('Crear un modelo');
    });

    it('el panel se abre con el tipo y la marca que ya se eligieron', async () => {
      // Quien elige tipo y marca y no encuentra su modelo no deberia tener que elegirlos otra vez.
      await abrir();
      escribir('idTipoElegido', ID_TIPO);
      escribir('idMarcaElegida', ID_MARCA);

      [...raiz().querySelectorAll('button')]
        .find((b) => (b.textContent ?? '').includes('crearlo aquí'))!
        .click();
      fixture.detectChanges();
      await asentar(fixture);
      await responderAlCatalogo(fixture, http);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);

      expect(raiz().querySelector<HTMLSelectElement>('#idTipo')!.value).toBe(ID_TIPO);
      expect(raiz().querySelector<HTMLSelectElement>('#idMarca')!.value).toBe(ID_MARCA);
    });

    it('con el panel abierto no hay dos elementos con el mismo id en la pagina', async () => {
      // El panel tiene sus propios #idTipo e #idMarca. Si el formulario los usara tambien, las
      // etiquetas dejarian de saber a cual apuntan, y un lector de pantalla leeria la equivocada.
      await abrir();
      [...raiz().querySelectorAll('button')]
        .find((b) => (b.textContent ?? '').includes('crearlo aquí'))!
        .click();
      fixture.detectChanges();
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);

      const ids = [...raiz().querySelectorAll('[id]')].map((e) => e.id);
      expect(ids.filter((id, i) => ids.indexOf(id) !== i)).toEqual([]);
    });
  });

  afterEach(() => http.verify());
});
