import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar } from '../../../../testing/pantalla';
import {
  ID_PATRON,
  ID_TIPO,
  PATRONES,
  TIPOS,
  URL_PATRONES,
  URL_TIPOS,
} from '../../../../testing/catalogo';
import { ID_ORDEN, URL_ORDENES } from '../../../../testing/ordenes';
import { PatronesDeLaVisita } from './patrones-de-la-visita';

const URL_PATRONES_DE_LA_ORDEN = `${URL_ORDENES}/${ID_ORDEN}/reference-standards`;

/** Lo que la orden lleva a la visita, en su ficha (V60): por tipo, con nombres, y el enlace para cambiarlo. */
describe('Patrones de la visita en la ficha de la orden', () => {
  let fixture: ComponentFixture<PatronesDeLaVisita>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...proveerApiSimulado(), provideRouter([])] });
    fixture = TestBed.createComponent(PatronesDeLaVisita);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('idOrden', ID_ORDEN);
  });

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => (raiz().textContent ?? '').replace(/\s+/g, ' ');
  const enlace = () =>
    [...raiz().querySelectorAll('a')].find((a) => a.textContent?.includes('Elegir patrones'));

  async function abrir(porTipo: object[]): Promise<void> {
    fixture.detectChanges();
    await asentar(fixture);
    http.match(URL_PATRONES_DE_LA_ORDEN).forEach((p) => p.flush(porTipo));
    http.match(URL_TIPOS).forEach((p) => p.flush(TIPOS));
    http.match(URL_PATRONES).forEach((p) => p.flush(PATRONES));
    await asentar(fixture);
  }

  it('nombra los patrones de cada tipo, y dice cuando un tipo no lleva ninguno', async () => {
    await abrir([
      { idTipoEquipo: ID_TIPO, patrones: [ID_PATRON] },
      { idTipoEquipo: 'otro', patrones: [] },
    ]);

    expect(texto()).toContain('Tensiómetro');
    expect(texto()).toContain('Juego de pesas clase F1 · serie KF1-2024-118');
    expect(raiz().querySelector('[data-tipo="otro"]')?.textContent).toContain('Ninguno elegido');
  });

  it('el enlace para cambiarlos sale solo si se pueden cambiar', async () => {
    await abrir([{ idTipoEquipo: ID_TIPO, patrones: [] }]);
    expect(enlace()).toBeUndefined();

    fixture.componentRef.setInput('cambiables', true);
    await asentar(fixture);
    expect(enlace()?.getAttribute('href')).toBe(`/ordenes/${ID_ORDEN}/patrones`);
  });
});
