import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import {
  asentar,
  elegirEnBuscador,
  escribirEnBuscador,
  sugerenciasDe,
} from '../../../../testing/pantalla';
import { ClaseDeAlta, NuevaPersona, TODA_LA_SEDE } from './nueva-persona';

const URL = 'http://localhost:8081/v1/api/persons';
const ID_CLIENTE = '33333333-3333-3333-3333-333333333333';
const URL_CLIENTE = `http://localhost:8081/v1/api/clients/${ID_CLIENTE}`;
const URL_CLIENTES = 'http://localhost:8081/v1/api/clients';
const URL_SEDES = `${URL_CLIENTE}/headquarters`;
const ID_SEDE = '44444444-4444-4444-4444-444444444444';
const URL_AREAS = `http://localhost:8081/v1/api/headquarters/${ID_SEDE}/service-areas`;
const URL_ENCARGADOS = 'http://localhost:8081/v1/api/managers';

const CLIENTES = [
  { id: 'c-retirado', razonSocial: 'Clínica Cerrada', estadoActivo: false },
  { id: ID_CLIENTE, razonSocial: 'Hospital Central', estadoActivo: true },
  { id: 'c-otro', razonSocial: 'Laboratorio Sur', estadoActivo: true },
];

@Component({ selector: 'app-listado-falso', template: '' })
class ListadoFalso {}

