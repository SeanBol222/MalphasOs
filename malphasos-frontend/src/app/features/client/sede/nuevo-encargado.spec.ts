import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, responderA } from '../../../../testing/pantalla';
import { NuevoEncargado } from './nuevo-encargado';

const ID = 's1';
const URL_SEDE = `http://localhost:8081/v1/api/headquarters/${ID}`;
const URL_AREAS = `${URL_SEDE}/service-areas`;
const URL_ENCARGADOS = 'http://localhost:8081/v1/api/managers';

const SEDE = { id: ID, nombre: 'Sede Norte', idCliente: 'c1', estadoActivo: true };

const AREAS = [
  { id: 'a1', nombre: 'Urgencias', idSede: ID, estadoActivo: true },
  { id: 'a2', nombre: 'Bodega vieja', idSede: ID, estadoActivo: false },
];

@Component({ selector: 'app-ficha-sede-falsa', template: '' })
class FichaSedeFalsa {}

describe('Registro de un encargado', () => {
  let fixture: ComponentFixture<NuevoEncargado>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'sedes/:id', component: FichaSedeFalsa }]),
      ],
    });
    fixture = TestBed.createComponent(NuevoEncargado);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_SEDE, SEDE);
    await responderA(fixture, http, URL_AREAS, AREAS);
  }

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function rellenarValido(): void {
    escribir('asignacion', `HEADQUARTER:${ID}`);
    escribir('cedula', '1020304050');
    escribir('primerNombre', 'Ana');
    escribir('primerApellido', 'Ruiz');
    escribir('correo', 'ana@hospital.co');
  }

  async function enviar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);
  }

  it('dice que el encargado no recibe acceso a la aplicacion', async () => {
    // Es la razon por la que PersonType.MANAGER existe sin rol, y quien rellena esto no lo sabe:
    // sin decirlo, alguien esperaria que a esa persona le llegue una cuenta.
    await abrir();

    expect(texto()).toContain('no recibe acceso a la aplicación');
  });

  it('ofrece la sede y sus areas abiertas, cada una con su tipo pegado', async () => {
    // El contrato pide tipo e identificador por separado; ofrecerlos como dos campos permitiria
    // elegir "area de servicio" con el identificador de una sede, que es un error que este
    // formulario no deberia dejar cometer.
    await abrir();

    const opciones = [...raiz().querySelectorAll<HTMLOptionElement>('#asignacion option')];

    expect(opciones.map((o) => o.value)).toEqual(['', `HEADQUARTER:${ID}`, 'SERVICE_AREA:a1']);
    // Y el area cerrada no esta: el backend la rechazaria como referencia retirada.
    expect(texto()).not.toContain('Bodega vieja');
  });

  it('manda lo que el contrato declara, con el tipo y el identificador ya separados', async () => {
    await abrir();
    escribir('asignacion', 'SERVICE_AREA:a1');
    escribir('cedula', '1020304050');
    escribir('primerNombre', 'Ana');
    escribir('segundoNombre', 'María');
    escribir('primerApellido', 'Ruiz');
    escribir('correo', 'ana@hospital.co');
    escribir('telefono', '3001234567');
    await enviar();

    const peticion = http.expectOne({ method: 'POST', url: URL_ENCARGADOS });

    expect(peticion.request.body).toEqual({
      cedula: '1020304050',
      primerNombre: 'Ana',
      segundoNombre: 'María',
      primerApellido: 'Ruiz',
      segundoApellido: undefined,
      correos: [{ valor: 'ana@hospital.co' }],
      telefonos: [{ valor: '3001234567' }],
      tipo: 'SERVICE_AREA',
      idAsignacion: 'a1',
    });
    peticion.flush({ idPersona: 'p9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('sin telefono manda una lista vacia, no una con un valor vacio', async () => {
    // [{ valor: '' }] seria un telefono que no existe, y el backend lo guardaria como tal.
    await abrir();
    rellenarValido();
    await enviar();

    const peticion = http.expectOne({ method: 'POST', url: URL_ENCARGADOS });

    expect(peticion.request.body.telefonos).toEqual([]);
    peticion.flush({ idPersona: 'p9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('sin asignacion no llega al servidor, y lo dice', async () => {
    await abrir();
    escribir('cedula', '1020304050');
    escribir('primerNombre', 'Ana');
    escribir('primerApellido', 'Ruiz');
    escribir('correo', 'ana@hospital.co');
    await enviar();

    http.expectNone({ method: 'POST', url: URL_ENCARGADOS });
    expect(texto()).toContain('Elija de qué se hace cargo');
  });

  it('al guardar vuelve a la ficha de la sede', async () => {
    await abrir();
    rellenarValido();
    await enviar();
    http.expectOne({ method: 'POST', url: URL_ENCARGADOS }).flush({ idPersona: 'p9' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(TestBed.inject(Router).url).toBe(`/sedes/${ID}`);
  });

  describe('Lo que WCAG exige y no se ve', () => {
    it('cada campo tiene su etiqueta asociada', async () => {
      await abrir();

      for (const id of [
        'asignacion',
        'cedula',
        'primerNombre',
        'segundoNombre',
        'primerApellido',
        'segundoApellido',
        'correo',
        'telefono',
      ]) {
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
