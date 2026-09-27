import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, responderA } from '../../../../testing/pantalla';
import {
  AREAS,
  EQUIPOS,
  ID_AREA,
  ID_EQUIPO,
  ID_ORDEN,
  ID_SEDE,
  orden,
  URL_ORDENES,
  urlAreasDeSede,
  urlEquiposDeArea,
} from '../../../../testing/ordenes';
import { AgregarEquipos } from './agregar-equipos';

const URL = `${URL_ORDENES}/${ID_ORDEN}`;

@Component({ selector: 'app-ficha-falsa', template: '' })
class FichaFalsa {}

describe('Añadir equipos a una orden', () => {
  let fixture: ComponentFixture<AgregarEquipos>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'ordenes/:id', component: FichaFalsa }]),
      ],
    });
    fixture = TestBed.createComponent(AgregarEquipos);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_ORDEN);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const casillas = () => [...raiz().querySelectorAll<HTMLInputElement>('input[type="checkbox"]')];

  async function abrir(datos: object = orden(), equipos: object[] = EQUIPOS): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL, datos);
    await responderA(fixture, http, urlAreasDeSede(ID_SEDE), AREAS);
    http.match(urlEquiposDeArea(ID_AREA)).forEach((peticion) => peticion.flush(equipos));
    await asentar(fixture);
  }

  function pulsar(etiqueta: string): void {
    const boton = [...raiz().querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes(etiqueta),
    )!;
    boton.click();
    fixture.detectChanges();
  }

  it('solo pregunta por las áreas abiertas de la sede de la orden', async () => {
    // Es la regla que el backend construyo el 2026-09-13 -el area del equipo tiene que ser de la sede
    // de la orden- ejercida por fin desde un navegador. Y el area cerrada no se ofrece: el backend la
    // rechazaria como referencia retirada.
    await abrir();

    expect(texto()).toContain('Urgencias');
    expect(texto()).not.toContain('Bodega cerrada');
    expect(texto()).toContain('áreas de la sede de esta orden');
  });

  it('lo que ya está en la orden no se vuelve a ofrecer', async () => {
    // Anadirlo otra vez no significa nada, y el backend lo trataria como un alcance repetido.
    await abrir(orden({ equipos: [{ idEquipoCliente: ID_EQUIPO, idAreaServicio: ID_AREA }] }));

    expect(texto()).not.toContain('SN-0001');
    expect(texto()).toContain('SN-0002');
  });

  it('el botón no hace nada hasta que hay algo marcado, y dice qué falta', async () => {
    await abrir();

    const boton = [...raiz().querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes('Marque los equipos'),
    )!;

    expect(boton.hasAttribute('disabled')).toBe(true);
  });

  it('marcar toda el área marca sus equipos, y el botón dice cuántos', async () => {
    await abrir();
    pulsar('Marcar toda el área');

    expect(casillas().every((casilla) => casilla.checked)).toBe(true);
    expect(texto()).toContain('Añadir 2 equipo(s)');

    pulsar('Desmarcar');

    expect(casillas().some((casilla) => casilla.checked)).toBe(false);
  });

  it('varios equipos son varias llamadas, una por equipo, y después se vuelve a la orden', async () => {
    // El API suma un equipo por llamada: es lo que hay, y se manda en serie porque el backend
    // reconcilia el alcance en cada alta.
    await abrir();
    casillas()[0].click();
    casillas()[1].click();
    fixture.detectChanges();
    pulsar('Añadir 2 equipo(s)');
    await asentar(fixture);

    const primera = http.expectOne({ method: 'POST', url: `${URL}/equipments` });

    expect(primera.request.body).toEqual({ idEquipoCliente: ID_EQUIPO });
    primera.flush(orden());
    await asentar(fixture);

    const segunda = http.expectOne({ method: 'POST', url: `${URL}/equipments` });

    expect(segunda.request.body).toEqual({ idEquipoCliente: 'ec2' });
    segunda.flush(orden());
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(TestBed.inject(Router).url).toBe(`/ordenes/${ID_ORDEN}`);
  });

  it('si uno falla, dice cuántos entraron y no se queda callado', async () => {
    // Son varias llamadas sin transaccion: los anteriores quedan dentro. Callarlo dejaria la pantalla
    // afirmando algo que no paso.
    await abrir();
    casillas()[0].click();
    casillas()[1].click();
    fixture.detectChanges();
    pulsar('Añadir 2 equipo(s)');
    await asentar(fixture);

    http.expectOne({ method: 'POST', url: `${URL}/equipments` }).flush(orden());
    await asentar(fixture);
    http.expectOne({ method: 'POST', url: `${URL}/equipments` }).flush(
      { code: 'ERR_WORK_ORDER_006', message: 'Service area not found', details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    const aviso = raiz().querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Esa área de servicio no existe');
    expect(aviso?.textContent).toContain('Entraron 1');
    expect(aviso?.textContent).toContain('quedaron fuera 1');
    // Y no se navega: hay algo que mirar en esta pantalla.
    expect(TestBed.inject(Router).url).not.toBe(`/ordenes/${ID_ORDEN}`);
  });

  it('sin nada que ofrecer lo explica, en vez de dejar la pantalla vacía', async () => {
    await abrir(orden(), []);

    expect(texto()).toContain('No hay equipos disponibles');
  });

  afterEach(() => http.verify());
});
