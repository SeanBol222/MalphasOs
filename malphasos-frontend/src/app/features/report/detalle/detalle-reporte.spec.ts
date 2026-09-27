import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import { EQUIPOS, ID_ORDEN, URL_EQUIPOS_DE_CLIENTE } from '../../../../testing/ordenes';
import {
  ID_REPORTE,
  reporte,
  reporteCerrable,
  urlCerrarReporte,
  urlReporte,
} from '../../../../testing/reportes';
import { ReporteApi } from '../reporte-api';
import { DetalleReporte } from './detalle-reporte';

const TODAS = ['report.read', 'report.write', 'equipment.read'];

@Component({ selector: 'app-orden-falsa', template: '' })
class OrdenFalsa {}

describe('Ficha de un reporte de servicio', () => {
  let fixture: ComponentFixture<DetalleReporte>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[] = TODAS): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([
          { path: 'ordenes/:id', component: OrdenFalsa },
          { path: 'ordenes', component: OrdenFalsa },
        ]),
      ],
    });
    fixture = TestBed.createComponent(DetalleReporte);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_REPORTE);
  }

  beforeEach(() => montar());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const campo = (id: string) => raiz().querySelector<HTMLTextAreaElement>(`#${id}`)!;
  const seleccion = () => raiz().querySelector<HTMLSelectElement>('#resultado')!;

  /** Abre la ficha y responde a sus dos consultas: el reporte y la lista de equipos. */
  async function abrir(datos: object = reporte()): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, urlReporte(ID_REPORTE), datos);
    http.match(URL_EQUIPOS_DE_CLIENTE).forEach((peticion) => peticion.flush(EQUIPOS));
    await asentar(fixture);
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
    const control = campo(id);
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  it('enseña de qué equipo habla y en qué estado está', async () => {
    await abrir();

    // La serie sale de la lista de equipos: el reporte solo trae el identificador.
    expect(texto()).toContain('SN-0001');
    expect(texto()).toContain('Borrador');
  });

  it('vuelca en el formulario los cinco campos que el servidor tiene', async () => {
    await abrir(
      reporte({
        fallaReportada: 'No enciende',
        diagnostico: 'Fuente quemada',
        procedimientos: 'Cambio de fuente',
        observaciones: 'Queda en prueba',
        resultado: 'OPERATIVO_CON_RESTRICCIONES',
      }),
    );

    expect(campo('fallaReportada').value).toBe('No enciende');
    expect(campo('diagnostico').value).toBe('Fuente quemada');
    expect(campo('procedimientos').value).toBe('Cambio de fuente');
    expect(campo('observaciones').value).toBe('Queda en prueba');
    expect(seleccion().value).toBe('OPERATIVO_CON_RESTRICCIONES');
  });

  it('guardar manda los cinco campos tal como se ven', async () => {
    await abrir();
    escribir('fallaReportada', 'No enciende');
    escribir('procedimientos', 'Cambio de fuente');

    pulsar('Guardar');
    await asentar(fixture);

    const peticion = http.expectOne(urlReporte(ID_REPORTE));
    expect(peticion.request.method).toBe('PATCH');
    expect(peticion.request.body.fallaReportada).toBe('No enciende');
    expect(peticion.request.body.procedimientos).toBe('Cambio de fuente');
    peticion.flush(reporte({ fallaReportada: 'No enciende', procedimientos: 'Cambio de fuente' }));
    await asentar(fixture);
    http.match(urlReporte(ID_REPORTE)).forEach((p) => p.flush(reporte()));
    http.match(URL_EQUIPOS_DE_CLIENTE).forEach((p) => p.flush(EQUIPOS));
    await asentar(fixture);
  });

  it('un campo que se vacía se manda vacío, porque vaciarlo es borrarlo', async () => {
    // El backend distingue el nulo -«no lo cambies»- del blanco -«bórralo»-, y este formulario muestra
    // todo lo que hay: vaciar una casilla en pantalla significa borrar el dato.
    await abrir(reporte({ fallaReportada: 'No enciende' }));
    escribir('fallaReportada', '');

    pulsar('Guardar');
    await asentar(fixture);

    const peticion = http.expectOne(urlReporte(ID_REPORTE));
    expect(peticion.request.body.fallaReportada).toBe('');
    peticion.flush(reporte());
    await asentar(fixture);
    http.match(urlReporte(ID_REPORTE)).forEach((p) => p.flush(reporte()));
    http.match(URL_EQUIPOS_DE_CLIENTE).forEach((p) => p.flush(EQUIPOS));
    await asentar(fixture);
  });

  it('sin resultado elegido no se manda la cadena vacía, que no es un valor del catálogo', async () => {
    await abrir();
    escribir('procedimientos', 'Limpieza');

    pulsar('Guardar');
    await asentar(fixture);

    const peticion = http.expectOne(urlReporte(ID_REPORTE));
    expect(peticion.request.body.resultado).toBeUndefined();
    peticion.flush(reporte({ procedimientos: 'Limpieza' }));
    await asentar(fixture);
    http.match(urlReporte(ID_REPORTE)).forEach((p) => p.flush(reporte()));
    http.match(URL_EQUIPOS_DE_CLIENTE).forEach((p) => p.flush(EQUIPOS));
    await asentar(fixture);
  });

  it('no deja cerrar sin procedimientos ni resultado, que es lo que el servidor exige', async () => {
    await abrir();

    const cerrar = [...raiz().querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes('Cerrar reporte'),
    )!;
    expect(cerrar.disabled).toBe(true);
    expect(texto()).toContain('Para cerrarlo hacen falta los procedimientos y el resultado');
  });

  it('con los dos campos, cerrar pide confirmación y avisa de que no se podrá modificar', async () => {
    await abrir(reporteCerrable());

    pulsar('Cerrar reporte');

    expect(texto()).toContain('Un reporte cerrado ya no se modifica');

    pulsar('Confirmar cierre');
    await asentar(fixture);

    const peticion = http.expectOne(urlCerrarReporte(ID_REPORTE));
    expect(peticion.request.method).toBe('PATCH');
    peticion.flush(reporteCerrable({ estado: 'FINALIZADO', finalizado: '2026-09-27T10:00:00' }));
    await asentar(fixture);
    http.match(urlReporte(ID_REPORTE)).forEach((p) => p.flush(reporteCerrable()));
    http.match(URL_EQUIPOS_DE_CLIENTE).forEach((p) => p.flush(EQUIPOS));
    await asentar(fixture);
  });

  it('un reporte cerrado se lee pero no se escribe, y dice cómo corregirlo', async () => {
    await abrir(
      reporteCerrable({ estado: 'FINALIZADO', finalizado: '2026-09-27T10:00:00' }),
    );

    expect(campo('procedimientos').disabled).toBe(true);
    expect(seleccion().disabled).toBe(true);
    expect(texto()).toContain('Para corregirlo hay que retirarlo y abrir otro');
    expect(texto()).toContain('cerrado el 2026-09-27');
    expect(
      [...raiz().querySelectorAll('button')].some((b) =>
        (b.textContent ?? '').includes('Cerrar reporte'),
      ),
    ).toBe(false);
  });

  it('un reporte retirado tampoco se escribe', async () => {
    await abrir(reporte({ estadoActivo: false }));

    expect(campo('procedimientos').disabled).toBe(true);
    expect(texto()).toContain('Retirado');
  });

  it('retirar avisa de que no se borra nada y vuelve a la orden', async () => {
    const router = TestBed.inject(Router);
    const viaje = vi.spyOn(router, 'navigate');
    await abrir();

    pulsar('Retirar');
    expect(texto()).toContain('No se borra nada');

    pulsar('Confirmar retiro');
    await asentar(fixture);

    const peticion = http.expectOne(urlReporte(ID_REPORTE));
    expect(peticion.request.method).toBe('DELETE');
    peticion.flush(null);
    await asentar(fixture);

    expect(viaje).toHaveBeenCalledWith(['/ordenes', ID_ORDEN]);
    http.match(urlReporte(ID_REPORTE)).forEach((p) => p.flush(reporte()));
    http.match(URL_EQUIPOS_DE_CLIENTE).forEach((p) => p.flush(EQUIPOS));
    await asentar(fixture);
  });

  it('sin report.write se lee y no se ofrece ninguna escritura', async () => {
    TestBed.resetTestingModule();
    montar(['report.read', 'equipment.read']);
    await abrir(reporteCerrable());

    expect(campo('procedimientos').disabled).toBe(true);
    expect(
      [...raiz().querySelectorAll('button')].map((b) => (b.textContent ?? '').trim()),
    ).toEqual([]);
  });

  it('un 409 al cerrar se explica con el mensaje del catálogo, no con el del servidor', async () => {
    await abrir(reporteCerrable());
    pulsar('Cerrar reporte');
    pulsar('Confirmar cierre');
    await asentar(fixture);

    http.expectOne(urlCerrarReporte(ID_REPORTE)).flush(
      { code: 'ERR_SERVICE_REPORT_003', message: 'Service report state does not allow it' },
      { status: 409, statusText: 'Conflict' },
    );
    await asentar(fixture);

    expect(texto()).toContain('la verificación completa');
    expect(texto()).not.toContain('Service report state');
  });

  it('una recarga de la caché no pisa lo que se está escribiendo', async () => {
    // Sin la guarda del efecto, cada recarga devolvería el formulario al valor del servidor mientras
    // alguien teclea. Es el mismo defecto que ya apareció editando un cliente.
    //
    // La recarga se provoca invalidando la caché, que es exactamente lo que hace cualquier escritura
    // del módulo. La primera versión de esta prueba se limitaba a llamar a `http.match`, y pasaba en
    // vacío: sin invalidar no hay peticion pendiente que responder, así que el efecto no volvía a
    // correr y la prueba no ejercía nada. Lo delató una mutación -quitar la guarda y verla seguir
    // verde-, que es el tercer caso de este patrón en el proyecto.
    await abrir();
    escribir('diagnostico', 'Lo estoy escribiendo');

    void TestBed.inject(QueryClient).invalidateQueries({ queryKey: ReporteApi.CLAVE });
    await responderA(
      fixture,
      http,
      urlReporte(ID_REPORTE),
      reporte({ diagnostico: 'Lo que tiene el servidor' }),
    );

    expect(campo('diagnostico').value).toBe('Lo estoy escribiendo');
  });
});
