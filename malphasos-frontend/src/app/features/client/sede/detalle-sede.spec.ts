import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, atenderRefresco, responderA } from '../../../../testing/pantalla';
import { DetalleSede } from './detalle-sede';

const ID = 's1';
const ID_CLIENTE = '11111111-1111-1111-1111-111111111111';
const ID_CIUDAD = '22222222-2222-2222-2222-222222222222';

const URL = `http://localhost:8081/v1/api/headquarters/${ID}`;
const URL_CIUDADES = 'http://localhost:8081/v1/api/cities';
const URL_AREAS = `${URL}/service-areas`;
const URL_AREA = 'http://localhost:8081/v1/api/service-areas';
const URL_ENCARGADOS = 'http://localhost:8081/v1/api/managers';
const URL_PERSONAS = 'http://localhost:8081/v1/api/persons';
const URL_CLIENTE = `http://localhost:8081/v1/api/clients/${ID_CLIENTE}`;
const CLIENTE = { id: ID_CLIENTE, razonSocial: 'Hospital Central', estadoActivo: true };

const SEDE = {
  id: ID,
  nombre: 'Sede Norte',
  idCliente: ID_CLIENTE,
  idCiudad: ID_CIUDAD,
  calle: '100',
  carrera: '15',
  numero: '20-30',
  estadoActivo: true,
};

const CIUDADES = [{ id: ID_CIUDAD, nombre: 'Bogotá', idPais: 'co', estadoActivo: true }];

const AREAS = [
  { id: 'a1', nombre: 'Urgencias', idSede: ID, estadoActivo: true },
  { id: 'a2', nombre: 'Bodega vieja', idSede: ID, estadoActivo: false },
];

// Desde el 2026-10-27 (V41) cada encargado trae lo que tiene a cargo en dos listas.
const ENCARGADOS = [
  { idPersona: 'p1', idsSede: [ID], idsAreaServicio: [], estadoActivo: true },
  { idPersona: 'p2', idsSede: [], idsAreaServicio: ['a1'], estadoActivo: true },
  // De otra sede: esta pantalla no debe ensenarlo, y el API no ofrece filtrar por sede.
  { idPersona: 'p3', idsSede: ['otra-sede'], idsAreaServicio: [], estadoActivo: true },
];

const PERSONAS = [
  {
    identificador: 'p1',
    primerNombre: 'Ana',
    segundoNombre: 'María',
    primerApellido: 'Ruiz',
    cedula: '123',
    tipoPersona: 'MANAGER',
    estadoActivo: true,
  },
  {
    identificador: 'p2',
    primerNombre: 'Luis',
    primerApellido: 'Peña',
    cedula: '456',
    tipoPersona: 'MANAGER',
    estadoActivo: true,
  },
];

/** Lo que un administrador trae y hace visible toda la pantalla. */
const AUTORIDADES = [
  'client.read',
  'client.write',
  // Las áreas se editan con la suya desde el 2026-10-26, como en el servidor.
  'service-area.write',
  'engineer.read',
  'engineer.assign',
  'person.read',
  'location.read',
];

@Component({ selector: 'app-ficha-cliente-falsa', template: '' })
class FichaClienteFalsa {}

