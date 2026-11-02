import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import { CuentaDePersona, Persona } from '../../../core/api/tipos';
import { DetallePersona } from './detalle-persona';

const URL = 'http://localhost:8081/v1/api/persons';
const ID = 'p1';

@Component({ selector: 'app-listado-falso', template: '' })
class ListadoFalso {}

/** Una persona del tipo que se pida, con los contactos que se pidan. */
function persona(cambios: Partial<Persona> = {}): Persona {
  return {
    identificador: ID,
    cedula: '1010101010',
    primerNombre: 'Grace',
    primerApellido: 'Hopper',
    tipoPersona: 'SUPERVISOR',
    estadoActivo: true,
    emailPersonList: [],
    phonePersonList: [],
    ...cambios,
  };
}

describe('Ficha de una persona', () => {
  let fixture: ComponentFixture<DetallePersona>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[]): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([{ path: 'personas', component: ListadoFalso }]),
      ],
    });
    fixture = TestBed.createComponent(DetallePersona);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID);
  }

  beforeEach(() => montar(['person.read', 'person.write', 'super.person.write']));

  // Toda escritura sobre la persona recarga también su cuenta, que cuelga de la ficha en la caché. Las
  // pruebas que no van de la cuenta no tienen por qué responderla: se contesta aquí, y verify() sigue
  // vigilando todo lo demás.
  afterEach(() => {
    http?.match(`${URL}/${ID}/account`).forEach((p) => p.flush({ tieneCuenta: true }));
    http?.verify();
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const botones = () =>
    [...raiz().querySelectorAll('button')].map((b) => (b.textContent ?? '').trim());

  /** La ficha y su cuenta, que son dos consultas: la cuenta vive en Keycloak, no en la base. */
  async function abrir(
    datos: Persona = persona(),
    cuenta: CuentaDePersona = { tieneCuenta: true, nombreUsuario: 'grace' },
  ): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, `${URL}/${ID}`, datos);
    await responderA(fixture, http, `${URL}/${ID}/account`, cuenta);
  }

  function pulsar(etiqueta: string, dentro: ParentNode = raiz()): void {
    const boton = [...dentro.querySelectorAll('button')].find(
      (b) => (b.textContent ?? '').trim() === etiqueta,
    );

    if (!boton) {
      throw new Error(
        `No hay ningun boton que diga exactamente "${etiqueta}". Hay: ` +
          [...dentro.querySelectorAll('button')]
            .map((b) => `"${(b.textContent ?? '').trim()}"`)
            .join(', '),
      );
    }

    (boton as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  /**
   * La seccion cuyo titulo coincide, para poder pulsar DENTRO de ella.
   *
   * <p>Hace falta porque hay tres botones «Retirar» en la pantalla —el de la persona, el de un correo y
   * el de un telefono— y dos que empiezan por «Añadir». La primera version de estas pruebas buscaba por
   * texto parcial en toda la pagina y <b>pulsaba el equivocado</b>: pedia retirar un correo y retiraba
   * a la persona. Lo delataron dos pruebas que esperaban una peticion que nunca salia.
   */
  function seccion(titulo: string): HTMLElement {
    const encontrada = [...raiz().querySelectorAll('section')].find((s) =>
      (s.querySelector('h2')?.textContent ?? '').includes(titulo),
    );

    if (!encontrada) {
      throw new Error(`No hay ninguna seccion titulada "${titulo}"`);
    }

    return encontrada as HTMLElement;
  }

  function escribir(id: string, valor: string): void {
    const control = raiz().querySelector<HTMLInputElement>(`#${id}`)!;
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('enseña el nombre, la cédula, la función y el estado', async () => {
    await abrir();

    expect(texto()).toContain('Grace Hopper');
    expect(texto()).toContain('1010101010');
    expect(texto()).toContain('Supervisor del cliente');
    expect(texto()).toContain('Activa');
  });

  it('solo pinta los correos y teléfonos vigentes', async () => {
    // Un contacto retirado se queda en la base; pintarlo como vigente seria mentir sobre a quien se
    // puede llamar.
    await abrir(
      persona({
        emailPersonList: [
          { idCorreoPersona: 'c1', correoPersona: 'vive@hospital.co', estadoActivo: true },
          { idCorreoPersona: 'c2', correoPersona: 'retirado@hospital.co', estadoActivo: false },
        ],
      }),
    );

    expect(texto()).toContain('vive@hospital.co');
    expect(texto()).not.toContain('retirado@hospital.co');
  });

  it('con person.write se puede tocar a alguien del cliente', async () => {
    TestBed.resetTestingModule();
    montar(['person.read', 'person.write']);
    await abrir(persona({ tipoPersona: 'SUPERVISOR' }));

    expect(botones()).toContain('Editar datos');
    expect(botones()).toContain('Retirar');
  });

  it('pero no a un ingeniero: eso está un escalón por encima', async () => {
    // Es la escalera del backend, que decide por el TIPO de la fila y no por la ruta. Con person.write
    // se puede con la gente del cliente y no con la de la casa.
    TestBed.resetTestingModule();
    montar(['person.read', 'person.write']);
    await abrir(persona({ tipoPersona: 'ENGINEER' }));

    expect(botones()).not.toContain('Editar datos');
    expect(botones()).not.toContain('Retirar');
    expect(texto()).toContain('un escalón por encima');
  });

  it('con super.person.write sí se puede con un ingeniero', async () => {
    TestBed.resetTestingModule();
    montar(['person.read', 'super.person.write']);
    await abrir(persona({ tipoPersona: 'ENGINEER' }));

    expect(botones()).toContain('Editar datos');
  });

  it('el formulario no ofrece cambiar la función, y dice por qué', async () => {
    // Cambiarla no mueve la cuenta de grupo en Keycloak: quien dejara de ser ingeniero conservaria sus
    // permisos. Ofrecer el campo seria ofrecer una accion que el servidor no completa.
    await abrir();
    pulsar('Editar datos');

    expect(raiz().querySelectorAll('select')).toHaveLength(0);
    expect(texto()).toContain('no movería la cuenta de grupo');
  });

  it('guardar manda la persona entera, incluido el tipo que no se tocó', async () => {
    // Es un PUT: lo que no viaje se pierde. El tipo va tal como estaba.
    await abrir(persona({ tipoPersona: 'ENGINEER' }));
    pulsar('Editar datos');
    escribir('primerNombre', 'Grace Brewster');

    pulsar('Guardar');
    await asentar(fixture);

    const peticion = http.expectOne(`${URL}/${ID}`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body.primerNombre).toBe('Grace Brewster');
    expect(peticion.request.body.cedula).toBe('1010101010');
    expect(peticion.request.body.tipoPersona).toBe('ENGINEER');
    peticion.flush(persona());
    await asentar(fixture);
    http.match(`${URL}/${ID}`).forEach((p) => p.flush(persona()));
    await asentar(fixture);
  });

  it('retirar avisa de que la sesión abierta sigue valiendo unos minutos', async () => {
    // El backend deshabilita la cuenta, pero un token ya emitido abre el API hasta que caduca. Dar a
    // entender que el acceso se corta en el acto seria falso.
    await abrir();
    pulsar('Retirar');

    expect(texto()).toContain('seguirá valiendo unos minutos');
  });

  it('al retirar vuelve al listado', async () => {
    const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
    await abrir();
    pulsar('Retirar');
    pulsar('Confirmar retiro');
    await asentar(fixture);

    const peticion = http.expectOne(`${URL}/${ID}`);
    expect(peticion.request.method).toBe('DELETE');
    peticion.flush(null);
    await asentar(fixture);

    expect(viaje).toHaveBeenCalledWith(['/personas']);
    http.match(`${URL}/${ID}`).forEach((p) => p.flush(persona()));
    await asentar(fixture);
  });

  describe('Correos y teléfonos', () => {
    const CON_CONTACTOS = persona({
      emailPersonList: [
        { idCorreoPersona: 'c1', correoPersona: 'grace@armada.mil', estadoActivo: true },
      ],
      phonePersonList: [
        { idTelefonoPersona: 't1', telefonoPersona: '3001234567', estadoActivo: true },
      ],
    });

    it('añadir un correo llama a su sub-recurso, no al cuerpo de la persona', async () => {
      // El API no admite mandar la lista completa: cada contacto es su propia llamada.
      await abrir(CON_CONTACTOS);
      pulsar('Añadir correo', seccion('Correos'));
      escribir('correoNuevo', 'grace@yale.edu');

      pulsar('Añadir', seccion('Correos'));
      await asentar(fixture);

      const peticion = http.expectOne(`${URL}/${ID}/emails`);
      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({ correoPersona: 'grace@yale.edu' });
      peticion.flush({ idCorreoPersona: 'c2' });
      await asentar(fixture);
      http.match(`${URL}/${ID}`).forEach((p) => p.flush(CON_CONTACTOS));
      await asentar(fixture);
    });

    it('un correo mal escrito no llega al servidor', async () => {
      await abrir(CON_CONTACTOS);
      pulsar('Añadir correo', seccion('Correos'));
      escribir('correoNuevo', 'esto-no-es-un-correo');

      pulsar('Añadir', seccion('Correos'));
      await asentar(fixture);

      http.expectNone(`${URL}/${ID}/emails`);
    });

    it('retirar un correo manda su identificador, y no borra la fila', async () => {
      await abrir(CON_CONTACTOS);

      // Acotado a la seccion: el «Retirar» de la persona es otro boton con el mismo texto.
      pulsar('Retirar', seccion('Correos'));
      await asentar(fixture);

      const peticion = http.expectOne(`${URL}/${ID}/emails/c1`);
      expect(peticion.request.method).toBe('DELETE');
      peticion.flush(null);
      await asentar(fixture);
      http.match(`${URL}/${ID}`).forEach((p) => p.flush(CON_CONTACTOS));
      await asentar(fixture);
    });

    it('añadir un teléfono va a la ruta de teléfonos', async () => {
      await abrir(CON_CONTACTOS);
      pulsar('Añadir teléfono', seccion('Teléfonos'));
      escribir('telefonoNuevo', '3109876543');

      pulsar('Añadir', seccion('Teléfonos'));
      await asentar(fixture);

      const peticion = http.expectOne(`${URL}/${ID}/phones`);
      expect(peticion.request.body).toEqual({ telefonoPersona: '3109876543' });
      peticion.flush({ idTelefonoPersona: 't2' });
      await asentar(fixture);
      http.match(`${URL}/${ID}`).forEach((p) => p.flush(CON_CONTACTOS));
      await asentar(fixture);
    });

    it('sin permiso sobre esta persona no se ofrece tocar sus contactos', async () => {
      TestBed.resetTestingModule();
      montar(['person.read', 'person.write']);
      await abrir(
        persona({
          tipoPersona: 'ENGINEER',
          emailPersonList: [
            { idCorreoPersona: 'c1', correoPersona: 'grace@armada.mil', estadoActivo: true },
          ],
        }),
      );

      // El correo se ve -es informacion-, pero no hay con que tocarlo.
      expect(texto()).toContain('grace@armada.mil');
      expect(botones()).not.toContain('Añadir correo');
      expect(botones()).not.toContain('Retirar');
    });
  });

  it('una persona retirada no ofrece ninguna escritura', async () => {
    await abrir(persona({ estadoActivo: false }));

    expect(botones()).toEqual([]);
    expect(texto()).toContain('Retirada');
  });

  describe('El correo de la cuenta (V40)', () => {
    // Con él se puede entrar y a él van los avisos de Keycloak: es el único que viaja a la cuenta.
    const CON_CUENTA = persona({
      tipoPersona: 'ENGINEER',
      emailPersonList: [
        {
          idCorreoPersona: 'c1',
          correoPersona: 'grace@armada.mil',
          estadoActivo: true,
          principal: true,
        },
        {
          idCorreoPersona: 'c2',
          correoPersona: 'grace@casa.mil',
          estadoActivo: true,
          principal: false,
        },
      ],
    });

    const filaDe = (correo: string) =>
      [...seccion('Correos').querySelectorAll('li')].find((li) =>
        li.textContent?.includes(correo),
      )!;
    const botonesDe = (correo: string) =>
      [...filaDe(correo).querySelectorAll('button')].map((b) => (b.textContent ?? '').trim());

    it('lo dice en palabras, y no se ofrece retirarlo', async () => {
      await abrir(CON_CUENTA);

      expect(filaDe('grace@armada.mil').hasAttribute('data-correo-de-cuenta')).toBe(true);
      expect(filaDe('grace@armada.mil').textContent).toContain('Correo de la cuenta');
      expect(botonesDe('grace@armada.mil')).toEqual([]);
      expect(filaDe('grace@armada.mil').textContent).toContain('elija antes otro para la cuenta');
    });

    it('los demás se pueden usar para la cuenta o retirar', async () => {
      await abrir(CON_CUENTA);

      expect(botonesDe('grace@casa.mil')).toEqual(['Usar para la cuenta', 'Retirar']);
    });

    it('usar otro para la cuenta es un PATCH sobre su sub-recurso', async () => {
      await abrir(CON_CUENTA);

      pulsar('Usar para la cuenta', filaDe('grace@casa.mil'));
      await asentar(fixture);

      const peticion = http.expectOne(`${URL}/${ID}/emails/c2/primary`);
      expect(peticion.request.method).toBe('PATCH');
      peticion.flush(CON_CUENTA);
      await asentar(fixture);
      http.match(`${URL}/${ID}`).forEach((p) => p.flush(CON_CUENTA));
      await asentar(fixture);
    });

    it('si otra cuenta ya usa ese correo, lo explica', async () => {
      await abrir(CON_CUENTA);

      pulsar('Usar para la cuenta', filaDe('grace@casa.mil'));
      await asentar(fixture);
      http
        .expectOne(`${URL}/${ID}/emails/c2/primary`)
        .flush(
          { code: 'ERR_KEYCLOAK_005', message: 'Another account already uses that email' },
          { status: 409, statusText: 'Conflict' },
        );
      await asentar(fixture);

      expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
        'Otra cuenta ya usa ese correo',
      );
    });

    it('a un supervisor también se le ofrece: desde V53 toda persona tiene cuenta', async () => {
      await abrir({ ...CON_CUENTA, tipoPersona: 'SUPERVISOR' });

      expect(botonesDe('grace@casa.mil')).toEqual(['Usar para la cuenta', 'Retirar']);
    });
  });

  describe('Restablecer la contraseña (2026-10-17)', () => {
    // Sin servidor de correo, es como se resuelve una contraseña olvidada.
    const INGENIERA = persona({ tipoPersona: 'ENGINEER' });
    const acceso = () => raiz().querySelector('section[aria-labelledby="titulo-acceso"]');

    async function ponerTemporal(valor: string): Promise<void> {
      pulsar('Restablecer contraseña');
      const campo = raiz().querySelector<HTMLInputElement>('#contrasenaTemporal')!;
      campo.value = valor;
      campo.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      pulsar('Poner contraseña temporal');
      await asentar(fixture);
    }

    it('manda la temporal a su ruta y dice que tendrá que cambiarla', async () => {
      await abrir(INGENIERA);

      await ponerTemporal('temporal-de-doce');

      const peticion = http.expectOne(`${URL}/${ID}/password`);
      expect(peticion.request.method).toBe('PATCH');
      expect(peticion.request.body).toEqual({ password: 'temporal-de-doce' });
      peticion.flush(null, { status: 204, statusText: 'No Content' });
      await asentar(fixture);

      expect(acceso()?.textContent).toContain('tendrá que cambiarla');
      expect(raiz().querySelector('#contrasenaTemporal')).toBeNull();
    });

    it('una de once caracteres no llega: la política del sistema de acceso pide doce', async () => {
      await abrir(INGENIERA);

      await ponerTemporal('once-letras');

      http.expectNone(`${URL}/${ID}/password`);
      expect(acceso()?.textContent).toContain('al menos 12 caracteres');
    });

    it('si el sistema de acceso la rechaza, lo explica', async () => {
      await abrir(INGENIERA);
      await ponerTemporal('temporal-de-doce');

      http
        .expectOne(`${URL}/${ID}/password`)
        .flush(
          { code: 'ERR_KEYCLOAK_006', message: 'The password does not meet the policy' },
          { status: 400, statusText: 'Bad Request' },
        );
      await asentar(fixture);

      expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
        'rechazó la contraseña',
      );
    });

    it('a una persona retirada tampoco: su cuenta está deshabilitada', async () => {
      await abrir(persona({ tipoPersona: 'ENGINEER', estadoActivo: false }));

      expect(acceso()).toBeNull();
    });

    it('sin el escalón que pide esta persona no se ofrece', async () => {
      // Restablecerla es tomar su cuenta: la misma escalera que editarla.
      TestBed.resetTestingModule();
      montar(['person.read', 'person.write']);
      await abrir(INGENIERA);

      expect(acceso()).toBeNull();
    });
  });

  describe('Dar acceso a quien no tiene cuenta (2026-11-02)', () => {
    // Las fichas que eran encargados hasta V53: supervisores sin cuenta, que no pueden entrar.
    const SIN_CUENTA: CuentaDePersona = { tieneCuenta: false };
    const CON_CORREO = persona({
      emailPersonList: [
        { idCorreoPersona: 'c1', correoPersona: 'grace@hospital.co', estadoActivo: true },
      ],
    });
    const acceso = () => raiz().querySelector('section[aria-labelledby="titulo-acceso"]')!;

    async function crearCuenta(usuario: string, contrasena: string): Promise<void> {
      pulsar('Dar acceso', acceso());
      escribir('nombreUsuarioNuevo', usuario);
      escribir('contrasenaDeLaCuentaNueva', contrasena);
      pulsar('Crear la cuenta', acceso());
      await asentar(fixture);
    }

    it('dice que no tiene cuenta, ofrece dársela y no ofrece restablecer una contraseña que no existe', async () => {
      await abrir(CON_CORREO, SIN_CUENTA);

      expect(acceso().querySelector('[data-sin-cuenta]')).not.toBeNull();
      expect(acceso().querySelector('[data-correo-nuevo]')?.textContent).toContain(
        'grace@hospital.co',
      );
      expect(botones()).toContain('Dar acceso');
      expect(botones()).not.toContain('Restablecer contraseña');
    });

    it('si la ficha ya marca un correo de la cuenta, dice que entrará con ese', async () => {
      // Es el que el servidor manda a Keycloak: el marcado, aunque no sea el primero de la lista.
      await abrir(
        persona({
          emailPersonList: [
            { idCorreoPersona: 'c1', correoPersona: 'primero@hospital.co', estadoActivo: true },
            {
              idCorreoPersona: 'c2',
              correoPersona: 'marcado@hospital.co',
              estadoActivo: true,
              principal: true,
            },
          ],
        }),
        SIN_CUENTA,
      );

      expect(acceso().querySelector('[data-correo-nuevo]')?.textContent).toContain(
        'marcado@hospital.co',
      );
    });

    it('con cuenta dice con qué usuario entra y no ofrece darla', async () => {
      await abrir(CON_CORREO, { tieneCuenta: true, nombreUsuario: 'ghopper' });

      expect(acceso().querySelector('[data-usuario]')?.textContent).toContain('ghopper');
      expect(acceso().querySelector('[data-sin-cuenta]')).toBeNull();
      expect(botones()).not.toContain('Dar acceso');
      expect(botones()).toContain('Restablecer contraseña');
    });

    it('sin correos no se ofrece, y dice que hace falta uno', async () => {
      await abrir(persona(), SIN_CUENTA);

      expect(botones()).not.toContain('Dar acceso');
      expect(acceso().textContent).toContain('Añada antes un correo');
    });

    it('crea la cuenta y lleva a la ficha con su identificador nuevo, sin dejar la vieja en el historial', async () => {
      await abrir(CON_CORREO, SIN_CUENTA);
      const router = TestBed.inject(Router);
      const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      await crearCuenta('  ghopper ', 'temporal-de-doce');

      const peticion = http.expectOne(`${URL}/${ID}/account`);
      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({
        nombreUsuario: 'ghopper',
        password: 'temporal-de-doce',
      });
      peticion.flush(persona({ identificador: 'p-nuevo' }));
      await asentar(fixture);
      http.match(`${URL}/${ID}`).forEach((p) => p.flush(CON_CORREO));
      await asentar(fixture);

      expect(navegar).toHaveBeenCalledWith(['/personas', 'p-nuevo'], { replaceUrl: true });
      expect(acceso().textContent).toContain('Cuenta creada');
      expect(raiz().querySelector('#nombreUsuarioNuevo')).toBeNull();
    });

    it('sin usuario o con una contraseña corta no llega al servidor', async () => {
      await abrir(CON_CORREO, SIN_CUENTA);

      await crearCuenta('', 'temporal-de-doce');
      escribir('nombreUsuarioNuevo', 'ghopper');
      escribir('contrasenaDeLaCuentaNueva', 'once-letras');
      pulsar('Crear la cuenta', acceso());
      await asentar(fixture);

      http.expectNone((r) => r.method === 'POST');
      expect(acceso().textContent).toContain('al menos 12 caracteres');
    });

    it('si el usuario ya existe en el sistema de acceso, lo explica', async () => {
      await abrir(CON_CORREO, SIN_CUENTA);
      await crearCuenta('ghopper', 'temporal-de-doce');

      http
        .expectOne((r) => r.method === 'POST')
        .flush(
          { code: 'ERR_KEYCLOAK_001', message: 'Keycloak user already exists' },
          { status: 409, statusText: 'Conflict' },
        );
      await asentar(fixture);

      expect(raiz().querySelector('[role="alert"]')).not.toBeNull();
      expect(raiz().querySelector('#nombreUsuarioNuevo')).not.toBeNull();
    });

    it('sin el escalón que pide esta persona no se ofrece', async () => {
      TestBed.resetTestingModule();
      montar(['person.read', 'person.write']);
      await abrir(persona({ ...CON_CORREO, tipoPersona: 'ENGINEER' }), SIN_CUENTA);

      expect(botones()).not.toContain('Dar acceso');
    });
  });
});
