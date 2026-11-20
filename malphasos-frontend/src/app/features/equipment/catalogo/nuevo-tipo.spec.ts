import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, elegirEnBuscador, escribirEnBuscador, responderA } from '../../../../testing/pantalla';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
import {
  ID_CELSIUS,
  ID_MMHG,
  ID_PRESION,
  ID_TEMPERATURA,
  MAGNITUDES,
  PROTOCOLOS,
  TIPOS,
  UNIDADES,
  URL_MAGNITUDES,
  URL_PROTOCOLOS,
  URL_PROTOCOLOS_DE_TIPO,
  URL_TIPOS,
  URL_UNIDADES_DE,
  FABRICANTES,
  ID_FABRICANTE,
  ID_MARCA,
  MARCAS,
  URL_EQUIPOS,
  URL_FABRICANTES,
  URL_MARCAS,
  URL_MODELOS,
} from '../../../../testing/catalogo';
import { NuevoTipo } from './nuevo-tipo';

@Component({ selector: 'app-catalogo-falso', template: '' })
class CatalogoFalso {}

describe('Alta de un tipo de equipo', () => {
  let fixture: ComponentFixture<NuevoTipo>;
  let http: HttpTestingController;
  let desinstalarAlmacenamiento: () => void;

  beforeEach(async () => {
    desinstalarAlmacenamiento = instalarAlmacenamiento();
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'catalogo', component: CatalogoFalso }]),
      ],
    });
    fixture = TestBed.createComponent(NuevoTipo);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    // La pantalla consulta los tipos ya registrados para sugerir sus tecnologias.
    await responderA(fixture, http, URL_TIPOS, TIPOS);
    // Y el bloque de verificacion pide el catalogo metrologico al montarse, aunque todavia no haya
    // ningun panel: son datos de referencia que no cambian, se piden una vez y se quedan en cache, de
    // modo que el panel que se abra despues ya los tiene. Hay que responderlo en TODA prueba de esta
    // pantalla o verify() lanza en afterEach.
    await responderA(fixture, http, URL_MAGNITUDES, MAGNITUDES);
    // Desde el 2026-10-10 el bloque de protocolos pide el catalogo al montarse.
    await responderA(fixture, http, URL_PROTOCOLOS, []);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  /** Vuelve a montar la pantalla con otros tipos registrados, de los que salen las autorizadas. */
  async function remontar(tipos: object[]): Promise<void> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'catalogo', component: CatalogoFalso }]),
      ],
    });
    fixture = TestBed.createComponent(NuevoTipo);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await responderA(fixture, http, URL_TIPOS, tipos);
    await responderA(fixture, http, URL_MAGNITUDES, MAGNITUDES);
    await responderA(fixture, http, URL_PROTOCOLOS, []);
  }

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function rellenarObligatorios(): void {
    escribir('nombre', 'Tensiómetro');
    // La tecnologia es un campo de busqueda con texto libre: se escribe igual que en un input.
    escribir('tecnologiaPredominante', 'Electrónica');
    escribir('definicionTecnica', 'Mide presión arterial');
    escribir('recomendacionesCuidado', 'No golpear');
  }

  async function enviar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);
  }

  describe('Dos columnas: los datos generales y lo opcional (2026-11-20)', () => {
    const columna = (id: string) =>
      raiz().querySelector<HTMLElement>(`section[aria-labelledby="${id}"]`)!;
    const seccion = (nombre: string) =>
      raiz().querySelector<HTMLButtonElement>(`[data-seccion="${nombre}"]`)!;

    it('a la izquierda los obligatorios, el protocolo y los botones; a la derecha lo opcional', () => {
      const generales = columna('datosGenerales');
      const opcionales = columna('datosOpcionales');

      for (const id of ['nombre', 'definicionTecnica', 'recomendacionesCuidado']) {
        expect(generales.querySelector(`#${id}`)).not.toBeNull();
      }
      expect(generales.querySelector('app-protocolos-del-tipo')).not.toBeNull();
      expect(generales.querySelector('button[type="submit"]')).not.toBeNull();
      expect(generales.textContent).toContain('Los campos con * son obligatorios');

      expect(opcionales.querySelector('app-verificacion')).not.toBeNull();
      expect(opcionales.querySelector('#uso')).not.toBeNull();
      expect(opcionales.querySelector('[data-seccion="modelos"]')).not.toBeNull();
    });

    it('las secciones de lo opcional abren plegadas, y cada una se abre por su cuenta', async () => {
      for (const nombre of ['verificacion', 'modelos', 'ficha']) {
        expect(seccion(nombre).getAttribute('aria-expanded')).toBe('false');
      }
      expect(raiz().querySelector<HTMLElement>('#seccionFicha')!.hidden).toBe(true);

      seccion('ficha').click();
      await asentar(fixture);

      expect(raiz().querySelector<HTMLElement>('#seccionFicha')!.hidden).toBe(false);
      expect(raiz().querySelector<HTMLElement>('#seccionVerificacion')!.hidden).toBe(true);

      seccion('ficha').click();
      await asentar(fixture);
      expect(raiz().querySelector<HTMLElement>('#seccionFicha')!.hidden).toBe(true);
    });

    it('la cabecera de la ficha dice cuantos de sus tres datos lleva', async () => {
      expect(seccion('ficha').textContent).toContain('Sin completar');

      escribir('uso', 'Consulta externa');
      escribir('valorUnitarioMantenimiento', '120000');
      await asentar(fixture);

      expect(seccion('ficha').textContent).toContain('2 de 3');
    });
  });

  describe('Las marcas y los modelos, plegados (2026-11-19)', () => {
    const seccion = () =>
      [...raiz().querySelectorAll('button')].find((b) => b.textContent?.includes('Marcas y modelos'))!;

    /** Abre la seccion y responde a las dos listas que el bloque pide al crearse. */
    async function abrirSeccion(): Promise<void> {
      seccion().click();
      await asentar(fixture);
      await responderA(fixture, http, URL_MARCAS, MARCAS);
      await responderA(fixture, http, URL_FABRICANTES, FABRICANTES);
    }

    function pulsar(etiqueta: string): void {
      [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === etiqueta)!.click();
      fixture.detectChanges();
    }

    /** Responde a la peticion de escritura que toca, y devuelve lo que se mando. */
    async function responderEscritura(url: string, respuesta: object): Promise<unknown> {
      await asentar(fixture);
      const peticion = http.expectOne({ method: 'POST', url });
      peticion.flush(respuesta);
      await asentar(fixture);

      return peticion.request.body;
    }

    /** Las recargas que cada escritura del catalogo lanza, hasta que no quede ninguna. */
    async function atenderRecargas(): Promise<void> {
      for (let ronda = 0; ronda < 3; ronda += 1) {
        await asentar(fixture);
        http.match(() => true).forEach((p) => p.flush([]));
      }
      await asentar(fixture);
    }

    it('esta plegada, y sin abrirla no pide marcas ni fabricantes', () => {
      expect(seccion().getAttribute('aria-expanded')).toBe('false');
      expect(raiz().querySelector('#marcaDeModelo0')).toBeNull();
      http.expectNone(URL_MARCAS);
      http.expectNone(URL_FABRICANTES);
    });

    it('al abrirla trae ya una fila, y plegarla no pierde lo escrito', async () => {
      await abrirSeccion();
      expect(seccion().getAttribute('aria-expanded')).toBe('true');
      escribir('nombreDeModelo0', 'Connex Spot');

      seccion().click();
      await asentar(fixture);
      expect(raiz().querySelector<HTMLElement>('#marcasYModelos')!.hidden).toBe(true);
      seccion().click();
      await asentar(fixture);
      expect(raiz().querySelector<HTMLElement>('#marcasYModelos')!.hidden).toBe(false);

      expect(raiz().querySelector<HTMLInputElement>('#nombreDeModelo0')!.value).toBe('Connex Spot');
      // Y la cabecera dice cuantos lleva, para que se vea aun plegada.
      expect(seccion().textContent).toContain('1 modelo');
    });

    it('con marca y fabricante existentes crea el tipo, la combinacion y el modelo, en ese orden', async () => {
      await abrirSeccion();
      rellenarObligatorios();
      escribir('marcaDeModelo0', ID_MARCA);
      escribir('fabricanteDeModelo0', ID_FABRICANTE);
      escribir('nombreDeModelo0', 'Connex Spot');
      escribir('invimaDeModelo0', 'INV-9');
      await enviar();

      await responderEscritura(URL_TIPOS, { id: 't9' });
      http.expectNone({ method: 'POST', url: URL_MARCAS });
      expect(await responderEscritura(URL_EQUIPOS, { id: 'e9' })).toEqual({
        idTipoEquipo: 't9',
        idMarca: ID_MARCA,
      });
      expect(await responderEscritura(URL_MODELOS, { id: 'mo9' })).toEqual({
        nombre: 'Connex Spot',
        idEquipo: 'e9',
        idFabricante: ID_FABRICANTE,
        invima: 'INV-9',
      });
      await atenderRecargas();

      expect(TestBed.inject(Router).url).toBe('/catalogo');
    });

    it('una marca y un fabricante nuevos se crean antes, con el nombre escrito', async () => {
      await abrirSeccion();
      rellenarObligatorios();
      escribir('marcaDeModelo0', 'nuevo');
      escribir('nombreDeMarca0', 'Omron');
      escribir('fabricanteDeModelo0', 'nuevo');
      escribir('nombreDeFabricante0', 'Omron Healthcare');
      escribir('nombreDeModelo0', 'HEM-7120');
      await enviar();

      await responderEscritura(URL_TIPOS, { id: 't9' });
      expect(await responderEscritura(URL_MARCAS, { id: 'm9' })).toEqual({ nombre: 'Omron' });
      expect(await responderEscritura(URL_FABRICANTES, { id: 'f9' })).toEqual({
        nombre: 'Omron Healthcare',
      });
      expect(await responderEscritura(URL_EQUIPOS, { id: 'e9' })).toEqual({
        idTipoEquipo: 't9',
        idMarca: 'm9',
      });
      // Sin INVIMA la clave no viaja.
      expect(await responderEscritura(URL_MODELOS, { id: 'mo9' })).toEqual({
        nombre: 'HEM-7120',
        idEquipo: 'e9',
        idFabricante: 'f9',
      });
      await atenderRecargas();
    });

    it('una marca nueva escrita con el nombre de una que existe es esa, y no se duplica', async () => {
      await abrirSeccion();
      rellenarObligatorios();
      escribir('marcaDeModelo0', 'nuevo');
      escribir('nombreDeMarca0', 'WELCH ALLYN');
      escribir('fabricanteDeModelo0', ID_FABRICANTE);
      escribir('nombreDeModelo0', 'Connex Spot');
      await enviar();

      await responderEscritura(URL_TIPOS, { id: 't9' });
      http.expectNone({ method: 'POST', url: URL_MARCAS });
      expect(await responderEscritura(URL_EQUIPOS, { id: 'e9' })).toEqual({
        idTipoEquipo: 't9',
        idMarca: ID_MARCA,
      });
      await responderEscritura(URL_MODELOS, { id: 'mo9' });
      await atenderRecargas();
    });

    it('dos modelos de la misma marca nueva crean la marca y la combinacion una sola vez', async () => {
      await abrirSeccion();
      rellenarObligatorios();
      escribir('marcaDeModelo0', 'nuevo');
      escribir('nombreDeMarca0', 'Omron');
      escribir('fabricanteDeModelo0', ID_FABRICANTE);
      escribir('nombreDeModelo0', 'HEM-7120');
      pulsar('Agregar otro modelo');
      escribir('marcaDeModelo1', 'nuevo');
      escribir('nombreDeMarca1', 'omron');
      escribir('fabricanteDeModelo1', ID_FABRICANTE);
      escribir('nombreDeModelo1', 'HEM-7156');
      await enviar();

      await responderEscritura(URL_TIPOS, { id: 't9' });
      await responderEscritura(URL_MARCAS, { id: 'm9' });
      await responderEscritura(URL_EQUIPOS, { id: 'e9' });
      expect(await responderEscritura(URL_MODELOS, { id: 'mo1' })).toMatchObject({
        nombre: 'HEM-7120',
        idEquipo: 'e9',
      });
      // La segunda fila reutiliza la marca y la combinacion de la primera.
      http.expectNone({ method: 'POST', url: URL_MARCAS });
      http.expectNone({ method: 'POST', url: URL_EQUIPOS });
      expect(await responderEscritura(URL_MODELOS, { id: 'mo2' })).toMatchObject({
        nombre: 'HEM-7156',
        idEquipo: 'e9',
      });
      await atenderRecargas();
    });

    it('una fila quitada no se crea', async () => {
      await abrirSeccion();
      rellenarObligatorios();
      pulsar('Quitar este modelo');
      await enviar();

      await responderEscritura(URL_TIPOS, { id: 't9' });
      http.expectNone({ method: 'POST', url: URL_MODELOS });
      await atenderRecargas();

      expect(TestBed.inject(Router).url).toBe('/catalogo');
    });

    it('una fila a medias no deja guardar, y si estaba plegada se abre para verla', async () => {
      await abrirSeccion();
      rellenarObligatorios();
      escribir('nombreDeModelo0', 'Connex Spot');
      seccion().click();
      await asentar(fixture);
      await enviar();

      http.expectNone({ method: 'POST', url: URL_TIPOS });
      expect(seccion().getAttribute('aria-expanded')).toBe('true');
      expect(texto()).toContain('Elija o escriba la marca y el fabricante');
    });

    it('si un modelo falla, dice que el tipo si se creo y cuantos modelos quedaron', async () => {
      await abrirSeccion();
      rellenarObligatorios();
      escribir('marcaDeModelo0', ID_MARCA);
      escribir('fabricanteDeModelo0', ID_FABRICANTE);
      escribir('nombreDeModelo0', 'Connex Spot');
      await enviar();

      await responderEscritura(URL_TIPOS, { id: 't9' });
      await responderEscritura(URL_EQUIPOS, { id: 'e9' });
      await asentar(fixture);
      http
        .expectOne({ method: 'POST', url: URL_MODELOS })
        .flush({ code: 'X' }, { status: 409, statusText: 'Conflict' });
      await atenderRecargas();

      expect(texto()).toContain('El tipo se creó, y 0 de 1 modelos');
      expect(TestBed.inject(Router).url).not.toBe('/catalogo');
    });
  });

  describe('Los protocolos del preventivo, con el mismo botón (2026-10-10)', () => {
    /** Vuelve a montar con el catalogo de protocolos lleno: el de arriba lo responde vacio. */
    async function conCatalogoDeProtocolos(): Promise<void> {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [...proveerApiSimulado(), provideRouter([{ path: 'catalogo', component: CatalogoFalso }])],
      });
      fixture = TestBed.createComponent(NuevoTipo);
      http = TestBed.inject(HttpTestingController);
      fixture.detectChanges();
      await responderA(fixture, http, URL_TIPOS, TIPOS);
      await responderA(fixture, http, URL_MAGNITUDES, MAGNITUDES);
      await responderA(fixture, http, URL_PROTOCOLOS, PROTOCOLOS);
    }

    async function agregarLimpieza(): Promise<void> {
      await escribirEnBuscador(fixture, 'agregarProtocolo', 'limp');
      [...raiz().querySelectorAll<HTMLElement>('#agregarProtocolo-lista [role="option"]')]
        .find((opcion) => opcion.textContent?.trim() === 'Limpieza y desinfección')!
        .dispatchEvent(new MouseEvent('mousedown'));
      await asentar(fixture);
    }

    it('crea el tipo y después le fija los protocolos en su orden', async () => {
      await conCatalogoDeProtocolos();
      await agregarLimpieza();
      rellenarObligatorios();
      await enviar();

      const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });
      // Los protocolos no viajan con el tipo: tienen su propia ruta, y el tipo aun no tiene id.
      expect(alta.request.body).not.toHaveProperty('protocolos');
      alta.flush({ id: 't9' });
      await asentar(fixture);

      const asignacion = http.expectOne({ method: 'PATCH', url: URL_PROTOCOLOS_DE_TIPO('t9') });
      expect(asignacion.request.body).toEqual({ protocolos: [{ idProtocolo: 'pr1' }] });
      asignacion.flush([]);
      for (let ronda = 0; ronda < 2; ronda += 1) {
        await asentar(fixture);
        http.match(() => true).forEach((p) => p.flush([]));
      }
      await asentar(fixture);

      expect(TestBed.inject(Router).url).toBe('/catalogo');
    });

    it('si los protocolos fallan, dice que el tipo sí se creó, para no intentar crearlo otra vez', async () => {
      await conCatalogoDeProtocolos();
      await agregarLimpieza();
      rellenarObligatorios();
      await enviar();

      http.expectOne({ method: 'POST', url: URL_TIPOS }).flush({ id: 't9' });
      await asentar(fixture);
      http
        .expectOne({ method: 'PATCH', url: URL_PROTOCOLOS_DE_TIPO('t9') })
        .flush({ code: 'ERR_EQUIPMENT_014' }, { status: 404, statusText: 'Not Found' });
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);

      expect(texto()).toContain('El tipo se creó, pero sus protocolos no se guardaron');
      expect(TestBed.inject(Router).url).not.toBe('/catalogo');
    });
  });

  it('dice cuales campos son opcionales, en vez de dejarlo para el momento de guardar', () => {
    expect(texto()).toContain('Datos opcionales');
    expect(texto()).toContain('ninguno impide guardar');
  });

  it('con solo los obligatorios, manda cuatro campos y ni uno mas', async () => {
    // Un vacio no es "no se sabe": mandar un uso en blanco o una modalidad vacia haria que el backend
    // guardara un dato inventado.
    rellenarObligatorios();
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });

    expect(alta.request.body).toEqual({
      nombre: 'Tensiómetro',
      tecnologiaPredominante: 'Electrónica',
      definicionTecnica: 'Mide presión arterial',
      recomendacionesCuidado: 'No golpear',
    });
    alta.flush({ id: 't9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('con los opcionales rellenos, los manda con su tipo', async () => {
    rellenarObligatorios();
    escribir('uso', ' Medir la presion arterial ');
    escribir('limpiezaCotidiana', 'Paño con alcohol al 70 %');
    escribir('valorUnitarioMantenimiento', '80000');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });

    expect(alta.request.body).toEqual({
      nombre: 'Tensiómetro',
      tecnologiaPredominante: 'Electrónica',
      definicionTecnica: 'Mide presión arterial',
      recomendacionesCuidado: 'No golpear',
      uso: 'Medir la presion arterial',
      limpiezaCotidiana: 'Paño con alcohol al 70 %',
      valorUnitarioMantenimiento: 80000,
    });
    alta.flush({ id: 't9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  describe('Que se le verifica', () => {
    /**
     * Despliega un panel y le pone magnitud y unidad.
     *
     * <p>Las unidades se consultan al elegir la magnitud, así que hay que responderlas: no están
     * cargadas de antemano porque dependen de lo elegido.
     */
    async function abrirPanel(magnitudId: string, unidadId: string): Promise<void> {
      escribirEn('#cuantasVerificaciones', '1');
      await asentar(fixture);
      escribirEn('#magnitud-1', magnitudId);
      await responderAUnidades();
      escribirEn('#unidad-1', unidadId);
    }

    async function responderAUnidades(): Promise<void> {
      for (const magnitud of Object.keys(UNIDADES)) {
        for (const peticion of http.match(URL_UNIDADES_DE(magnitud))) {
          peticion.flush(UNIDADES[magnitud]);
        }
      }

      await asentar(fixture);
    }

    function escribirEn(selector: string, valor: string): void {
      const control = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(selector)!;
      control.value = valor;
      control.dispatchEvent(new Event(control.tagName === 'SELECT' ? 'change' : 'input'));
      fixture.detectChanges();
    }

    it('el contador despliega un panel por cada cosa que se le mida', async () => {
      // Sin panel no hay nada: cero verificaciones significa que al tipo no se le verifica nada.
      expect(raiz().querySelector('#magnitud-1')).toBeNull();

      escribirEn('#cuantasVerificaciones', '2');
      await asentar(fixture);

      expect(raiz().querySelector('#magnitud-1')).toBeTruthy();
      expect(raiz().querySelector('#magnitud-2')).toBeTruthy();
      await responderAUnidades();
    });

    it('una modalidad constante pide cuantas lecturas y en que valores', async () => {
      await abrirPanel(ID_PRESION, ID_MMHG);

      // Con patron y equipo variables no hay nada constante que declarar, asi que los campos no estan.
      expect(raiz().querySelector('#cantidad-1')).toBeNull();

      escribirEn('#modalidad-1', 'PATRON_EQUIPO_VARIABLE');

      expect(raiz().querySelector('#cantidad-1')).toBeNull();

      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');

      expect(raiz().querySelector('#cantidad-1')).toBeTruthy();
      expect(raiz().querySelector('#cuantos-1')).toBeTruthy();
      // Y abre con un punto en blanco, porque hace falta al menos uno.
      expect(raiz().querySelector('#valor-1-0')).toBeTruthy();
    });

    it('dice que las lecturas son por punto, no en total', async () => {
      // Confundirlo daria un reporte con un tercio de los datos que hacian falta.
      await abrirPanel(ID_PRESION, ID_MMHG);
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');

      expect(texto()).toContain('En cada punto se toman estas lecturas');
    });

    it('manda la verificacion entera: magnitud, unidad, modalidad, cantidad y puntos', async () => {
      rellenarObligatorios();
      await abrirPanel(ID_PRESION, ID_MMHG);
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');
      escribirEn('#cantidad-1', '5');
      escribirEn('#valor-1-0', '100');
      await enviar();

      const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });

      expect(alta.request.body.verificaciones).toEqual([
        {
          magnitudId: ID_PRESION,
          unidadId: ID_MMHG,
          modalidad: 'PATRON_CONSTANTE',
          cantidadDatos: 5,
          // Solo el valor: la unidad la declara la verificacion, una sola vez.
          puntos: [{ valor: 100 }],
        },
      ]);
      alta.flush({ id: 't9' });
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('un punto negativo vale: un congelador se verifica a -20 grados', async () => {
      rellenarObligatorios();
      await abrirPanel(ID_TEMPERATURA, ID_CELSIUS);
      escribirEn('#modalidad-1', 'EQUIPO_CONSTANTE');
      escribirEn('#cantidad-1', '3');
      escribirEn('#valor-1-0', '-20');
      await enviar();

      const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });

      expect(alta.request.body.verificaciones[0].puntos).toEqual([{ valor: -20 }]);
      alta.flush({ id: 't9' });
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('sin puntos no llega al servidor, y dice por que hacen falta', async () => {
      rellenarObligatorios();
      await abrirPanel(ID_PRESION, ID_MMHG);
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');
      escribirEn('#cantidad-1', '3');
      // El punto que abre en blanco se queda sin rellenar.
      await enviar();

      http.expectNone({ method: 'POST', url: URL_TIPOS });
      expect(texto()).toContain('al menos un punto');
    });

    it('si la verificacion esta a medias y plegada, guardar la abre para que se vea el error', async () => {
      rellenarObligatorios();
      await abrirPanel(ID_PRESION, ID_MMHG);
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');
      escribirEn('#cantidad-1', '3');
      expect(raiz().querySelector<HTMLElement>('#seccionVerificacion')!.hidden).toBe(true);

      await enviar();

      expect(raiz().querySelector<HTMLElement>('#seccionVerificacion')!.hidden).toBe(false);
      expect(
        raiz().querySelector('[data-seccion="verificacion"]')!.getAttribute('aria-expanded'),
      ).toBe('true');
    });

    it('la cabecera dice cuantas magnitudes lleva, aun plegada', async () => {
      const cabecera = () => raiz().querySelector('[data-seccion="verificacion"]')!.textContent;
      expect(cabecera()).toContain('Sin verificación');

      await abrirPanel(ID_PRESION, ID_MMHG);
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');
      escribirEn('#cantidad-1', '3');
      await asentar(fixture);
      // A medias no cuenta: solo se cuentan las que se guardarian.
      expect(cabecera()).toContain('Sin verificación');

      escribirEn('#valor-1-0', '100');
      await asentar(fixture);

      expect(cabecera()).toContain('1 magnitud');
    });

    it('sin magnitud no llega al servidor', async () => {
      // El panel esta abierto pero vacio: el servidor lo rechazaria con un 400 que nadie sabria leer.
      rellenarObligatorios();
      escribirEn('#cuantasVerificaciones', '1');
      await asentar(fixture);
      await enviar();

      http.expectNone({ method: 'POST', url: URL_TIPOS });
      expect(texto()).toContain('Indique qué se mide');
      await responderAUnidades();
    });

    it('el mismo punto dos veces se avisa sin esperar a guardar', async () => {
      await abrirPanel(ID_PRESION, ID_MMHG);
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');
      escribirEn('#cantidad-1', '3');
      escribirEn('#cuantos-1', '2');
      escribirEn('#valor-1-0', '100');
      escribirEn('#valor-1-1', '100');

      expect(texto()).toContain('declarado dos veces');
    });

    it('cambiar a la modalidad variable borra lo que ya no significa nada', async () => {
      await abrirPanel(ID_PRESION, ID_MMHG);
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');
      escribirEn('#cantidad-1', '3');
      escribirEn('#valor-1-0', '100');

      escribirEn('#modalidad-1', 'PATRON_EQUIPO_VARIABLE');
      escribirEn('#modalidad-1', 'PATRON_CONSTANTE');

      expect(raiz().querySelector<HTMLInputElement>('#cantidad-1')!.value).toBe('');
      expect(raiz().querySelector<HTMLInputElement>('#valor-1-0')!.value).toBe('');
    });

    it('la unidad solo ofrece las de la magnitud elegida', async () => {
      // El esquema ata el par (magnitud, unidad) con una foranea compuesta: ofrecer °C para presion
      // seria ofrecer un 409.
      await abrirPanel(ID_PRESION, ID_MMHG);

      const unidades = [...raiz().querySelectorAll('#unidad-1 option')]
        .map((o) => o.textContent?.trim())
        .join(' ');

      expect(unidades).toContain('mmHg');
      expect(unidades).not.toContain('°C');
    });
  });

  it('sin los obligatorios no llega al servidor, y se dice una vez', async () => {
    await enviar();

    http.expectNone({ method: 'POST', url: URL_TIPOS });
    expect(texto()).toContain('son obligatorios');
  });

  it('las cotas salen del contrato: 50 en nombre, 250 en los textos largos', () => {
    // La tecnologia no lleva cota y no hace falta: al ser lista cerrada, no se puede escribir un valor
    // que no exista, y todos los autorizados caben.
    expect(raiz().querySelector<HTMLInputElement>('#nombre')!.maxLength).toBe(50);
    expect(raiz().querySelector<HTMLTextAreaElement>('#definicionTecnica')!.maxLength).toBe(250);
    expect(raiz().querySelector<HTMLTextAreaElement>('#recomendacionesCuidado')!.maxLength).toBe(250);
  });

  describe('La tecnología es una lista cerrada', () => {
    it('ofrece lo que la empresa ya usa antes que las autorizadas de partida', async () => {
      // El tipo registrado en la prueba usa «Electrónica»: es la grafia que agrupa con lo que hay.
      const campo = raiz().querySelector<HTMLInputElement>('#tecnologiaPredominante')!;
      campo.dispatchEvent(new Event('focus'));
      campo.value = 'electr';
      campo.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      await asentar(fixture);

      const opciones = [
        ...raiz().querySelectorAll('#tecnologiaPredominante-lista [role="option"]'),
      ].map((o) => o.textContent?.trim());

      expect(opciones[0]).toBe('Electrónica');
      expect(opciones).toContain('Electromecánica');
    });

    it('no deja poner una tecnología que no esté autorizada', async () => {
      // Es una lista cerrada por decision del usuario: con texto libre, la misma tecnologia acaba
      // escrita de cuatro maneras y agrupar por ella deja de servir. El backend aceptaria cualquier
      // cosa, asi que la restriccion tiene que estar aqui.
      escribir('nombre', 'Bomba de infusión');
      escribir('tecnologiaPredominante', 'Peristáltica');
      escribir('definicionTecnica', 'Infunde a caudal constante');
      escribir('recomendacionesCuidado', 'Revisar el equipo de infusión');
      await enviar();

      http.expectNone({ method: 'POST', url: URL_TIPOS });
      expect(texto()).toContain('No hay ninguna opción con ese nombre');
    });

    it('una tecnología que la empresa ya usa está autorizada, aunque no sea de la lista de partida', async () => {
      // «Peristáltica» NO esta entre las autorizadas de partida: solo cuenta porque ya hay un tipo
      // registrado con ella. Sin esto, abrir un tipo antiguo dejaria el campo en blanco y el
      // formulario invalido sin explicar nada.
      //
      // Y la prueba usa a proposito un valor que NO esta en la lista de partida: con «Electrónica»
      // habria pasado igual de las dos formas, que es una prueba que no mira nada.
      await remontar([{ ...TIPOS[0], id: 't5', tecnologiaPredominante: 'Peristáltica' }]);

      escribir('nombre', 'Bomba de infusión');
      escribir('tecnologiaPredominante', 'Peristáltica');
      escribir('definicionTecnica', 'Infunde a caudal constante');
      escribir('recomendacionesCuidado', 'Revisar el equipo de infusión');
      await enviar();

      const alta = http.expectOne({ method: 'POST', url: URL_TIPOS });

      expect(alta.request.body.tecnologiaPredominante).toBe('Peristáltica');
      alta.flush({ id: 't9' });
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('elegir una sugerencia la escribe tal cual', async () => {
      await elegirEnBuscador(fixture, 'tecnologiaPredominante', 'Neumática');

      expect(raiz().querySelector<HTMLInputElement>('#tecnologiaPredominante')!.value).toBe(
        'Neumática',
      );
    });
  });

  it('al guardar vuelve al catalogo', async () => {
    rellenarObligatorios();
    await enviar();
    http.expectOne({ method: 'POST', url: URL_TIPOS }).flush({ id: 't9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(TestBed.inject(Router).url).toBe('/catalogo');
  });

  afterEach(() => {
    // El try/finally no es adorno: si verify() lanza por una peticion abierta, sin el no se
    // desinstalaria el doble de localStorage y el fichero SIGUIENTE heredaria lo que este guardo. Es
    // la variante con contaminacion del fallo que este proyecto ya pago una vez -- un fallo en
    // afterEach se lee como ochenta y seis -- y asi el rojo se queda donde ocurrio.
    try {
      http.verify();
    } finally {
      desinstalarAlmacenamiento();
    }
  });
});
