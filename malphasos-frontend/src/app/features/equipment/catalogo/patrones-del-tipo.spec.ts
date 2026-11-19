import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, responderA } from '../../../../testing/pantalla';
import {
  ID_PATRON,
  ID_TIPO,
  ID_VERIFICACION,
  PATRONES,
  TIPOS,
  URL_PATRONES,
  URL_TIPOS,
} from '../../../../testing/catalogo';
import { HOY } from '../../../core/fechas';
import { PatronesDelTipo } from './patrones-del-tipo';

const URL_DEL_TIPO = `${URL_TIPOS}/${ID_TIPO}`;
const URL_PATRONES_DEL_TIPO = `${URL_DEL_TIPO}/reference-standards`;
const URL_ASIGNAR = `${URL_DEL_TIPO}/verifications/${ID_VERIFICACION}/reference-standards`;

@Component({ selector: 'app-catalogo-falso', template: '' })
class CatalogoFalso {}

/**
 * Los patrones de cada verificación de un tipo (V60, 2026-11-19): decidido por el usuario que el
 * ingeniero elija solo entre los que el catálogo dice que sirven. Los datos de prueba traen un vigente,
 * uno vencido y uno retirado.
 */
describe('Patrones de cada verificacion de un tipo', () => {
  let fixture: ComponentFixture<PatronesDelTipo>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([{ path: 'catalogo', component: CatalogoFalso }]),
        { provide: HOY, useValue: () => new Date(2026, 10, 19) },
      ],
    });
    fixture = TestBed.createComponent(PatronesDelTipo);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_TIPO);
  });

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const nombres = () =>
    [...raiz().querySelectorAll('li[data-patron] span.block:first-child')].map((n) =>
      n.textContent?.trim(),
    );
  const casilla = (idPatron: string) =>
    raiz().querySelector<HTMLInputElement>(`li[data-patron="${idPatron}"] input[type=checkbox]`)!;
  const guardar = () =>
    [...raiz().querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Guardar'),
    ) as HTMLButtonElement;

  async function abrir(elegidos: string[] = []): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_DEL_TIPO, TIPOS[0]);
    await responderA(fixture, http, URL_PATRONES, PATRONES);
    await responderA(fixture, http, URL_PATRONES_DEL_TIPO, [
      { idVerificacion: ID_VERIFICACION, patrones: elegidos },
    ]);
  }

  it('ofrece los vigentes, vencidos incluidos y dichos, y no los retirados que nadie eligio', async () => {
    await abrir();

    expect(raiz().textContent).toContain('Presión · mmHg');
    expect(nombres()).toEqual(['Juego de pesas clase F1', 'Termómetro vencido']);
    expect(raiz().textContent).toContain('Vencido el');
    expect(raiz().textContent).toContain('ninguno elegido');
  });

  it('un retirado que ya estaba elegido se ve, marcado, para poder quitarlo', async () => {
    await abrir(['pat3']);

    expect(nombres()[0]).toBe('Barómetro retirado');
    expect(casilla('pat3').checked).toBe(true);
    expect(raiz().querySelector('li[data-patron="pat3"]')!.textContent).toContain('Retirado');
  });

  it('el filtro esconde los que no casan, pero nunca uno elegido', async () => {
    await abrir(['pat2']);
    const filtro = raiz().querySelector<HTMLInputElement>('#filtroPatrones')!;
    filtro.value = 'PESAS';
    filtro.dispatchEvent(new Event('input'));
    await asentar(fixture);

    expect(nombres()).toEqual(['Termómetro vencido', 'Juego de pesas clase F1']);

    filtro.value = 'nada que case';
    filtro.dispatchEvent(new Event('input'));
    await asentar(fixture);

    expect(nombres()).toEqual(['Termómetro vencido']);
  });

  it('sin cambios no deja guardar; al marcar uno manda la lista entera de esa verificacion', async () => {
    await abrir(['pat2']);
    expect(guardar().disabled).toBe(true);

    casilla(ID_PATRON).click();
    await asentar(fixture);
    expect(guardar().disabled).toBe(false);
    guardar().click();
    await asentar(fixture);

    const peticion = http.expectOne({ method: 'PATCH', url: URL_ASIGNAR });
    expect([...peticion.request.body.patrones].sort()).toEqual([ID_PATRON, 'pat2']);
    peticion.flush({ idVerificacion: ID_VERIFICACION, patrones: [ID_PATRON, 'pat2'] });
    // Dos rondas: la escritura invalida el catalogo y su recarga sale un tic despues.
    for (let ronda = 0; ronda < 2; ronda += 1) {
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
    }
    await asentar(fixture);

    expect(TestBed.inject(Router).url).toBe('/catalogo');
  });

  it('desmarcar el ultimo manda la lista vacia, que los quita todos', async () => {
    await abrir(['pat2']);

    casilla('pat2').click();
    await asentar(fixture);
    guardar().click();
    await asentar(fixture);

    const peticion = http.expectOne({ method: 'PATCH', url: URL_ASIGNAR });
    expect(peticion.request.body).toEqual({ patrones: [] });
    peticion.flush({ idVerificacion: ID_VERIFICACION, patrones: [] });
    for (let ronda = 0; ronda < 2; ronda += 1) {
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
    }
  });

  it('si el servidor rechaza, lo dice y se queda en la pagina', async () => {
    await abrir();

    casilla(ID_PATRON).click();
    await asentar(fixture);
    guardar().click();
    await asentar(fixture);

    http
      .expectOne({ method: 'PATCH', url: URL_ASIGNAR })
      .flush(
        { code: 'ERR_EQUIPMENT_022', message: 'x', details: ['El tipo no tiene esa verificacion'] },
        { status: 404, statusText: 'Not Found' },
      );
    await asentar(fixture);

    expect(raiz().querySelector('[role=alert]')).not.toBeNull();
    expect(guardar().disabled).toBe(false);
  });
});
