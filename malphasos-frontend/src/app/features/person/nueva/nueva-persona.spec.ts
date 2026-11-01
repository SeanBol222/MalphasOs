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
import { ClaseDeAlta, NuevaPersona } from './nueva-persona';

const URL = 'http://localhost:8081/v1/api/persons';
const ID_CLIENTE = '33333333-3333-3333-3333-333333333333';
const URL_CLIENTE = `http://localhost:8081/v1/api/clients/${ID_CLIENTE}`;
const URL_CLIENTES = 'http://localhost:8081/v1/api/clients';

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

  /** Lo mínimo que cualquiera de las tres altas necesita, sin la cuenta. */
  function rellenarLoComun(): void {
    escribir('cedula', '1010101010');
    escribir('primerNombre', 'Grace');
    escribir('primerApellido', 'Hopper');
  }

  it('las tres altas piden cuenta, también la del supervisor (V53): ninguna puerta deja a nadie sin usuario', () => {
    for (const clase of ['ingeniero', 'administrador', 'supervisor'] as const) {
      TestBed.resetTestingModule();
      montar(clase);

      expect(campo('nombreUsuario'), clase).not.toBeNull();
      expect(campo('password'), clase).not.toBeNull();
      // Y ninguna ofrece elegir el tipo: lo dice la ruta.
      expect(raiz().querySelectorAll('select'), clase).toHaveLength(0);
    }
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
    montar('supervisor');
    rellenarLoComun();
    escribir('nombreUsuario', 'ghopper');
    escribir('password', 'contrasena-de-12');
    escribir('correo-0', 'grace@armada.mil');

    enviar();
    await asentar(fixture);

    const peticion = http.expectOne(`${URL}/supervisors`);
    expect(peticion.request.body.segundoNombre).toBeUndefined();
    expect(peticion.request.body.segundoApellido).toBeUndefined();
    expect(peticion.request.body.phonePersonList).toEqual([]);
    peticion.flush({ identificador: 'p9' });
    await asentar(fixture);
  });

  it('el alta de un ingeniero no ofrece cliente ni cargo: solo supervisa la gente del cliente', async () => {
    montar('ingeniero', undefined, [
      'super.person.write',
      'person.write',
      'client.read',
      'client.write',
    ]);
    fixture.detectChanges();

    expect(raiz().querySelector('#clienteElegido')).toBeNull();
    expect(campo('cargo')).toBeNull();
    http.expectNone(URL_CLIENTES);
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
    montar('administrador');
    const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
    rellenarLoComun();
    escribir('nombreUsuario', 'jefe');
    escribir('password', 'contrasena-de-12');
    escribir('correo-0', 'jefe@casa.local');
    enviar();
    await asentar(fixture);

    http.expectOne(`${URL}/admins`).flush({ identificador: 'p9' });
    await asentar(fixture);

    expect(viaje).toHaveBeenCalledWith(['/personas']);
  });

  describe('Un supervisor se registra para un cliente', () => {
    // Sin nombrarlo, su cuenta entra y no ve nada: el filtrado por dueño lo acota a sus clientes.
    function rellenarConCuenta(): void {
      rellenarLoComun();
      escribir('nombreUsuario', 'ghopper');
      escribir('password', 'contrasena-de-12');
      escribir('correo-0', 'grace@armada.mil');
    }

    it('entrando desde la ficha dice de qué cliente, lo nombra al crearlo y vuelve a la ficha', async () => {
      montar('supervisor', ID_CLIENTE);
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
      await asentar(fixture);
      http.expectOne(URL_CLIENTE).flush({ id: ID_CLIENTE, razonSocial: 'Hospital Central' });
      await asentar(fixture);

      expect(texto()).toContain('Se nombrará supervisor de Hospital Central');

      rellenarConCuenta();
      escribir('cargo', '  Ingeniero biomédico ');
      enviar();
      await asentar(fixture);
      http.expectOne(`${URL}/supervisors`).flush({ identificador: 'p9' });
      await asentar(fixture);

      // Hasta que no se nombra no se sale: salir antes dejaba la cuenta sin nada que ver.
      expect(viaje).not.toHaveBeenCalled();
      const nombramiento = http.expectOne(`${URL_CLIENTE}/supervisors/p9`);
      expect(nombramiento.request.method).toBe('POST');
      // El cargo viaja con el nombramiento, recortado: es como sale en los documentos.
      expect(nombramiento.request.body).toEqual({ cargo: 'Ingeniero biomédico' });
      nombramiento.flush({ id: ID_CLIENTE, supervisores: [{ idPersona: 'p9' }] });
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
      montar('supervisor', ID_CLIENTE);
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
      await asentar(fixture);
      http.expectOne(URL_CLIENTE).flush({ id: ID_CLIENTE, razonSocial: 'Hospital Central' });
      rellenarConCuenta();
      enviar();
      await asentar(fixture);
      http.expectOne(`${URL}/supervisors`).flush({ identificador: 'p9' });
      await asentar(fixture);

      http
        .expectOne(`${URL_CLIENTE}/supervisors/p9`)
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
      montar('supervisor');
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
      rellenarConCuenta();
      enviar();
      await asentar(fixture);

      http.expectOne(`${URL}/supervisors`).flush({ identificador: 'p9' });
      await asentar(fixture);

      http.expectNone((r) => r.url.includes('/clients/'));
      expect(viaje).toHaveBeenCalledWith(['/personas']);
    });

    it('el cliente de la ruta no cuenta en otra alta: a un ingeniero no se le nombra supervisor', async () => {
      montar('ingeniero', ID_CLIENTE);
      rellenarConCuenta();
      enviar();
      await asentar(fixture);

      http.expectOne(`${URL}/engineers`).flush({ identificador: 'p9' });
      await asentar(fixture);

      http.expectNone((r) => r.url.includes('/clients/'));
      expect(texto()).not.toContain('Se nombrará supervisor');
    });
  });

  describe('El supervisor elige su cliente desde el listado (2026-10-26)', () => {
    const QUIEN_NOMBRA = ['person.write', 'client.read', 'client.write'];

    it('elegido, se le nombra al crearlo y se vuelve a la ficha del cliente', async () => {
      montar('supervisor', undefined, QUIEN_NOMBRA);
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
      await asentar(fixture);
      http.expectOne(URL_CLIENTES).flush(CLIENTES);
      await asentar(fixture);

      // Se predice escribiendo, y un cliente retirado no admite supervisores nuevos.
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
      http.expectOne(`${URL}/supervisors`).flush({ identificador: 'p9' });
      await asentar(fixture);

      http.expectOne(`${URL_CLIENTE}/supervisors/p9`).flush({ id: ID_CLIENTE });
      await asentar(fixture);
      http.match(URL_CLIENTES).forEach((p) => p.flush(CLIENTES));
      await asentar(fixture);

      expect(viaje).toHaveBeenCalledWith(['/clientes', ID_CLIENTE]);
    });

    it('un cliente que no existe se dice y no deja registrar: no se guarda sin él a escondidas', async () => {
      montar('supervisor', undefined, QUIEN_NOMBRA);
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
      http.expectNone(`${URL}/supervisors`);
    });

    it('sin elegir, se registra sin nombrar y vuelve al listado', async () => {
      montar('supervisor', undefined, QUIEN_NOMBRA);
      const viaje = vi.spyOn(TestBed.inject(Router), 'navigate');
      await asentar(fixture);
      http.expectOne(URL_CLIENTES).flush(CLIENTES);
      rellenarLoComun();
      escribir('nombreUsuario', 'ghopper');
      escribir('password', 'contrasena-de-12');
      escribir('correo-0', 'grace@armada.mil');
      enviar();
      await asentar(fixture);
      http.expectOne(`${URL}/supervisors`).flush({ identificador: 'p9' });
      await asentar(fixture);

      http.expectNone((r) => r.url.includes('/supervisors/'));
      expect(viaje).toHaveBeenCalledWith(['/personas']);
    });

    it('quien no puede nombrar no ve el selector, ni se piden los clientes', async () => {
      montar('supervisor', undefined, ['person.write', 'client.read']);
      await asentar(fixture);

      expect(raiz().querySelector('#clienteElegido')).toBeNull();
      http.expectNone(URL_CLIENTES);
    });
  });
});
