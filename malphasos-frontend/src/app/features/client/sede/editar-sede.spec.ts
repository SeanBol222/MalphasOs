import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, atenderRefresco, responderA } from '../../../../testing/pantalla';
import { EditarSede } from './editar-sede';

const ID = 's1';
const ID_CIUDAD = '22222222-2222-2222-2222-222222222222';
const OTRA_CIUDAD = '33333333-3333-3333-3333-333333333333';

const URL = `http://localhost:8081/v1/api/headquarters/${ID}`;
const URL_CIUDADES = 'http://localhost:8081/v1/api/cities';

const SEDE = {
  id: ID,
  nombre: 'Sede Norte',
  idCliente: '11111111-1111-1111-1111-111111111111',
  idCiudad: ID_CIUDAD,
  calle: '100',
  carrera: '15',
  numero: '20-30',
  estadoActivo: true,
};

const CIUDADES = [
  { id: ID_CIUDAD, nombre: 'Bogotá', idPais: 'co', estadoActivo: true },
  { id: OTRA_CIUDAD, nombre: 'Lima', idPais: 'pe', estadoActivo: true },
];

@Component({ selector: 'app-ficha-sede-falsa', template: '' })
class FichaSedeFalsa {}

describe('Edicion de una sede', () => {
  let fixture: ComponentFixture<EditarSede>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'sedes/:id', component: FichaSedeFalsa }]),
      ],
    });
    fixture = TestBed.createComponent(EditarSede);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;

  async function abrir(): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL, SEDE);
    await responderA(fixture, http, URL_CIUDADES, CIUDADES);
  }

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  async function enviar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);
  }

  it('llega con los datos de la sede ya puestos', async () => {
    await abrir();

    expect(raiz().querySelector<HTMLInputElement>('#nombre')!.value).toBe('Sede Norte');
    expect(raiz().querySelector<HTMLSelectElement>('#idCiudad')!.value).toBe(ID_CIUDAD);
    expect(raiz().querySelector<HTMLInputElement>('#calle')!.value).toBe('100');
  });

  it('ofrece todas las ciudades y no solo las del pais: corregir una mal elegida es el caso', async () => {
    // Al dar de alta se recortan por el pais del cliente; aqui el cliente no se consulta, y recortar
    // con un dato que no se tiene esconderia justo la ciudad correcta.
    await abrir();

    const opciones = [...raiz().querySelectorAll<HTMLOptionElement>('#idCiudad option')];

    expect(opciones.map((o) => o.textContent?.trim())).toEqual([
      'Elija una ciudad',
      'Bogotá',
      'Lima',
    ]);
  });

  it('manda un PATCH con los cinco campos que el contrato admite', async () => {
    await abrir();
    escribir('nombre', 'Sede Principal');
    await enviar();

    const peticion = http.expectOne({ method: 'PATCH', url: URL });

    expect(peticion.request.body).toEqual({
      nombre: 'Sede Principal',
      idCiudad: ID_CIUDAD,
      calle: '100',
      carrera: '15',
      numero: '20-30',
    });
    peticion.flush(SEDE);
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL, SEDE);
  });

  it('al guardar vuelve a la ficha de la sede', async () => {
    await abrir();
    escribir('nombre', 'Sede Principal');
    await enviar();
    http.expectOne({ method: 'PATCH', url: URL }).flush(SEDE);
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL, SEDE);

    expect(TestBed.inject(Router).url).toBe(`/sedes/${ID}`);
  });

  it('un nombre vacio no llega al servidor', async () => {
    await abrir();
    escribir('nombre', '');
    await enviar();

    http.expectNone({ method: 'PATCH', url: URL });
  });

  afterEach(() => http.verify());
});
