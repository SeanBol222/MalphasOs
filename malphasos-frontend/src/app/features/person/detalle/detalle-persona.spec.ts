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

  it('una persona retirada no ofrece ninguna escritura', async () => {
    await abrir(persona({ estadoActivo: false }));

    expect(botones()).toEqual([]);
    expect(texto()).toContain('Retirada');
  });
});
