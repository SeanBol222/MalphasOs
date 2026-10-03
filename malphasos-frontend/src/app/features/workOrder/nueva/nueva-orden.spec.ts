import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
import { asentar, elegirEnBuscador, responderA } from '../../../../testing/pantalla';
import {
  CLIENTES,
  ID_CLIENTE,
  ID_ORDEN,
  ID_SEDE,
  SEDE,
  URL_CLIENTES,
  URL_ORDENES,
  urlAreasDeSede,
} from '../../../../testing/ordenes';
import { NuevaOrden } from './nueva-orden';

const URL_SEDES_DEL_CLIENTE = `http://localhost:8081/v1/api/clients/${ID_CLIENTE}/headquarters`;

@Component({ selector: 'app-ficha-falsa', template: '' })
class FichaFalsa {}

describe('Programar una orden de trabajo', () => {
  let fixture: ComponentFixture<NuevaOrden>;
  let http: HttpTestingController;
  let desinstalarAlmacenamiento: () => void;

  beforeEach(() => {
    desinstalarAlmacenamiento = instalarAlmacenamiento();
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'ordenes/:id', component: FichaFalsa }]),
      ],
    });
    fixture = TestBed.createComponent(NuevaOrden);
    http = TestBed.inject(HttpTestingController);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_CLIENTES, CLIENTES);
  }

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  async function elegirClienteYSede(): Promise<void> {
    await elegirEnBuscador(fixture, 'idCliente', 'Hospital Central');
    await asentar(fixture);
    await responderA(fixture, http, URL_SEDES_DEL_CLIENTE, [
      SEDE,
      { id: 's9', nombre: 'Sede cerrada', idCliente: ID_CLIENTE, estadoActivo: false },
    ]);
    escribir('idSede', ID_SEDE);
  }

  async function enviar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);
  }

  it('dice que los equipos se añaden después, en la ficha', async () => {
    // Es lo que el API ofrece y lo que ocurre de verdad: se acuerda la visita y luego se decide sobre
    // qué se trabaja. Sin decirlo, alguien los busca en este formulario.
    await abrir();

    expect(texto()).toContain('Los equipos sobre los que se trabaja se añaden después');
  });

  it('la sede no se consulta hasta elegir cliente, y solo ofrece las abiertas', async () => {
    await abrir();

    http.expectNone(URL_SEDES_DEL_CLIENTE);

    await elegirClienteYSede();

    const opciones = [...raiz().querySelectorAll<HTMLOptionElement>('#idSede option')];

    expect(opciones.map((o) => o.textContent?.trim())).toEqual(['Elija una sede', 'Sede Norte']);
  });

  it('manda lo que el contrato declara, y nada más', async () => {
    await abrir();
    await elegirClienteYSede();
    escribir('fechaMantenimiento', '2026-11-20');
    escribir('tipoServicio', 'CALIBRACION');
    escribir('periodicidad', 'ANUAL');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_ORDENES });

    expect(alta.request.body).toEqual({
      idCliente: ID_CLIENTE,
      idSede: ID_SEDE,
      fechaMantenimiento: '2026-11-20',
      tipoServicio: 'CALIBRACION',
      periodicidad: 'ANUAL',
    });
    alta.flush({ id: ID_ORDEN });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('cambiar de cliente borra la sede: sería la sede de otro', async () => {
    // Se intentó primero con (change) en el campo del cliente, que es un buscador y no lo emite: el
    // manejador no se llamaba nunca y quedaba puesta la sede del cliente anterior.
    await abrir();
    await elegirClienteYSede();

    expect(raiz().querySelector<HTMLSelectElement>('#idSede')!.value).toBe(ID_SEDE);

    const cliente = raiz().querySelector<HTMLInputElement>('#idCliente')!;
    cliente.value = '';
    cliente.dispatchEvent(new Event('input'));
    await asentar(fixture);

    expect(raiz().querySelector<HTMLSelectElement>('#idSede')!.value).toBe('');
  });

  it('sin cliente, sede o fecha no llega al servidor', async () => {
    await abrir();
    await enviar();

    http.expectNone({ method: 'POST', url: URL_ORDENES });
    expect(texto()).toContain('Elija el cliente');
    expect(texto()).toContain('Elija la sede');
    expect(texto()).toContain('Indique la fecha');
  });

  it('al programar, lleva a la ficha de la orden', async () => {
    await abrir();
    await elegirClienteYSede();
    escribir('fechaMantenimiento', '2026-11-20');
    await enviar();
    http.expectOne({ method: 'POST', url: URL_ORDENES }).flush({ id: ID_ORDEN });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(TestBed.inject(Router).url).toBe(`/ordenes/${ID_ORDEN}`);
  });

  it('un fallo del API se enseña traducido, con el código real del módulo', async () => {
    await abrir();
    await elegirClienteYSede();
    escribir('fechaMantenimiento', '2026-11-20');
    await enviar();
    http.expectOne({ method: 'POST', url: URL_ORDENES }).flush(
      { code: 'ERR_WORK_ORDER_005', message: 'Headquarter not found', details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    await asentar(fixture);

    expect(raiz().querySelector('[role="alert"]')?.textContent).toContain('Esa sede no existe');
  });

  afterEach(() => {
    // En finally a proposito: si verify() lanza por una peticion abierta, sin esto el doble de
    // localStorage se queda instalado y el fichero SIGUIENTE hereda lo que este guardo. Es la variante
    // con contaminacion del fallo que este proyecto ya pago -- un fallo en afterEach se lee como
    // ochenta y seis -- y asi el rojo se queda donde ocurrio.
    try {
      http.verify();
    } finally {
      desinstalarAlmacenamiento();
    }
  });
});
