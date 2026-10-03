import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import { Persona } from '../../../core/api/tipos';
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
    tipoPersona: 'MANAGER',
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

  afterEach(() => http?.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const botones = () =>
    [...raiz().querySelectorAll('button')].map((b) => (b.textContent ?? '').trim());

  async function abrir(datos: Persona = persona()): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, `${URL}/${ID}`, datos);
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
    await abrir(persona({ segundoTipoPersona: 'CEO_CLIENT' }));

    expect(texto()).toContain('Grace Hopper');
    expect(texto()).toContain('1010101010');
    expect(texto()).toContain('Encargado del cliente');
    expect(texto()).toContain('Representante del cliente');
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
    await abrir(persona({ tipoPersona: 'MANAGER' }));

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
    await abrir(persona({ tipoPersona: 'ENGINEER', segundoTipoPersona: 'MANAGER' }));
    pulsar('Editar datos');
    escribir('primerNombre', 'Grace Brewster');

    pulsar('Guardar');
    await asentar(fixture);

    const peticion = http.expectOne(`${URL}/${ID}`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body.primerNombre).toBe('Grace Brewster');
    expect(peticion.request.body.cedula).toBe('1010101010');
    expect(peticion.request.body.tipoPersona).toBe('ENGINEER');
    expect(peticion.request.body.segundoTipoPersona).toBe('MANAGER');
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
});