describe('Alta de una persona', () => {
  let fixture: ComponentFixture<NuevaPersona>;
  let http: HttpTestingController;

  function montar(
    clase: ClaseDeAlta,
    cliente?: string,
    autoridades: readonly string[] = ['super.person.write', 'person.write'],
  ): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([
          { path: 'personas', component: ListadoFalso },
          { path: 'clientes/:id', component: ListadoFalso },
        ]),
      ],
    });
    fixture = TestBed.createComponent(NuevaPersona);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('clase', clase);
    if (cliente) {
      fixture.componentRef.setInput('cliente', cliente);
    }
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const campo = (id: string) => raiz().querySelector<HTMLInputElement>(`#${id}`);

  function escribir(id: string, valor: string): void {
    const control = campo(id)!;
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function elegir(id: string, valor: string): void {
    const control = raiz().querySelector<HTMLSelectElement>(`#${id}`)!;
    control.value = valor;
    control.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function enviar(): void {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  /** Lo mínimo que cualquiera de las cuatro altas necesita. */
  function rellenarLoComun(): void {
    escribir('cedula', '1010101010');
    escribir('primerNombre', 'Grace');
    escribir('primerApellido', 'Hopper');
  }

  it('el alta de un encargado no pide cuenta: esa puerta no crea usuario', () => {
    montar('encargado');

    expect(campo('nombreUsuario')).toBeNull();
    expect(campo('password')).toBeNull();
    expect(texto()).toContain('no entra al sistema');
  });

  it('y tampoco ofrece elegir el tipo: el servidor solo admite encargados ahí', async () => {
    // Dejar elegir invitaría a lo que el backend rechaza: con esa puerta no se puede escribir una fila
    // que diga ser administrador sin serlo.
    montar('encargado');
    rellenarLoComun();

    expect(raiz().querySelectorAll('select')).toHaveLength(0);

    enviar();
    await asentar(fixture);

    const peticion = http.expectOne(URL);
    expect(peticion.request.body.tipoPersona).toBe('MANAGER');
    peticion.flush({ identificador: 'p9' });
    await asentar(fixture);
  });

  it('el alta de un ingeniero va a su propia ruta y no manda el tipo en el cuerpo', async () => {
    // El tipo lo dice la ruta. Si viajara en el cuerpo, se podría pedir «ingeniero» y mandar «ADMIN».
    montar('ingeniero');
    rellenarLoComun();
    escribir('nombreUsuario', 'ghopper');
    escribir('password', 'contrasena-de-12');
    escribir('correo-0', 'grace@armada.mil');

    enviar();
    await asentar(fixture);

    const peticion = http.expectOne(`${URL}/engineers`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body.tipoPersona).toBeUndefined();
    expect(peticion.request.body.nombreUsuario).toBe('ghopper');
    expect(peticion.request.body.emailPersonList).toEqual([{ correoPersona: 'grace@armada.mil' }]);
    peticion.flush({ identificador: 'p9' });
    await asentar(fixture);
  });

  it('sin correo no se registra a alguien con cuenta: con él se crea el usuario', async () => {
    montar('administrador');
    rellenarLoComun();
    escribir('nombreUsuario', 'jefe');
    escribir('password', 'contrasena-de-12');

    enviar();
    await asentar(fixture);

    // Ni una peticion: el formulario se detiene antes.
    http.expectNone(`${URL}/admins`);
  });

  it('una contraseña corta no llega al servidor', async () => {
    montar('ingeniero');
    rellenarLoComun();
    escribir('nombreUsuario', 'ghopper');
    escribir('password', 'corta');
    escribir('correo-0', 'grace@armada.mil');

    enviar();
    await asentar(fixture);

    http.expectNone(`${URL}/engineers`);
  });

  it('una contraseña de once caracteres no llega: el sistema de acceso exige doce', async () => {
    // La política del realm es length(12). Con ocho, como exigía el alta hasta el 2026-10-17, esta
    // contraseña pasaba el formulario y la rechazaba Keycloak.
    montar('ingeniero');
    rellenarLoComun();
    escribir('nombreUsuario', 'ghopper');
    escribir('password', 'once-letras');
    escribir('correo-0', 'grace@armada.mil');

    enviar();
    await asentar(fixture);

    http.expectNone(`${URL}/engineers`);
  });

  it('los campos opcionales vacíos se mandan ausentes, no en blanco', async () => {
    // Una cadena vacía no es «no tiene segundo nombre», es un segundo nombre en blanco.
    montar('encargado');
    rellenarLoComun();

    enviar();
    await asentar(fixture);

    const peticion = http.expectOne(URL);
    expect(peticion.request.body.segundoNombre).toBeUndefined();
    expect(peticion.request.body.segundoApellido).toBeUndefined();
    expect(peticion.request.body.phonePersonList).toEqual([]);
    peticion.flush({ identificador: 'p9' });
    await asentar(fixture);
  });

  it('el alta de un ingeniero no ofrece ponerlo a cargo de un cliente (V46)', async () => {
    // Desde V46 solo encargados y representantes responden por una sede o un área, y la casilla
    // «También es encargado» ponía un segundo tipo que ya no existe.
    montar('ingeniero');
    fixture.detectChanges();

    expect(raiz().textContent).not.toContain('También es encargado');
    expect(raiz().querySelector('input[type="checkbox"]')).toBeNull();
  });

  it('un nombre de usuario repetido se explica con el mensaje del catálogo', async () => {
    // Es un error de Keycloak, no del dato: se arregla cambiando el nombre, y el mensaje lo dice.
    montar('ingeniero');
    rellenarLoComun();
    escribir('nombreUsuario', 'ghopper');
    escribir('password', 'contrasena-de-12');
    escribir('correo-0', 'grace@armada.mil');
    enviar();
    await asentar(fixture);

    http.expectOne(`${URL}/engineers`).flush(
      { code: 'ERR_KEYCLOAK_001', message: 'Keycloak user already exists' },
      { status: 409, statusText: 'Conflict' },
    );
    await asentar(fixture);

    expect(texto()).toContain('ya existe en el sistema de acceso');
    expect(texto()).not.toContain('Keycloak user already exists');
  });

  it('al registrar vuelve al listado', async () => {
    montar('encargado');
    const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
    rellenarLoComun();
    enviar();
    await asentar(fixture);

    http.expectOne(URL).flush({ identificador: 'p9' });
    await asentar(fixture);

    expect(viaje).toHaveBeenCalledWith(['/personas']);
  });

  describe('Un representante se registra para un cliente', () => {
    // Sin nombrarlo, su cuenta entra y no ve nada: el filtrado por dueño lo acota a sus clientes.
    function rellenarConCuenta(): void {
      rellenarLoComun();
      escribir('nombreUsuario', 'ghopper');
      escribir('password', 'contrasena-de-12');
      escribir('correo-0', 'grace@armada.mil');
    }

    it('entrando desde la ficha dice de qué cliente, lo nombra al crearlo y vuelve a la ficha', async () => {
      montar('representante', ID_CLIENTE);
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
      await asentar(fixture);
      http.expectOne(URL_CLIENTE).flush({ id: ID_CLIENTE, razonSocial: 'Hospital Central' });
      await asentar(fixture);

      expect(texto()).toContain('Se nombrará representante de Hospital Central');

      rellenarConCuenta();
      enviar();
      await asentar(fixture);
      http.expectOne(`${URL}/ceo-clients`).flush({ identificador: 'p9' });
      await asentar(fixture);

      // Hasta que no se nombra no se sale: salir antes dejaba la cuenta sin nada que ver.
      expect(viaje).not.toHaveBeenCalled();
      const nombramiento = http.expectOne(`${URL_CLIENTE}/representatives/p9`);
      expect(nombramiento.request.method).toBe('POST');
      nombramiento.flush({ id: ID_CLIENTE, representantes: ['p9'] });
      await asentar(fixture);
      // Nombrar invalida la ficha del cliente, y la mutación espera a tenerla antes de resolverse.
      http
        .match(URL_CLIENTE)
        .forEach((p) => p.flush({ id: ID_CLIENTE, razonSocial: 'Hospital Central' }));
      await asentar(fixture);

      expect(viaje).toHaveBeenCalledWith(['/clientes', ID_CLIENTE]);
    });

    it('si no se pudo nombrar, dice que la persona ya existe y no deja registrarla otra vez', async () => {
      // Son dos llamadas sin transacción: reenviar el formulario crearía a la misma persona dos veces.
      montar('representante', ID_CLIENTE);
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
      await asentar(fixture);
      http.expectOne(URL_CLIENTE).flush({ id: ID_CLIENTE, razonSocial: 'Hospital Central' });
      rellenarConCuenta();
      enviar();
      await asentar(fixture);
      http.expectOne(`${URL}/ceo-clients`).flush({ identificador: 'p9' });
      await asentar(fixture);

      http
        .expectOne(`${URL_CLIENTE}/representatives/p9`)
        .flush({ code: 'ERR_CLIENT_001' }, { status: 500, statusText: 'Server Error' });
      await asentar(fixture);

      expect(texto()).toContain('La persona quedó registrada');
      expect(viaje).not.toHaveBeenCalled();
      expect(raiz().querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
      expect(
        raiz().querySelector(`a[href="/clientes/${ID_CLIENTE}"]`),
        'enlaza la ficha para nombrarla desde ahí',
      ).not.toBeNull();
    });

    it('sin cliente en la ruta se registra como siempre y vuelve al listado', async () => {
      montar('representante');
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
      rellenarConCuenta();
      enviar();
      await asentar(fixture);

      http.expectOne(`${URL}/ceo-clients`).flush({ identificador: 'p9' });
      await asentar(fixture);

      http.expectNone((r) => r.url.includes('/clients/'));
      expect(viaje).toHaveBeenCalledWith(['/personas']);
    });

    it('el cliente de la ruta no cuenta en otra alta: a un ingeniero no se le nombra representante', async () => {
      montar('ingeniero', ID_CLIENTE);
      rellenarConCuenta();
      enviar();
      await asentar(fixture);

      http.expectOne(`${URL}/engineers`).flush({ identificador: 'p9' });
      await asentar(fixture);

      http.expectNone((r) => r.url.includes('/clients/'));
      expect(texto()).not.toContain('Se nombrará representante');
    });
  });

  describe('El representante elige su cliente desde el listado (2026-10-26)', () => {
    const QUIEN_NOMBRA = ['person.write', 'client.read', 'client.write'];

    it('elegido, se le nombra al crearlo y se vuelve a la ficha del cliente', async () => {
      montar('representante', undefined, QUIEN_NOMBRA);
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
      await asentar(fixture);
      http.expectOne(URL_CLIENTES).flush(CLIENTES);
      await asentar(fixture);

      // Se predice escribiendo, y un cliente retirado no admite representantes nuevos.
      await escribirEnBuscador(fixture, 'clienteElegido', 'a');
      expect(sugerenciasDe(fixture, 'clienteElegido')).toEqual([
        'Hospital Central',
        'Laboratorio Sur',
      ]);

      rellenarLoComun();
      escribir('nombreUsuario', 'ghopper');
      escribir('password', 'contrasena-de-12');
      escribir('correo-0', 'grace@armada.mil');
      await elegirEnBuscador(fixture, 'clienteElegido', 'Hospital Central');
      enviar();
      await asentar(fixture);
      http.expectOne(`${URL}/ceo-clients`).flush({ identificador: 'p9' });
      await asentar(fixture);

      http.expectOne(`${URL_CLIENTE}/representatives/p9`).flush({ id: ID_CLIENTE });
      await asentar(fixture);
      http.match(URL_CLIENTES).forEach((p) => p.flush(CLIENTES));
      await asentar(fixture);

      expect(viaje).toHaveBeenCalledWith(['/clientes', ID_CLIENTE]);
    });

    it('un cliente que no existe se dice y no deja registrar: no se guarda sin él a escondidas', async () => {
      montar('representante', undefined, QUIEN_NOMBRA);
      await asentar(fixture);
      http.expectOne(URL_CLIENTES).flush(CLIENTES);
      await asentar(fixture);
      rellenarLoComun();
      escribir('nombreUsuario', 'ghopper');
      escribir('password', 'contrasena-de-12');
      escribir('correo-0', 'grace@armada.mil');
      await escribirEnBuscador(fixture, 'clienteElegido', 'Hospital Inventado');
      enviar();
      await asentar(fixture);

      expect(texto()).toContain('No hay ninguna opción con ese nombre');
      http.expectNone(`${URL}/ceo-clients`);
    });

    it('sin elegir, se registra sin nombrar y vuelve al listado', async () => {
      montar('representante', undefined, QUIEN_NOMBRA);
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
      await asentar(fixture);
      http.expectOne(URL_CLIENTES).flush(CLIENTES);
      rellenarLoComun();
      escribir('nombreUsuario', 'ghopper');
      escribir('password', 'contrasena-de-12');
      escribir('correo-0', 'grace@armada.mil');
      enviar();
      await asentar(fixture);
      http.expectOne(`${URL}/ceo-clients`).flush({ identificador: 'p9' });
      await asentar(fixture);

      http.expectNone((r) => r.url.includes('/representatives/'));
      expect(viaje).toHaveBeenCalledWith(['/personas']);
    });

    it('quien no puede nombrar no ve el selector, ni se piden los clientes', async () => {
      montar('representante', undefined, ['person.write', 'client.read']);
      await asentar(fixture);

      expect(raiz().querySelector('#clienteElegido')).toBeNull();
      http.expectNone(URL_CLIENTES);
    });
  });

  describe('El encargado elige cliente, sede y área (2026-10-26)', () => {
    const QUIEN_ASIGNA = ['person.write', 'client.read', 'engineer.assign'];
    const SEDES = [
      { id: 'sede-cerrada', nombre: 'Sede Vieja', estadoActivo: false },
      { id: ID_SEDE, nombre: 'Sede Norte', estadoActivo: true },
    ];
    const AREAS = [
      { id: 'area-1', nombre: 'Urgencias', estadoActivo: true },
      { id: 'area-cerrada', nombre: 'Bodega', estadoActivo: false },
    ];

    async function elegirSede(): Promise<void> {
      montar('encargado', undefined, QUIEN_ASIGNA);
      await asentar(fixture);
      http.expectOne(URL_CLIENTES).flush(CLIENTES);
      await asentar(fixture);
      await elegirEnBuscador(fixture, 'clienteElegido', 'Hospital Central');
      await asentar(fixture);
      http.expectOne(URL_SEDES).flush(SEDES);
      await asentar(fixture);
      elegir('sede', ID_SEDE);
      await asentar(fixture);
      http.expectOne(URL_AREAS).flush(AREAS);
      await asentar(fixture);
    }

    it('con toda la sede queda al frente de ella, en una sola operación', async () => {
      await elegirSede();
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');

      expect(
        [...raiz().querySelectorAll('#sede option')].map((o) => o.textContent?.trim()),
      ).toEqual(['Elija una sede', 'Sede Norte']);

      rellenarLoComun();
      escribir('correo-0', 'grace@armada.mil');
      elegir('area', TODA_LA_SEDE);
      enviar();
      await asentar(fixture);

      const alta = http.expectOne(URL_ENCARGADOS);
      expect(alta.request.method).toBe('POST');
      expect(alta.request.body).toMatchObject({
        cedula: '1010101010',
        tipo: 'HEADQUARTER',
        idAsignacion: ID_SEDE,
        correos: [{ valor: 'grace@armada.mil' }],
        telefonos: [],
      });
      http.expectNone(URL);
      alta.flush({ idPersona: 'p9' });
      await asentar(fixture);

      expect(viaje).toHaveBeenCalledWith(['/personas']);
    });

    it('con área, queda al frente del área; las cerradas no se ofrecen', async () => {
      await elegirSede();

      expect(
        [...raiz().querySelectorAll('#area option')].map((o) => o.textContent?.trim()),
      ).toEqual(['Elija', 'Toda la sede', 'Área: Urgencias']);

      rellenarLoComun();
      escribir('correo-0', 'grace@armada.mil');
      elegir('area', 'area-1');
      enviar();
      await asentar(fixture);

      const alta = http.expectOne(URL_ENCARGADOS);
      expect(alta.request.body).toMatchObject({ tipo: 'SERVICE_AREA', idAsignacion: 'area-1' });
      alta.flush({ idPersona: 'p9' });
      await asentar(fixture);
    });

    it('con sede pide un correo, que esa puerta exige', async () => {
      await elegirSede();
      rellenarLoComun();
      elegir('area', TODA_LA_SEDE);
      enviar();
      await asentar(fixture);

      http.expectNone(URL_ENCARGADOS);
    });

    it('elegida la sede, hay que decir si es toda o un área: no se supone', async () => {
      await elegirSede();
      rellenarLoComun();
      escribir('correo-0', 'grace@armada.mil');
      enviar();
      await asentar(fixture);

      http.expectNone(URL_ENCARGADOS);
      http.expectNone(URL);
    });

    it('elegido el cliente, falta la sede: el cliente solo no asigna a nadie', async () => {
      montar('encargado', undefined, QUIEN_ASIGNA);
      await asentar(fixture);
      http.expectOne(URL_CLIENTES).flush(CLIENTES);
      await asentar(fixture);
      await elegirEnBuscador(fixture, 'clienteElegido', 'Hospital Central');
      await asentar(fixture);
      http.expectOne(URL_SEDES).flush(SEDES);
      rellenarLoComun();
      enviar();
      await asentar(fixture);

      http.expectNone(URL_ENCARGADOS);
      http.expectNone(URL);
    });

    it('cambiar de cliente olvida la sede elegida', async () => {
      await elegirSede();
      await escribirEnBuscador(fixture, 'clienteElegido', '');
      await asentar(fixture);

      expect(raiz().querySelector('#sede')).toBeNull();

      rellenarLoComun();
      enviar();
      await asentar(fixture);

      // Sin cliente es el alta de siempre, sin correo obligatorio.
      const alta = http.expectOne(URL);
      expect(alta.request.body.tipoPersona).toBe('MANAGER');
      alta.flush({ identificador: 'p9' });
      await asentar(fixture);
    });

    it('volver a elegir el mismo cliente no olvida la sede: nada cambió', async () => {
      await elegirSede();
      await elegirEnBuscador(fixture, 'clienteElegido', 'Hospital Central');
      await asentar(fixture);

      expect(raiz().querySelector<HTMLSelectElement>('#sede')!.value).toBe(ID_SEDE);
    });

    it('otro cliente olvida la sede y el área del anterior', async () => {
      await elegirSede();
      elegir('area', 'area-1');
      await elegirEnBuscador(fixture, 'clienteElegido', 'Laboratorio Sur');
      await asentar(fixture);
      http.expectOne(`${URL_CLIENTES}/c-otro/headquarters`).flush([]);
      await asentar(fixture);

      expect(raiz().querySelector<HTMLSelectElement>('#sede')!.value).toBe('');
      expect(raiz().querySelector('#area')).toBeNull();
    });

    it('quien no puede asignar encargados no ve el selector', async () => {
      montar('encargado', undefined, ['person.write', 'client.read']);
      await asentar(fixture);

      expect(raiz().querySelector('#clienteElegido')).toBeNull();
      http.expectNone(URL_CLIENTES);
    });
  });

  describe('El representante puede ser además encargado (2026-10-26)', () => {
    const QUIEN_ASIGNA = ['person.write', 'client.read', 'client.write', 'engineer.assign'];
    const URL_ASIGNACION = `${URL_ENCARGADOS}/assignments`;
    const SEDES = [{ id: ID_SEDE, nombre: 'Sede Norte', estadoActivo: true }];
    const AREAS = [{ id: 'area-1', nombre: 'Urgencias', estadoActivo: true }];

    /** Entra desde la ficha del cliente, que ya trae el cliente, y deja la cuenta rellena. */
    async function abrir(autoridades: readonly string[] = QUIEN_ASIGNA): Promise<void> {
      montar('representante', ID_CLIENTE, autoridades);
      await asentar(fixture);
      http.expectOne(URL_CLIENTE).flush({ id: ID_CLIENTE, razonSocial: 'Hospital Central' });
      await asentar(fixture);
      rellenarLoComun();
      escribir('nombreUsuario', 'ghopper');
      escribir('password', 'contrasena-de-12');
      escribir('correo-0', 'grace@armada.mil');
    }

    async function elegirLaSede(): Promise<void> {
      http.expectOne(URL_SEDES).flush(SEDES);
      await asentar(fixture);
      elegir('sede', ID_SEDE);
      await asentar(fixture);
      http.expectOne(URL_AREAS).flush(AREAS);
      await asentar(fixture);
    }

    /** Crea y nombra; lo que venga después es lo que la prueba mira. */
    async function crearYNombrar(): Promise<void> {
      enviar();
      await asentar(fixture);
      http.expectOne(`${URL}/ceo-clients`).flush({ identificador: 'p9' });
      await asentar(fixture);
      http.expectOne(`${URL_CLIENTE}/representatives/p9`).flush({ id: ID_CLIENTE });
      await asentar(fixture);
      await atenderRecargas();
    }

    /**
     * Nombrar invalida la ficha del cliente, y las sedes cuelgan de su clave: la mutación espera a
     * tener las dos antes de resolverse.
     */
    async function atenderRecargas(): Promise<void> {
      http
        .match(URL_CLIENTE)
        .forEach((p) => p.flush({ id: ID_CLIENTE, razonSocial: 'Hospital Central' }));
      http.match(URL_SEDES).forEach((p) => p.flush(SEDES));
      await asentar(fixture);
    }

    it('no lleva el check: lo dice eligiendo sede', async () => {
      await abrir();
      http.expectOne(URL_SEDES).flush(SEDES);

      expect(raiz().querySelector('input[type="checkbox"]')).toBeNull();
      expect(raiz().querySelector('#sede')).not.toBeNull();
    });

    it('sin sede es solo representante: sin asignación', async () => {
      await abrir();
      http.expectOne(URL_SEDES).flush(SEDES);
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
      enviar();
      await asentar(fixture);

      const alta = http.expectOne(`${URL}/ceo-clients`);
      alta.flush({ identificador: 'p9' });
      await asentar(fixture);
      http.expectOne(`${URL_CLIENTE}/representatives/p9`).flush({ id: ID_CLIENTE });
      await asentar(fixture);
      await atenderRecargas();

      http.expectNone(URL_ASIGNACION);
      expect(viaje).toHaveBeenCalledWith(['/clientes', ID_CLIENTE]);
    });

    it('con sede, hay que decir si es toda o un área', async () => {
      await abrir();
      await elegirLaSede();
      enviar();
      await asentar(fixture);

      http.expectNone(`${URL}/ceo-clients`);
    });

    it('con un área, queda nombrado y al frente de ella, y vuelve a la ficha del cliente', async () => {
      await abrir();
      await elegirLaSede();
      elegir('area', 'area-1');
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');

      enviar();
      await asentar(fixture);
      const alta = http.expectOne(`${URL}/ceo-clients`);
      alta.flush({ identificador: 'p9' });
      await asentar(fixture);
      http.expectOne(`${URL_CLIENTE}/representatives/p9`).flush({ id: ID_CLIENTE });
      await asentar(fixture);
      await atenderRecargas();

      // Hasta asignar no se sale.
      expect(viaje).not.toHaveBeenCalled();
      const asignacion = http.expectOne(URL_ASIGNACION);
      expect(asignacion.request.body).toEqual({
        idPersona: 'p9',
        tipo: 'SERVICE_AREA',
        idAsignacion: 'area-1',
      });
      asignacion.flush({ idPersona: 'p9' });
      await asentar(fixture);

      expect(viaje).toHaveBeenCalledWith(['/clientes', ID_CLIENTE]);
    });

    it('si la asignación falla, lo dice, no deja registrarla otra vez y reintenta solo eso', async () => {
      await abrir();
      await elegirLaSede();
      elegir('area', TODA_LA_SEDE);
      await crearYNombrar();

      http
        .expectOne(URL_ASIGNACION)
        .flush({ code: 'ERR_CLIENT_001' }, { status: 500, statusText: 'Server Error' });
      await asentar(fixture);

      expect(texto()).toContain('nombrada representante, pero no se pudo ponerla al frente');
      expect(raiz().querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);

      const reintento = [...raiz().querySelectorAll('button')].find(
        (b) => b.textContent?.trim() === 'Reintentar la asignación',
      )!;
      reintento.click();
      await asentar(fixture);

      const otra = http.expectOne(URL_ASIGNACION);
      expect(otra.request.body).toEqual({
        idPersona: 'p9',
        tipo: 'HEADQUARTER',
        idAsignacion: ID_SEDE,
      });
      otra.flush({ idPersona: 'p9' });
      await asentar(fixture);
      http.expectNone(`${URL}/ceo-clients`);
    });

    it('quien no puede asignar encargados no ve la sede', async () => {
      await abrir(['person.write', 'client.read', 'client.write']);

      expect(raiz().querySelector('#sede')).toBeNull();
      http.expectNone(URL_SEDES);
    });
  });
});
