import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { DetalleCliente } from './detalle-cliente';

const ID = '11111111-1111-1111-1111-111111111111';
const URL = `http://localhost:8081/v1/api/clients/${ID}`;
const URL_PAISES = 'http://localhost:8081/v1/api/countries';
const URL_SEDES = `${URL}/headquarters`;
const API = 'http://localhost:8081/v1/api';
const URL_RESUMEN_SEDES = `${API}/headquarters/summary`;
const URL_CONTEOS = `${API}/client-equipments/summary`;
const URL_PERSONAS = `${API}/persons`;
const URL_CIUDADES = `${API}/cities`;

/** Lo que dice el resumen de sedes: la cerrada no cuenta en las cifras. */
const RESUMEN_SEDES = [
  {
    id: 's1',
    nombre: 'Sede Norte',
    idCliente: ID,
    idCiudad: 'ci1',
    abierta: true,
    areas: [
      { id: 'a1', nombre: 'Urgencias', abierta: true },
      { id: 'a2', nombre: 'Laboratorio', abierta: true },
      { id: 'a3', nombre: 'Bodega', abierta: false },
    ],
  },
  {
    id: 's2',
    nombre: 'Sede Vieja',
    idCliente: ID,
    abierta: false,
    areas: [{ id: 'a4', nombre: 'Vieja', abierta: true }],
  },
];
const CONTEOS = [
  {
    idArea: 'a1',
    idSede: 's1',
    idCliente: ID,
    equipos: 5,
    fueraDeServicio: 1,
    ultimoServicio: '2026-10-06T21:30:00',
  },
  { idArea: 'a2', idSede: 's1', idCliente: ID, equipos: 3, fueraDeServicio: 0 },
];

/** Un identificador de verdad, no un alias corto: es lo que el usuario NO debe ver en pantalla. */
const ID_PAIS = '22222222-2222-2222-2222-222222222222';

const CLIENTE = {
  id: ID,
  razonSocial: 'Hospital Central',
  documento: '900123456',
  tipoIdentificacion: 'NIT_JURIDICO',
  idPais: ID_PAIS,
  estadoActivo: true,
  correos: [{ id: 'c1', valor: 'contacto@hospital.co', estadoActivo: true }],
  telefonos: [{ id: 't1', valor: '6011234567', estadoActivo: true }],
  supervisores: [{ idPersona: 'p1', cargo: 'Ingeniera biomédica' }, { idPersona: 'p2' }],
};

const PAISES = [{ id: ID_PAIS, nombre: 'Colombia', codigoIso: 'CO', estadoActivo: true }];

// La cerrada primero: la ficha pone las abiertas delante, y con el orden ya bueno no se notaría.
const SEDES = [
  {
    id: 's2',
    nombre: 'Sede Vieja',
    idCliente: ID,
    calle: '1',
    carrera: '2',
    numero: '3',
    estadoActivo: false,
  },
  {
    id: 's1',
    nombre: 'Sede Norte',
    idCliente: ID,
    idCiudad: 'ci1',
    calle: '10',
    carrera: '20',
    numero: '30',
    estadoActivo: true,
  },
];

@Component({ selector: 'app-listado-falso', template: '' })
class ListadoFalso {}