describe('Ficha de una sede', () => {
  let fixture: ComponentFixture<DetalleSede>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[] = AUTORIDADES): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([{ path: 'clientes/:id', component: FichaClienteFalsa }]),
      ],
    });
    fixture = TestBed.createComponent(DetalleSede);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID);
  }

  beforeEach(() => montar());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(
    sede: object = SEDE,
    opciones?: { status: number; statusText: string },
  ): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL, sede, opciones);
    await responderA(fixture, http, URL_CIUDADES, CIUDADES);
    // Las areas se piden siempre, tambien cuando la sede falla: son dos consultas independientes y
    // la de las areas sale al construir la pantalla, no al llegar la sede.
    await responderA(fixture, http, URL_AREAS, AREAS);
    await responderA(fixture, http, URL_ENCARGADOS, ENCARGADOS);
    await responderA(fixture, http, URL_PERSONAS, PERSONAS);
    // El cliente de la sede, para la línea de arriba: se pide cuando la sede llega.
    http.match(URL_CLIENTE).forEach((p) => p.flush(CLIENTE));
    await asentar(fixture);
  }

  function pulsar(etiqueta: string): void {
    const boton = [...raiz().querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes(etiqueta),
    );

    if (!boton) {
      throw new Error(`No hay ningun boton que diga "${etiqueta}"`);
    }

    boton.click();
    fixture.detectChanges();
  }

  it('ensena el nombre, la ciudad en palabras y la direccion legible', async () => {
    await abrir();

    expect(texto()).toContain('Sede Norte');
    expect(texto()).toContain('Bogotá');
    expect(texto()).toContain('Calle 100');
    expect(texto()).toContain('Carrera 15');
    // El identificador de la ciudad no se ve: un UUID en pantalla es una fuga de la base de datos.
    expect(texto()).not.toContain(ID_CIUDAD);
  });

  it('si el catalogo de ciudades no se pudo consultar, lo dice en vez de dejarlo en blanco', async () => {
    fixture.detectChanges();
    await responderA(fixture, http, URL, SEDE);
    await responderA(
      fixture,
      http,
      URL_CIUDADES,
      { code: 'ERR_LOCATION_002' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );
    await responderA(fixture, http, URL_AREAS, AREAS);
    await responderA(fixture, http, URL_ENCARGADOS, ENCARGADOS);
    await responderA(fixture, http, URL_PERSONAS, PERSONAS);
    http.match(URL_CLIENTE).forEach((p) => p.flush(CLIENTE));
    await asentar(fixture);

    expect(texto()).toContain('No disponible');
  });

  it('vuelve al cliente del que es, sin que la ruta tenga que arrastrarlo', async () => {
    await abrir();

    const enlaces = [...raiz().querySelectorAll('a')].map((a) => a.getAttribute('href'));

    expect(enlaces).toContain(`/clientes/${ID_CLIENTE}`);
  });

  describe('Cerrar una sede no es un clic', () => {
    it('el primer clic pide confirmacion y avisa de la consecuencia real', async () => {
      await abrir();
      pulsar('Cerrar sede');

      http.expectNone({ method: 'DELETE', url: URL });
      // La consecuencia no es cosmetica: el backend no deja abrir areas en una sede cerrada.
      expect(texto()).toContain('no se pueden abrir áreas');
    });

    it('confirmar cierra y vuelve a la ficha del cliente', async () => {
      await abrir();
      pulsar('Cerrar sede');
      pulsar('Confirmar cierre');
      await asentar(fixture);

      http.expectOne({ method: 'DELETE', url: URL }).flush(null);
      await asentar(fixture);
      await atenderRefresco(fixture, http, URL, SEDE);
      await atenderRefresco(fixture, http, URL_AREAS, AREAS);
      // Cerrar una sede caduca también su cliente, que esta ficha nombra desde el rediseño.
      await atenderRefresco(fixture, http, URL_CLIENTE, CLIENTE);

      expect(TestBed.inject(Router).url).toBe(`/clientes/${ID_CLIENTE}`);
    });

    it('una sede ya cerrada no ofrece cerrarse otra vez', async () => {
      await abrir({ ...SEDE, estadoActivo: false });

      expect(
        [...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim()),
      ).not.toContain('Cerrar sede');
    });
  });

  describe('Sus encargados', () => {
    it('ensena los de la sede y los de sus areas, con nombre y con lo que tienen a cargo', async () => {
      // El API devuelve solo identificadores: sin resolver el nombre contra el modulo de personas,
      // esta lista seria una columna de UUID.
      await abrir();

      expect(texto()).toContain('Ana María Ruiz');
      expect(texto()).toContain('Toda la sede');
      expect(texto()).toContain('Luis Peña');
      expect(texto()).toContain('Área: Urgencias');
    });

    it('no ensena los encargados de otras sedes, que vienen en la misma lista', async () => {
      // El API no ofrece filtrar por sede: el recorte lo hace la pantalla, y hay que comprobarlo.
      await abrir();

      const encargados = [...raiz().querySelectorAll('section ul li')].map((li) => li.textContent);

      expect(encargados.filter((t) => t?.includes('Toda la sede'))).toHaveLength(1);
    });

    it('quitar a un encargado de la sede lo releva de la sede, y solo de ella', async () => {
      await abrir();
      const quitar = [...raiz().querySelectorAll('button')].filter(
        (b) => b.textContent?.trim() === 'Quitar',
      );
      quitar[0].click();
      await asentar(fixture);

      // Desde V41 no se retira a la persona entera: tiene otras cosas a cargo.
      http
        .expectOne({ method: 'DELETE', url: `${URL_ENCARGADOS}/p1/headquarters/${ID}` })
        .flush(null);
      await asentar(fixture);
      await atenderRefresco(fixture, http, URL_ENCARGADOS, ENCARGADOS);
      await atenderRefresco(fixture, http, URL_PERSONAS, PERSONAS);
    });

    it('quien esta a cargo de la sede y de un area sale dos veces, y se quita de cada una aparte', async () => {
      TestBed.resetTestingModule();
      montar();
      fixture.detectChanges();
      await responderA(fixture, http, URL, SEDE);
      await responderA(fixture, http, URL_CIUDADES, CIUDADES);
      await responderA(fixture, http, URL_AREAS, AREAS);
      await responderA(fixture, http, URL_ENCARGADOS, [
        { idPersona: 'p1', idsSede: [ID], idsAreaServicio: ['a1'], estadoActivo: true },
      ]);
      await responderA(fixture, http, URL_PERSONAS, PERSONAS);
      http.match(URL_CLIENTE).forEach((p) => p.flush(CLIENTE));
      await asentar(fixture);

      const quitar = [...raiz().querySelectorAll<HTMLButtonElement>('button')].filter(
        (b) => b.textContent?.trim() === 'Quitar',
      );
      expect(quitar.map((b) => b.getAttribute('aria-label'))).toEqual([
        'Quitar a Ana María Ruiz de Toda la sede',
        'Quitar a Ana María Ruiz de Área: Urgencias',
      ]);

      quitar[1].click();
      await asentar(fixture);

      http
        .expectOne({ method: 'DELETE', url: `${URL_ENCARGADOS}/p1/service-areas/a1` })
        .flush(null);
      await asentar(fixture);
      http.match(URL_ENCARGADOS).forEach((p) => p.flush([]));
      http.match(URL_PERSONAS).forEach((p) => p.flush(PERSONAS));
      await asentar(fixture);
    });

    it('si un nombre no se pudo consultar, lo dice en vez de inventarselo', async () => {
      fixture.detectChanges();
      await responderA(fixture, http, URL, SEDE);
      await responderA(fixture, http, URL_CIUDADES, CIUDADES);
      await responderA(fixture, http, URL_AREAS, AREAS);
      await responderA(fixture, http, URL_ENCARGADOS, ENCARGADOS);
      // Sin person.read el backend responde 403, y es el caso real del grupo clients.
      await responderA(
        fixture,
        http,
        URL_PERSONAS,
        { code: 'ERR_PERSON_001' },
        {
          status: 403,
          statusText: 'Forbidden',
        },
      );

      expect(texto()).toContain('no se pudo consultar');
    });

    describe('El frontend oculta, no autoriza', () => {
      it('sin engineer.read la seccion no se ensena', async () => {
        // Es el caso del grupo clients, que no tiene ninguna de las dos autoridades.
        TestBed.resetTestingModule();
        montar(['client.read']);
        fixture.detectChanges();
        await responderA(fixture, http, URL, SEDE);
        await responderA(fixture, http, URL_AREAS, AREAS);

        expect(texto()).not.toContain('Encargado');
        // Y no se piden: desde el rediseño, sin la autoridad la consulta ni sale.
        expect(http.match(URL_ENCARGADOS)).toHaveLength(0);
        expect(http.match(URL_PERSONAS)).toHaveLength(0);
        expect(http.match(URL_CIUDADES)).toHaveLength(0);
      });

      it('con engineer.read pero sin engineer.assign se ven y no se tocan', async () => {
        // Es el caso del grupo engineers: consulta quien es el responsable, no lo cambia.
        TestBed.resetTestingModule();
        montar(['client.read', 'engineer.read', 'person.read', 'location.read']);
        await abrir();

        expect(texto()).toContain('Ana María Ruiz');
        expect(
          [...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim()),
        ).not.toContain('Quitar');
        expect([...raiz().querySelectorAll('a')].map((a) => a.textContent?.trim())).not.toContain(
          'Registrar encargado',
        );
      });
    });
  });

  describe('Sus areas de servicio', () => {
    it('las lista, y dice para que sirven', async () => {
      await abrir();

      expect(texto()).toContain('Urgencias');
      // Que los equipos cuelgan del area y no de la sede no es evidente, y de ello depende que
      // alguien entienda por que tiene que crear un area antes de registrar un equipo.
      expect(texto()).toContain('Los equipos se registran en un área');
    });

    it('anadir un area manda el nombre y refresca la lista', async () => {
      await abrir();

      const campo = raiz().querySelector<HTMLInputElement>('#areaNueva')!;
      campo.value = 'Quirófanos';
      campo.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      pulsar('Añadir');
      await asentar(fixture);

      const alta = http.expectOne({ method: 'POST', url: URL_AREAS });

      expect(alta.request.body).toEqual({ nombre: 'Quirófanos' });
      alta.flush({ id: 'a3' });
      await asentar(fixture);
      await atenderRefresco(fixture, http, URL, SEDE);
      await atenderRefresco(fixture, http, URL_AREAS, AREAS);
    });

    it('un area sin nombre no llega al servidor', async () => {
      await abrir();
      pulsar('Añadir');
      await asentar(fixture);

      http.expectNone({ method: 'POST', url: URL_AREAS });
      expect(texto()).toContain('El nombre del área es obligatorio');
    });

    it('renombrar un area manda un PATCH a su propia ruta', async () => {
      await abrir();
      pulsar('Renombrar');

      const campo = raiz().querySelector<HTMLInputElement>('#area-a1')!;

      // Llega con el nombre actual puesto: renombrar no es escribirlo de nuevo desde cero.
      expect(campo.value).toBe('Urgencias');

      campo.value = 'Urgencias adultos';
      campo.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      pulsar('Guardar');
      await asentar(fixture);

      const cambio = http.expectOne({ method: 'PATCH', url: `${URL_AREA}/a1` });

      expect(cambio.request.body).toEqual({ nombre: 'Urgencias adultos' });
      cambio.flush({ id: 'a1' });
      await asentar(fixture);
      await atenderRefresco(fixture, http, URL, SEDE);
      await atenderRefresco(fixture, http, URL_AREAS, AREAS);
    });

    it('cerrar un area llama a su propia ruta', async () => {
      await abrir();
      // Solo el area abierta ofrece cerrarse; la cerrada ya no.
      const cerrar = [...raiz().querySelectorAll('button')].filter(
        (b) => b.textContent?.trim() === 'Cerrar',
      );

      expect(cerrar).toHaveLength(1);

      cerrar[0].click();
      await asentar(fixture);

      http.expectOne({ method: 'DELETE', url: `${URL_AREA}/a1` }).flush(null);
      await asentar(fixture);
      await atenderRefresco(fixture, http, URL, SEDE);
      await atenderRefresco(fixture, http, URL_AREAS, AREAS);
    });

    it('en una sede cerrada no se ofrece anadir areas, porque el backend lo rechaza', async () => {
      // No es una limitacion de la pantalla: la regla es del servidor, y ofrecer el formulario
      // prometeria algo que el servidor niega.
      await abrir({ ...SEDE, estadoActivo: false });

      expect(raiz().querySelector('#areaNueva')).toBeNull();
      expect(texto()).toContain('no se pueden abrir áreas nuevas');
    });
  });

  it('un fallo del API se ensena traducido, con el codigo real de la sede', async () => {
    await abrir(
      { code: 'ERR_CLIENT_002', message: 'Headquarter not found', details: [] },
      { status: 404, statusText: 'Not Found' },
    );

    const aviso = raiz().querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Esa sede no existe');
    expect(aviso?.textContent).not.toContain('not found');
  });

  describe('Quien solo lee, como el representante del cliente (2026-10-26)', () => {
    // No se le ofrece lo que el servidor le rechazaría: la sede pide client.write, y sus áreas,
    // service-area.write.
    const botones = () => [...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim());

    async function abrirCon(autoridades: readonly string[], sede: object = SEDE): Promise<void> {
      TestBed.resetTestingModule();
      montar(autoridades);
      fixture.detectChanges();
      await responderA(fixture, http, URL, sede);
      await responderA(fixture, http, URL_AREAS, AREAS);
      http.match(URL_CLIENTE).forEach((p) => p.flush(CLIENTE));
      await asentar(fixture);
    }

    it('no tiene Editar ni Cerrar sede', async () => {
      await abrirCon(['client.read']);

      expect(raiz().querySelector(`a[href="/sedes/${ID}/editar"]`)).toBeNull();
      expect(botones()).not.toContain('Cerrar sede');
    });

    it('ve las áreas y no las toca: ni renombrar, ni cerrar, ni añadir', async () => {
      await abrirCon(['client.read']);

      expect(texto()).toContain(AREAS[0].nombre);
      expect(botones()).not.toContain('Renombrar');
      expect(botones()).not.toContain('Cerrar');
      expect(botones()).not.toContain('Añadir área');
      expect(raiz().querySelector('#areaNueva')).toBeNull();
    });

    it('en una sede cerrada no le explica por qué no puede abrir áreas: no podría de todos modos', async () => {
      await abrirCon(['client.read'], { ...SEDE, estadoActivo: false });

      expect(texto()).not.toContain('no se pueden abrir áreas nuevas');
    });

    it('quien edita las áreas sin editar la sede toca las áreas y no la sede', async () => {
      await abrirCon(['client.read', 'service-area.write']);

      expect(botones()).toContain('Renombrar');
      expect(raiz().querySelector('#areaNueva')).not.toBeNull();
      expect(raiz().querySelector(`a[href="/sedes/${ID}/editar"]`)).toBeNull();
      expect(botones()).not.toContain('Cerrar sede');
    });
  });

  it('el estado se dice en palabras, con el mismo color abierta y cerrada', async () => {
    await abrir({ ...SEDE, estadoActivo: false });
    const cerrada = raiz().querySelector('header p')!;

    expect(cerrada.textContent).toContain('Sede cerrada');
    const clases = cerrada.className;
    http.match(URL_CLIENTE).forEach((p) => p.flush(CLIENTE));
    TestBed.resetTestingModule();
    montar();
    await abrir();
    expect(raiz().querySelector('header p')!.className).toBe(clases);
  });

  describe('Lo que trajo el rediseño (2026-10-11)', () => {
    const URL_CONTEOS = 'http://localhost:8081/v1/api/client-equipments/summary';
    const URL_ORDENES = 'http://localhost:8081/v1/api/work-orders';
    const TODO = [
      ...AUTORIDADES,
      'equipment.read',
      'equipment.assign',
      'work-order.read',
      'work-order.write',
    ];

    async function abrirConTodo(): Promise<void> {
      TestBed.resetTestingModule();
      montar(TODO);
      await abrir();
      const conteos = http.match((r) => r.url === URL_CONTEOS);
      // Los conteos se piden por el cliente de la sede, que llega con ella.
      expect(conteos.map((r) => r.request.params.get('idCliente'))).toEqual([ID_CLIENTE]);
      conteos.forEach((p) =>
        p.flush([
          { idArea: 'a1', idSede: ID, idCliente: ID_CLIENTE, equipos: 4, fueraDeServicio: 0 },
        ]),
      );
      http.match(URL_ORDENES).forEach((p) =>
        p.flush([
          {
            id: 'o1',
            idSede: ID,
            estadoActivo: true,
            estadoEjecucion: 'EN_EJECUCION',
            fechaMantenimiento: '2026-10-06',
          },
          {
            id: 'o2',
            idSede: ID,
            estadoActivo: true,
            estadoEjecucion: 'EJECUTADA',
            fechaMantenimiento: '2026-09-01',
          },
          {
            id: 'o3',
            idSede: ID,
            estadoActivo: false,
            estadoEjecucion: 'CREADA',
            fechaMantenimiento: '2026-11-01',
          },
          {
            id: 'o4',
            idSede: 'otra',
            estadoActivo: true,
            estadoEjecucion: 'CREADA',
            fechaMantenimiento: '2026-11-01',
          },
        ]),
      );
      await asentar(fixture);
    }

    const fila = (id: string) => raiz().querySelector(`[data-area="${id}"]`)!;
    const celdas = (id: string) =>
      [...fila(id).querySelectorAll('td')].map((td) => td.textContent?.replace(/\s+/g, ' ').trim());

    it('nombra al cliente arriba, enlazado a su ficha', async () => {
      await abrir();

      const enlace = raiz().querySelector('header p a');
      expect(enlace?.textContent?.trim()).toBe('Hospital Central');
      expect(enlace?.getAttribute('href')).toBe(`/clientes/${ID_CLIENTE}`);
    });

    it('cada área dice sus equipos y su encargado; sin uno propio, el de la sede, y se nota', async () => {
      await abrirConTodo();

      expect(celdas('a1').slice(0, 3)).toEqual(['Urgencias', '4', 'Luis Peña']);
      expect(celdas('a2').slice(0, 3)).toEqual([
        'Bodega vieja Cerrada',
        '0',
        'De la sede: Ana María Ruiz',
      ]);
      expect(fila('a2').querySelectorAll('td')[2].className).toContain('text-neutral-700');
    });

    it('registrar un equipo se ofrece en las áreas abiertas, y solo con equipment.assign', async () => {
      await abrirConTodo();

      expect(fila('a1').querySelector('a[href="/areas/a1/equipos/nuevo"]')).not.toBeNull();
      expect(fila('a2').querySelector('a[href="/areas/a2/equipos/nuevo"]')).toBeNull();
    });

    it('cuenta las órdenes abiertas de esta sede: ni ejecutadas, ni anuladas, ni de otra', async () => {
      await abrirConTodo();

      const bloque = raiz().querySelector('section[aria-labelledby="ordenes-sede"]')!;
      expect(bloque.querySelector('p')?.textContent?.trim()).toBe('1');
      expect([...bloque.querySelectorAll('a')].map((a) => a.getAttribute('href'))).toEqual([
        '/ordenes/o1',
      ]);
    });

    it('«Nueva orden aquí» lleva al alta con el cliente y la sede puestos', async () => {
      await abrirConTodo();

      const enlace = [...raiz().querySelectorAll('a')].find(
        (a) => a.textContent?.trim() === 'Nueva orden aquí',
      );
      expect(enlace?.getAttribute('href')).toBe(`/ordenes/nueva?cliente=${ID_CLIENTE}&sede=${ID}`);
    });

    it('sin work-order.* ni equipment.* no pide ni pinta órdenes, conteos ni el alta', async () => {
      await abrir();

      expect(http.match(URL_ORDENES)).toHaveLength(0);
      expect(http.match((r) => r.url === URL_CONTEOS)).toHaveLength(0);
      expect(raiz().querySelector('section[aria-labelledby="ordenes-sede"]')).toBeNull();
      expect(texto()).not.toContain('Nueva orden aquí');
      expect(texto()).not.toContain('Registrar equipo');
    });
  });

  afterEach(() => {
    // El nombre del cliente se pide cuando llega la sede y casi ninguna prueba lo mira: se responde
    // aqui para no repetirlo en cada una. Lo que si importa de el tiene su propia prueba.
    http.match(URL_CLIENTE).forEach((p) => p.flush(CLIENTE));
    http.verify();
  });
});
