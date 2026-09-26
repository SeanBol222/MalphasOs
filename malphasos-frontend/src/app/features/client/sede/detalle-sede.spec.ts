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

const ENCARGADOS = [
  { idPersona: 'p1', idSede: ID, tipo: 'HEADQUARTER', estadoActivo: true },
  { idPersona: 'p2', idAreaServicio: 'a1', tipo: 'SERVICE_AREA', estadoActivo: true },
  // De otra sede: esta pantalla no debe ensenarlo, y el API no ofrece filtrar por sede.
  { idPersona: 'p3', idSede: 'otra-sede', tipo: 'HEADQUARTER', estadoActivo: true },
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
const AUTORIDADES = ['client.read', 'client.write', 'engineer.read', 'engineer.assign', 'person.read'];

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
    await responderA(fixture, http, URL_CIUDADES, { code: 'ERR_LOCATION_002' }, {
      status: 403,
      statusText: 'Forbidden',
    });
    await responderA(fixture, http, URL_AREAS, AREAS);
    await responderA(fixture, http, URL_ENCARGADOS, ENCARGADOS);
    await responderA(fixture, http, URL_PERSONAS, PERSONAS);

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

      expect(TestBed.inject(Router).url).toBe(`/clientes/${ID_CLIENTE}`);
    });

    it('una sede ya cerrada no ofrece cerrarse otra vez', async () => {
      await abrir({ ...SEDE, estadoActivo: false });

      expect([...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim())).not.toContain(
        'Cerrar sede',
      );
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

    it('quitar a un encargado usa el identificador de la persona, que es su ruta', async () => {
      await abrir();
      const quitar = [...raiz().querySelectorAll('button')].filter(
        (b) => b.textContent?.trim() === 'Quitar',
      );
      quitar[0].click();
      await asentar(fixture);

      http.expectOne({ method: 'DELETE', url: `${URL_ENCARGADOS}/p1` }).flush(null);
      await asentar(fixture);
      await atenderRefresco(fixture, http, URL_ENCARGADOS, ENCARGADOS);
      await atenderRefresco(fixture, http, URL_PERSONAS, PERSONAS);
    });

    it('si un nombre no se pudo consultar, lo dice en vez de inventarselo', async () => {
      fixture.detectChanges();
      await responderA(fixture, http, URL, SEDE);
      await responderA(fixture, http, URL_CIUDADES, CIUDADES);
      await responderA(fixture, http, URL_AREAS, AREAS);
      await responderA(fixture, http, URL_ENCARGADOS, ENCARGADOS);
      // Sin person.read el backend responde 403, y es el caso real del grupo clients.
      await responderA(fixture, http, URL_PERSONAS, { code: 'ERR_PERSON_001' }, {
        status: 403,
        statusText: 'Forbidden',
      });

      expect(texto()).toContain('no se pudo consultar');
    });

    describe('El frontend oculta, no autoriza', () => {
      it('sin engineer.read la seccion no se ensena', async () => {
        // Es el caso del grupo clients, que no tiene ninguna de las dos autoridades.
        TestBed.resetTestingModule();
        montar(['client.read']);
        fixture.detectChanges();
        await responderA(fixture, http, URL, SEDE);
        await responderA(fixture, http, URL_CIUDADES, CIUDADES);
        await responderA(fixture, http, URL_AREAS, AREAS);
        await responderA(fixture, http, URL_ENCARGADOS, ENCARGADOS);
        await responderA(fixture, http, URL_PERSONAS, PERSONAS);

        expect(texto()).not.toContain('Encargados');
      });

      it('con engineer.read pero sin engineer.assign se ven y no se tocan', async () => {
        // Es el caso del grupo engineers: consulta quien es el responsable, no lo cambia.
        TestBed.resetTestingModule();
        montar(['client.read', 'engineer.read', 'person.read']);
        await abrir();

        expect(texto()).toContain('Ana María Ruiz');
        expect([...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim())).not.toContain(
          'Quitar',
        );
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

  it('el estado se distingue por peso tipografico y no por un color nuevo', async () => {
    await abrir({ ...SEDE, estadoActivo: false });

    const estado = [...raiz().querySelectorAll('p')].find((p) =>
      (p.textContent ?? '').includes('Cerrada'),
    )!;

    expect(estado.className).not.toContain('font-semibold');
    expect(estado.className).not.toContain('text-accent');
  });

  afterEach(() => http.verify());
});
