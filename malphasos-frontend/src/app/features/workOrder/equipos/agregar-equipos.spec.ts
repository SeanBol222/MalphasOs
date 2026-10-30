import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
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
const URL_LISTADO = 'http://localhost:8081/v1/api/client-equipments/overview';

/** Lo que el listado del servidor sabe de los dos equipos: su tipo y su marca (2026-10-27). */
const LISTADO = {
  filas: [
    { id: ID_EQUIPO, serie: 'SN-0001', tipoEquipo: 'Báscula', marca: 'Kern' },
    { id: 'ec2', serie: 'SN-0002', tipoEquipo: 'Tensiómetro', marca: 'Welch Allyn' },
  ],
  total: 2,
};

@Component({ selector: 'app-ficha-falsa', template: '' })
class FichaFalsa {}

describe('Añadir equipos a una orden', () => {
  let fixture: ComponentFixture<AgregarEquipos>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[] = ['work-order.write', 'equipment.read']): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([{ path: 'ordenes/:id', component: FichaFalsa }]),
      ],
    });
    fixture = TestBed.createComponent(AgregarEquipos);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_ORDEN);
  }

  beforeEach(() => montar());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const casillas = () => [...raiz().querySelectorAll<HTMLInputElement>('input[type="checkbox"]')];

  async function abrir(datos: object = orden(), equipos: object[] = EQUIPOS): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL, datos);
    await responderA(fixture, http, urlAreasDeSede(ID_SEDE), AREAS);
    http.match(urlEquiposDeArea(ID_AREA)).forEach((peticion) => peticion.flush(equipos));
    await asentar(fixture);
    http.match((r) => r.url === URL_LISTADO).forEach((peticion) => peticion.flush(LISTADO));
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
    await abrir(orden({ equipos: [{ idEquipoCliente: ID_EQUIPO, idReporteServicio: 'r1' }] }));

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

  describe('Cada equipo dice qué es (2026-10-27)', () => {
    it('ofrece cada equipo con su tipo y su marca, y debajo su serie', async () => {
      await abrir();

      expect(
        [...raiz().querySelectorAll('[data-descripcion]')].map((e) => e.textContent?.trim()),
      ).toEqual(['Báscula · Kern', 'Tensiómetro · Welch Allyn']);
      expect(texto()).toContain('Serie SN-0001');
    });

    it('pide al listado exactamente los equipos ofrecidos', async () => {
      fixture.detectChanges();
      await responderA(fixture, http, URL, orden());
      await responderA(fixture, http, urlAreasDeSede(ID_SEDE), AREAS);
      http.match(urlEquiposDeArea(ID_AREA)).forEach((peticion) => peticion.flush(EQUIPOS));
      await asentar(fixture);

      const peticion = http.expectOne((r) => r.url === URL_LISTADO);
      expect(peticion.request.params.get('ids')).toBe([ID_EQUIPO, 'ec2'].sort().join(','));
      peticion.flush(LISTADO);
      await asentar(fixture);
    });

    it('con más de cien equipos lo pide en tandas de cien y junta las respuestas', async () => {
      // El listado del servidor entrega cien por página: antes, del ciento uno en adelante se quedaban
      // sin tipo ni marca.
      const muchos = Array.from({ length: 150 }, (_, i) => ({
        id: `e${String(i).padStart(3, '0')}`,
        serie: `S-${i}`,
        idAreaServicio: ID_AREA,
        estadoActivo: true,
      }));
      fixture.detectChanges();
      await responderA(fixture, http, URL, orden());
      await responderA(fixture, http, urlAreasDeSede(ID_SEDE), AREAS);
      http.match(urlEquiposDeArea(ID_AREA)).forEach((peticion) => peticion.flush(muchos));
      await asentar(fixture);

      const tandas = http.match((r) => r.url === URL_LISTADO);
      expect(tandas.map((t) => t.request.params.get('ids')!.split(',').length)).toEqual([100, 50]);
      tandas.forEach((tanda) =>
        tanda.flush({
          filas: tanda.request.params
            .get('ids')!
            .split(',')
            .map((id) => ({ id, tipoEquipo: 'Báscula', marca: 'Kern' })),
        }),
      );
      await asentar(fixture);

      expect(raiz().querySelectorAll('[data-descripcion]')).toHaveLength(150);
    });

    it('sin equipment.read no lo pide, y cada equipo se ofrece por su serie', async () => {
      TestBed.resetTestingModule();
      montar(['work-order.write']);
      await abrir();

      expect(raiz().querySelectorAll('[data-descripcion]')).toHaveLength(0);
      expect(texto()).toContain('Serie SN-0001');
    });
  });
});
