import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, responderA } from '../../../../testing/pantalla';
import { NuevaSede } from './nueva-sede';

const ID_CLIENTE = '11111111-1111-1111-1111-111111111111';
const COLOMBIA = '22222222-2222-2222-2222-222222222222';
const PERU = '33333333-3333-3333-3333-333333333333';

const URL_CLIENTE = `http://localhost:8081/v1/api/clients/${ID_CLIENTE}`;
const URL_SEDES = `${URL_CLIENTE}/headquarters`;
const URL_CIUDADES = 'http://localhost:8081/v1/api/cities';

const CLIENTE = { id: ID_CLIENTE, razonSocial: 'Hospital Central', idPais: COLOMBIA };

const CIUDADES = [
  { id: 'c1', nombre: 'Bogotá', idPais: COLOMBIA, estadoActivo: true },
  { id: 'c2', nombre: 'Medellín', idPais: COLOMBIA, estadoActivo: true },
  { id: 'c3', nombre: 'Lima', idPais: PERU, estadoActivo: true },
];

@Component({ selector: 'app-ficha-sede-falsa', template: '' })
class FichaSedeFalsa {}

describe('Alta de una sede', () => {
  let fixture: ComponentFixture<NuevaSede>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'sedes/:id', component: FichaSedeFalsa }]),
      ],
    });
    fixture = TestBed.createComponent(NuevaSede);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_CLIENTE);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(cliente: object = CLIENTE, ciudades: object = CIUDADES): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_CLIENTE, cliente);
    await responderA(fixture, http, URL_CIUDADES, ciudades);
  }

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function rellenarValido(): void {
    escribir('nombre', 'Sede Norte');
    escribir('idCiudad', 'c1');
    escribir('calle', '100');
    escribir('carrera', '15');
    escribir('numero', '20-30');
  }

  async function enviar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);
  }

  const ciudadesOfrecidas = () =>
    [...raiz().querySelectorAll<HTMLOptionElement>('#idCiudad option')].map((o) =>
      o.textContent?.trim(),
    );

  it('dice de que cliente es la sede', async () => {
    await abrir();

    expect(texto()).toContain('Hospital Central');
  });

  it('ofrece solo las ciudades del pais del cliente', async () => {
    // Es lo que evita el error que nadie detecta hasta que un ingeniero viaja: abrir una sede de un
    // cliente colombiano en Lima porque las tres ciudades estaban en la misma lista.
    await abrir();

    expect(ciudadesOfrecidas()).toEqual(['Elija una ciudad', 'Bogotá', 'Medellín']);
  });

  it('si el cliente no tiene pais, ofrece todas: no hay con que recortar', async () => {
    await abrir({ ...CLIENTE, idPais: undefined });

    expect(ciudadesOfrecidas()).toEqual(['Elija una ciudad', 'Bogotá', 'Medellín', 'Lima']);
  });

  it('si el pais del cliente no tiene ciudades registradas, lo dice', async () => {
    // Un desplegable con una sola opcion vacia no explica nada, y el usuario no puede adivinar que
    // lo que falta esta en otro sitio.
    await abrir({ ...CLIENTE, idPais: PERU }, [CIUDADES[0], CIUDADES[1]]);

    expect(texto()).toContain('No hay ciudades registradas');
  });

  it('manda exactamente lo que el contrato declara', async () => {
    await abrir();
    rellenarValido();
    await enviar();

    const peticion = http.expectOne({ method: 'POST', url: URL_SEDES });

    expect(peticion.request.body).toEqual({
      nombre: 'Sede Norte',
      idCiudad: 'c1',
      calle: '100',
      carrera: '15',
      numero: '20-30',
    });
    peticion.flush({ id: 's1' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('sin ciudad elegida no llega al servidor, y lo dice', async () => {
    await abrir();
    escribir('nombre', 'Sede Norte');
    escribir('calle', '100');
    escribir('carrera', '15');
    escribir('numero', '20-30');
    await enviar();

    http.expectNone({ method: 'POST', url: URL_SEDES });
    expect(texto()).toContain('Elija la ciudad de la sede');
  });

  it('sin direccion completa tampoco, y se dice una vez y no tres', async () => {
    await abrir();
    escribir('nombre', 'Sede Norte');
    escribir('idCiudad', 'c1');
    await enviar();

    http.expectNone({ method: 'POST', url: URL_SEDES });
    expect(texto()).toContain('La dirección necesita calle, carrera y número');
  });

  it('al guardar, lleva a la ficha de la sede creada', async () => {
    await abrir();
    rellenarValido();
    await enviar();
    http.expectOne({ method: 'POST', url: URL_SEDES }).flush({ id: 's1' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(TestBed.inject(Router).url).toBe('/sedes/s1');
  });

  describe('Lo que WCAG exige y no se ve', () => {
    it('cada campo tiene su etiqueta asociada', async () => {
      await abrir();

      for (const id of ['nombre', 'idCiudad', 'calle', 'carrera', 'numero']) {
        expect(raiz().querySelector(`label[for="${id}"]`)).toBeTruthy();
      }
    });

    it('los controles respetan el area tactil minima', async () => {
      await abrir();

      for (const control of raiz().querySelectorAll('input, select, button, form a')) {
        expect(control.className).toContain('min-h-tactil');
      }
    });
  });

  afterEach(() => http.verify());
});