describe('Ficha de un cliente', () => {
  let fixture: ComponentFixture<DetalleCliente>;
  let http: HttpTestingController;

  /** Solo client.*: lo que piden las autoridades de más se prueba aparte, en «Las cifras». */
  function montar(
    autoridades: readonly string[] = ['client.read', 'client.write', 'client.delete'],
  ): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([{ path: 'clientes', component: ListadoFalso }]),
      ],
    });
    fixture = TestBed.createComponent(DetalleCliente);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID);
  }

  beforeEach(() => montar());

  /** Responde lo que la invalidacion de una escritura vuelve a pedir: la ficha, sus sedes y el resumen. */
  async function atenderRecargas(): Promise<void> {
    http.match(URL).forEach((p) => p.flush(CLIENTE));
    http.match(URL_SEDES).forEach((p) => p.flush(SEDES));
    http.match((r) => r.url === URL_RESUMEN_SEDES).forEach((p) => p.flush(RESUMEN_SEDES));
    await asentar();
  }

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  /**
   * Deja pasar tics hasta que las senales lleguen al DOM.
   *
   * <p><b>Sin {@code whenStable()} a proposito.</b> La aplicacion es zoneless y una peticion HTTP sin
   * responder cuenta como tarea pendiente, de modo que esperar la estabilidad mientras hay una en
   * vuelo no termina nunca: la prueba agota su tiempo en vez de fallar diciendo algo. Un tic vacio
   * basta para que las promesas de la consulta se resuelvan.
   */
  async function asentar(): Promise<void> {
    for (let i = 0; i < 20; i += 1) {
      fixture.detectChanges();
      await new Promise((seguir) => setTimeout(seguir, 0));
    }
    fixture.detectChanges();
  }

  /** Abre la pantalla y responde a sus dos consultas: el cliente y el catálogo de países. */
  async function abrir(
    cliente: object = CLIENTE,
    opciones?: { status: number; statusText: string },
  ): Promise<void> {
    fixture.detectChanges();
    await responder(URL, cliente, opciones);
    await responder(URL_PAISES, PAISES);
    await responder(URL_SEDES, SEDES);
    http.match((r) => r.url === URL_RESUMEN_SEDES).forEach((p) => p.flush(RESUMEN_SEDES));
    await asentar();
  }

  async function responder(
    url: string,
    cuerpo: object,
    opciones?: { status: number; statusText: string },
  ): Promise<void> {
    for (let intento = 0; intento < 20; intento += 1) {
      fixture.detectChanges();
      const pendientes = http.match(url);
      if (pendientes.length) {
        if (opciones) {
          pendientes[0].flush(cuerpo, opciones);
        } else {
          pendientes[0].flush(cuerpo);
        }
        await asentar();

        return;
      }
      await new Promise((seguir) => setTimeout(seguir, 0));
    }

    throw new Error(`La pantalla no pidio ${url}`);
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

  it('mientras carga lo dice, y lo dice a un lector de pantalla', () => {
    fixture.detectChanges();
    http.expectOne(URL);
    http.match(URL_PAISES).forEach((p) => p.flush(PAISES));
    http.match(URL_SEDES).forEach((p) => p.flush(SEDES));
    http.match((r) => r.url === URL_RESUMEN_SEDES).forEach((p) => p.flush(RESUMEN_SEDES));

    expect(raiz().querySelector('[role="status"]')).toBeTruthy();
  });

  it('ensena los datos del cliente, con el tipo de documento y el pais en palabras', async () => {
    await abrir();

    expect(texto()).toContain('Hospital Central');
    expect(texto()).toContain('900123456');
    // El contrato da codigos —NIT_JURIDICO, un UUID de pais—; las palabras las pone el frontend.
    expect(texto()).toContain('NIT jurídico');
    expect(texto()).toContain('Colombia');
    // Y el identificador no se ve: un UUID en pantalla es una fuga de la base de datos.
    expect(texto()).not.toContain(ID_PAIS);
  });

  it('un cliente sin pais no ensena un identificador ni una linea vacia', async () => {
    await abrir({ ...CLIENTE, idPais: undefined });

    expect(texto()).toContain('Sin especificar');
  });

  it('si el catalogo de paises no se pudo consultar, no dice que el cliente no tiene pais', async () => {
    // El grupo clients no tiene location.read: para esos usuarios el catalogo responde 403. Decir
    // «sin especificar» seria afirmar algo falso sobre el cliente.
    fixture.detectChanges();
    await responder(URL, CLIENTE);
    await responder(
      URL_PAISES,
      { code: 'ERR_LOCATION_001' },
      { status: 403, statusText: 'Forbidden' },
    );
    await responder(URL_SEDES, SEDES);
    await atenderRecargas();

    expect(texto()).toContain('No disponible');
    expect(texto()).not.toContain('Sin especificar');
  });

  it('ensena sus correos y telefonos', async () => {
    await abrir();

    expect(texto()).toContain('contacto@hospital.co');
    expect(texto()).toContain('6011234567');
  });

  it('anadir un correo manda el cuerpo que el contrato declara y refresca la ficha', async () => {
    await abrir();

    const campo = raiz().querySelector<HTMLInputElement>('#correoNuevo')!;
    campo.value = 'nuevo@hospital.co';
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    pulsar('Añadir');
    await asentar();

    const alta = http.expectOne({ method: 'POST', url: `${URL}/emails` });

    expect(alta.request.body).toEqual({ valor: 'nuevo@hospital.co' });
    alta.flush(CLIENTE);
    await asentar();

    // La segunda consulta es la que demuestra que la ficha se invalido: sin ella, el correo recien
    // anadido no apareceria hasta recargar la pagina.
    http.expectOne({ method: 'GET', url: URL }).flush(CLIENTE);
    await asentar();
    // La invalidacion alcanza tambien a las sedes, porque su clave cuelga de la del cliente.
    await atenderRecargas();
  });

  it('un correo mal escrito no llega al servidor', async () => {
    await abrir();

    const campo = raiz().querySelector<HTMLInputElement>('#correoNuevo')!;
    campo.value = 'no-es-un-correo';
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    pulsar('Añadir');
    await asentar();

    http.expectNone({ method: 'POST', url: `${URL}/emails` });
    expect(texto()).toContain('nombre@dominio');
  });

  it('quitar un telefono llama al sub-recurso con su identificador', async () => {
    await abrir();

    // El ultimo "Quitar" es el del telefono; el primero, el del correo.
    const quitar = [...raiz().querySelectorAll('button')].filter((b) =>
      (b.textContent ?? '').includes('Quitar'),
    );
    quitar[quitar.length - 1].click();
    await asentar();

    http.expectOne({ method: 'DELETE', url: `${URL}/phones/t1` }).flush(null);
    await asentar();
    http.expectOne({ method: 'GET', url: URL }).flush(CLIENTE);
    await atenderRecargas();
  });

  describe('Retirar no es un clic', () => {
    it('el primer clic pide confirmacion y no manda nada', async () => {
      await abrir();
      pulsar('Retirar');

      http.expectNone({ method: 'DELETE', url: URL });
      // Y dice lo que va a pasar de verdad: el cliente no se borra.
      expect(texto()).toContain('No se borra nada');
    });

    it('confirmar retira y vuelve al listado', async () => {
      await abrir();
      pulsar('Retirar');
      pulsar('Confirmar retiro');
      await asentar();

      http.expectOne({ method: 'DELETE', url: URL }).flush(null);
      await asentar();
      await atenderRecargas();

      expect(TestBed.inject(Router).url).toBe('/clientes');
    });

    it('un cliente ya retirado no ofrece retirarse otra vez', async () => {
      await abrir({ ...CLIENTE, estadoActivo: false });

      expect(
        [...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim()),
      ).not.toContain('Retirar');
    });
  });

  describe('Quien solo lee, como el supervisor del cliente (2026-10-26)', () => {
    // No se le ofrece lo que el servidor le rechazaría: editar y el contacto piden client.write, y
    // retirar, client.delete.
    async function abrirSoloLectura(sedes: object = SEDES): Promise<void> {
      TestBed.resetTestingModule();
      montar(['client.read']);
      fixture.detectChanges();
      await responder(URL, CLIENTE);
      await responder(URL_PAISES, PAISES);
      await responder(URL_SEDES, sedes);
      http.match((r) => r.url === URL_RESUMEN_SEDES).forEach((p) => p.flush(RESUMEN_SEDES));
      await asentar();
    }

    const botones = () => [...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim());

    it('no tiene Editar, Retirar ni Nueva sede', async () => {
      await abrirSoloLectura();

      expect(raiz().querySelector(`a[href="/clientes/${ID}/editar"]`)).toBeNull();
      expect(raiz().querySelector(`a[href="/clientes/${ID}/sedes/nueva"]`)).toBeNull();
      expect(botones()).not.toContain('Retirar');
    });

    it('ve el contacto y no lo toca: ni quitar ni añadir', async () => {
      await abrirSoloLectura();

      expect(texto()).toContain('contacto@hospital.co');
      expect(texto()).toContain('6011234567');
      expect(botones()).not.toContain('Quitar');
      expect(botones()).not.toContain('Añadir');
      expect(raiz().querySelector('#correoNuevo')).toBeNull();
      expect(raiz().querySelector('#telefonoNuevo')).toBeNull();
    });

    it('sin sedes no le pide que abra la primera', async () => {
      await abrirSoloLectura([]);

      expect(texto()).toContain('todavía no tiene sedes registradas');
      expect(texto()).not.toContain('Abra la primera');
    });

    it('quien edita sin poder retirar ve Editar y no Retirar', async () => {
      TestBed.resetTestingModule();
      montar(['client.read', 'client.write']);
      await abrir();

      expect(raiz().querySelector(`a[href="/clientes/${ID}/editar"]`)).not.toBeNull();
      expect(botones()).not.toContain('Retirar');
    });
  });

  describe('Sus sedes', () => {
    it('las lista, cada una enlazada a su ficha', async () => {
      await abrir();

      const enlaces = [...raiz().querySelectorAll('a')].map((a) => a.getAttribute('href'));

      expect(texto()).toContain('Sede Norte');
      expect(enlaces).toContain('/sedes/s1');
    });

    it('el estado de una sede tambien se distingue por peso, no por color', async () => {
      await abrir();

      const estados = [...raiz().querySelectorAll('[data-sede] span')];
      const abierta = estados.find((e) => e.textContent?.trim() === 'Abierta')!;
      const cerrada = estados.find((e) => e.textContent?.trim() === 'Cerrada')!;

      // Desde el rediseño pesa lo que hay que mirar: la cerrada, que es la excepción.
      expect(cerrada.className).toContain('font-bold');
      expect(abierta.className).not.toContain('font-bold');
      expect(cerrada.className).not.toContain('text-accent');
    });

    it('cada tarjeta dice la dirección y cuántas áreas abiertas tiene, y van primero las abiertas', async () => {
      await abrir();

      const tarjetas = [...raiz().querySelectorAll('[data-sede]')];
      expect(tarjetas.map((t) => t.getAttribute('data-sede'))).toEqual(['s1', 's2']);
      expect(tarjetas[0].textContent).toContain('Calle 10 · Carrera 20 · N.º 30');
      expect(tarjetas[0].textContent?.replace(/\s+/g, ' ')).toContain('2 áreas');
    });

    it('sin sedes dice por donde empezar y para que sirven', async () => {
      fixture.detectChanges();
      await responder(URL, CLIENTE);
      await responder(URL_PAISES, PAISES);
      await responder(URL_SEDES, []);
      await atenderRecargas();

      expect(texto()).toContain('Abra la primera');
    });
  });

  it('un fallo del API se ensena traducido, con el codigo real del backend', async () => {
    await abrir(
      { code: 'ERR_CLIENT_001', message: 'Client not found', details: [] },
      { status: 404, statusText: 'Not Found' },
    );

    const aviso = raiz().querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Ese cliente no existe');
    expect(aviso?.textContent).not.toContain('not found');
  });

  describe('Lo que WCAG exige y no se ve', () => {
    it('los dos campos de contacto tienen etiqueta asociada', async () => {
      await abrir();

      for (const id of ['correoNuevo', 'telefonoNuevo']) {
        expect(raiz().querySelector(`label[for="${id}"]`)).toBeTruthy();
      }
    });

    it('los controles respetan el area tactil minima', async () => {
      await abrir();

      for (const control of raiz().querySelectorAll('input, select, button')) {
        expect(control.className).toContain('min-h-tactil');
      }
    });

    it('el estado se dice en palabras, con el mismo color para activo y retirado', async () => {
      await abrir({ ...CLIENTE, estadoActivo: false });
      const retirado = raiz().querySelector('header p')!;

      expect(retirado.textContent).toContain('Cliente retirado');
      // La línea de arriba va en el rojo de la marca en los dos estados: el color no distingue.
      const clases = retirado.className;
      TestBed.resetTestingModule();
      montar();
      await abrir();
      expect(raiz().querySelector('header p')!.className).toBe(clases);
    });
  });

  describe('Las cifras, con las autoridades que las abren', () => {
    const TODAS = [
      'client.read',
      'client.write',
      'equipment.read',
      'person.read',
      'person.write',
      'location.read',
    ];

    async function abrirConTodo(): Promise<void> {
      TestBed.resetTestingModule();
      montar(TODAS);
      await abrir();
      await asentar();
      const conteos = http.match((r) => r.url === URL_CONTEOS);
      expect(conteos.map((r) => r.request.params.get('idCliente'))).toEqual([ID]);
      conteos.forEach((p) => p.flush(CONTEOS));
      http
        .match(URL_PERSONAS)
        .forEach((p) =>
          p.flush([{ identificador: 'p1', primerNombre: 'Carolina', primerApellido: 'Vélez' }]),
        );
      http.match(URL_CIUDADES).forEach((p) => p.flush([{ id: 'ci1', nombre: 'Bogotá' }]));
      await asentar();
    }

    const cifra = (etiqueta: string) =>
      [...raiz().querySelectorAll('dt')]
        .find((dt) => dt.textContent?.trim() === etiqueta)
        ?.nextElementSibling?.textContent?.trim();

    it('cuenta sedes y áreas abiertas, equipos y fuera de servicio, sin la sede cerrada', async () => {
      await abrirConTodo();

      expect(cifra('Sedes abiertas')).toBe('1');
      expect(cifra('Áreas de servicio')).toBe('2');
      expect(cifra('Equipos instalados')).toBe('8');
      expect(cifra('Fuera de servicio')).toBe('1');
      expect(raiz().querySelector('[data-sede="s1"]')?.textContent).toContain('Bogotá');
    });

    it('pide los conteos de este cliente y nombra a los supervisores que encuentra, con su cargo', async () => {
      await abrirConTodo();

      const supervisores = [...raiz().querySelectorAll('[data-supervisor]')].map((li) =>
        li.textContent?.replace(/\s+/g, ' ').trim(),
      );
      // El otro supervisor no está en la lista de personas: no se inventa su nombre, y sin cargo se dice.
      expect(supervisores[0]).toContain('Carolina Vélez Ingeniera biomédica');
      expect(supervisores[1]).toContain('Supervisor Sin cargo');
      // El alta lleva el cliente, para nombrar a la persona al crearla.
      expect(
        raiz().querySelector(`a[href="/personas/nueva/supervisor?cliente=${ID}"]`),
      ).not.toBeNull();
    });

    it('sin equipment.read ni person.read no pide ni pinta lo que no puede leer', async () => {
      await abrir();

      expect(cifra('Equipos instalados')).toBeUndefined();
      expect(http.match((r) => r.url === URL_CONTEOS)).toHaveLength(0);
      expect(http.match(URL_PERSONAS)).toHaveLength(0);
      expect(raiz().querySelector('a[href^="/personas/nueva/supervisor"]')).toBeNull();
    });
  });

  describe('Sus supervisores, que ven lo del cliente y aprueban sus órdenes (V53)', () => {
    const PERSONAS = [
      {
        identificador: 'p1',
        primerNombre: 'Carolina',
        primerApellido: 'Vélez',
        tipoPersona: 'SUPERVISOR',
        estadoActivo: true,
      },
      {
        identificador: 'p3',
        primerNombre: 'Andrés',
        primerApellido: 'Mora',
        tipoPersona: 'SUPERVISOR',
        estadoActivo: true,
      },
      {
        identificador: 'p4',
        primerNombre: 'Iván',
        primerApellido: 'Ruiz',
        tipoPersona: 'ENGINEER',
        estadoActivo: true,
      },
      {
        identificador: 'p5',
        primerNombre: 'Lucía',
        primerApellido: 'Paz',
        tipoPersona: 'SUPERVISOR',
        estadoActivo: false,
      },
    ];
    const QUIEN_NOMBRA = ['client.read', 'client.write', 'person.read'];

    async function abrirCon(autoridades: readonly string[]): Promise<void> {
      TestBed.resetTestingModule();
      montar(autoridades);
      await abrir();
      http.match(URL_PERSONAS).forEach((p) => p.flush(PERSONAS));
      await asentar();
    }

    const opciones = () =>
      [...raiz().querySelectorAll<HTMLOptionElement>('#supervisorElegido option')]
        .filter((o) => o.value)
        .map((o) => o.textContent?.trim());

    function escribirEn(id: string, valor: string): void {
      const campo = raiz().querySelector<HTMLInputElement>(`#${id}`)!;
      campo.value = valor;
      campo.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    it('ofrece nombrar solo a supervisores activos que aún no lo son: un ingeniero aprobaría su trabajo', async () => {
      await abrirCon(QUIEN_NOMBRA);

      expect(opciones()).toEqual(['Andrés Mora']);
    });

    it('nombrar manda la persona elegida y su cargo al sub-recurso del cliente', async () => {
      await abrirCon(QUIEN_NOMBRA);
      const select = raiz().querySelector<HTMLSelectElement>('#supervisorElegido')!;
      select.value = 'p3';
      select.dispatchEvent(new Event('change'));
      escribirEn('cargoNuevo', 'Médico propietario');

      pulsar('Nombrar');
      await asentar();

      const peticion = http.expectOne(`${URL}/supervisors/p3`);
      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({ cargo: 'Médico propietario' });
      peticion.flush(CLIENTE);
      await asentar();
      // La ficha se vuelve a pedir: sin eso, el cambio no se veria hasta recargar.
      http.expectOne({ method: 'GET', url: URL }).flush(CLIENTE);
      await atenderRecargas();
    });

    it('sin cargo, lo manda nulo y no en blanco', async () => {
      await abrirCon(QUIEN_NOMBRA);
      const select = raiz().querySelector<HTMLSelectElement>('#supervisorElegido')!;
      select.value = 'p3';
      select.dispatchEvent(new Event('change'));
      escribirEn('cargoNuevo', '   ');

      pulsar('Nombrar');
      await asentar();

      const peticion = http.expectOne(`${URL}/supervisors/p3`);
      expect(peticion.request.body).toEqual({ cargo: null });
      peticion.flush(CLIENTE);
      await asentar();
      http.expectOne({ method: 'GET', url: URL }).flush(CLIENTE);
      await atenderRecargas();
    });

    it('sin elegir a nadie no se nombra', async () => {
      await abrirCon(QUIEN_NOMBRA);

      pulsar('Nombrar');
      await asentar();

      expect(http.match((r) => r.url.includes('/supervisors/'))).toHaveLength(0);
    });

    it('cambiar el cargo de uno manda un PATCH a su sub-recurso', async () => {
      await abrirCon(QUIEN_NOMBRA);

      raiz()
        .querySelector<HTMLButtonElement>(
          'button[aria-label="Cambiar el cargo de Carolina Vélez"]',
        )!
        .click();
      fixture.detectChanges();
      const campo = raiz().querySelector<HTMLInputElement>('#cargo-p1')!;
      expect(campo.value).toBe('Ingeniera biomédica');
      escribirEn('cargo-p1', 'Jefa de biomédica');
      pulsar('Guardar');
      await asentar();

      const peticion = http.expectOne(`${URL}/supervisors/p1`);
      expect(peticion.request.method).toBe('PATCH');
      expect(peticion.request.body).toEqual({ cargo: 'Jefa de biomédica' });
      peticion.flush(CLIENTE);
      await asentar();
      http.expectOne({ method: 'GET', url: URL }).flush(CLIENTE);
      await atenderRecargas();
    });

    it('retirar a uno llama al sub-recurso con su identificador', async () => {
      await abrirCon(QUIEN_NOMBRA);

      const boton = raiz().querySelector<HTMLButtonElement>(
        'button[aria-label="Retirar a Carolina Vélez como supervisor"]',
      )!;
      boton.click();
      await asentar();

      const peticion = http.expectOne(`${URL}/supervisors/p1`);
      expect(peticion.request.method).toBe('DELETE');
      peticion.flush({ ...CLIENTE, supervisores: [{ idPersona: 'p2' }] });
      await asentar();
      // La ficha se vuelve a pedir: sin eso, el cambio no se veria hasta recargar.
      http.expectOne({ method: 'GET', url: URL }).flush(CLIENTE);
      await atenderRecargas();
    });

    it('sin supervisores dice que sus órdenes no se pueden aprobar', async () => {
      TestBed.resetTestingModule();
      montar(QUIEN_NOMBRA);
      await abrir({ ...CLIENTE, supervisores: [] });
      http.match(URL_PERSONAS).forEach((p) => p.flush(PERSONAS));
      await asentar();

      expect(texto()).toContain('sus órdenes no se pueden aprobar hasta nombrar uno');
    });

    it('sin client.write los ve y no los toca', async () => {
      await abrirCon(['client.read', 'person.read', 'person.write']);

      expect(texto()).toContain('Carolina Vélez');
      expect(raiz().querySelector('#supervisorElegido')).toBeNull();
      expect(raiz().querySelector('button[aria-label^="Retirar a"]')).toBeNull();
      expect(raiz().querySelector('button[aria-label^="Cambiar el cargo"]')).toBeNull();
      // Registrar uno nuevo sin poder nombrarlo dejaría una cuenta que no ve nada.
      expect(raiz().querySelector('a[href^="/personas/nueva/supervisor"]')).toBeNull();
    });
  });

  afterEach(() => http.verify());
});
