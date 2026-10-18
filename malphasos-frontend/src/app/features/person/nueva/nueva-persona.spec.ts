import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import { ClaseDeAlta, NuevaPersona } from './nueva-persona';

const URL = 'http://localhost:8081/v1/api/persons';
const ID_CLIENTE = '33333333-3333-3333-3333-333333333333';
const URL_CLIENTE = `http://localhost:8081/v1/api/clients/${ID_CLIENTE}`;

@Component({ selector: 'app-listado-falso', template: '' })
class ListadoFalso {}

describe('Alta de una persona', () => {
  let fixture: ComponentFixture<NuevaPersona>;
  let http: HttpTestingController;

  function montar(clase: ClaseDeAlta, cliente?: string): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['super.person.write', 'person.write'] }),
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
    // Y la segunda funcion NO viaja si no se marco la casilla. Sin esta linea, mandarla siempre
    // convertia a todo el mundo en encargado de un cliente sin que ninguna prueba se enterara.
    expect(peticion.request.body.segundoTipoPersona).toBeUndefined();
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

  it('marcar «también es encargado» viaja como la segunda función', async () => {
    montar('representante');
    rellenarLoComun();
    escribir('nombreUsuario', 'repre');
    escribir('password', 'contrasena-de-12');
    escribir('correo-0', 'repre@hospital.co');
    const casilla = raiz().querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    casilla.click();
    fixture.detectChanges();

    enviar();
    await asentar(fixture);

    const peticion = http.expectOne(`${URL}/ceo-clients`);
    expect(peticion.request.body.segundoTipoPersona).toBe('MANAGER');
    peticion.flush({ identificador: 'p9' });
    await asentar(fixture);
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
});
