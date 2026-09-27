import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import {
  CLIENTES,
  ID_SEDE,
  orden,
  SEDE,
  URL_CLIENTES,
  URL_ORDENES,
  urlSede,
} from '../../../../testing/ordenes';
import { ListaOrdenes } from './lista-ordenes';

describe('Listado de órdenes de trabajo', () => {
  let fixture: ComponentFixture<ListaOrdenes>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[] = ['work-order.read', 'work-order.write']): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(ListaOrdenes);
    http = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => montar());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(ordenes: object[]): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_ORDENES, ordenes);
    await responderA(fixture, http, URL_CLIENTES, CLIENTES);
    http.match(urlSede(ID_SEDE)).forEach((peticion) => peticion.flush(SEDE));
    await asentar(fixture);
  }

  it('pinta una fila por orden, con el cliente y la sede en palabras', async () => {
    // La orden trae identificadores: los dos nombres se cruzan con otras dos consultas.
    await abrir([orden()]);

    const fila = raiz().querySelector('tbody tr')!.textContent ?? '';

    expect(fila).toContain('2026-10-15');
    expect(fila).toContain('Hospital Central');
    expect(fila).toContain('Sede Norte');
    expect(fila).toContain('Preventivo');
  });

  it('una orden sin equipos lo dice, y con peso: está programada y no dice sobre qué', async () => {
    await abrir([orden()]);

    const celda = raiz().querySelectorAll('tbody td')[4];

    expect(celda.textContent).toContain('Sin equipos');
    expect(celda.className).toContain('font-semibold');
  });

  it('el estado se distingue por peso tipográfico y no por color', async () => {
    await abrir([
      orden({ id: 'o1', estadoEjecucion: 'EN_EJECUCION' }),
      orden({ id: 'o2', estadoActivo: false }),
    ]);

    const estados = [...raiz().querySelectorAll('tbody tr')].map(
      (fila) => fila.querySelectorAll('td')[5],
    );

    expect(estados[0].textContent).toContain('En ejecución');
    expect(estados[0].className).toContain('font-semibold');
    expect(estados[1].textContent).toContain('Anulada');
    expect(estados[1].className).not.toContain('font-semibold');
    expect(estados.every((celda) => !celda.className.includes('text-accent'))).toBe(true);
  });

  it('sin órdenes explica cómo se programa una', async () => {
    await abrir([]);

    expect(raiz().querySelector('table')).toBeNull();
    expect(texto()).toContain('se programa para un cliente y una de sus sedes');
  });

  it('sin work-order.write no se ofrece programar', async () => {
    TestBed.resetTestingModule();
    montar(['work-order.read']);
    await abrir([orden()]);

    expect([...raiz().querySelectorAll('a')].map((a) => a.textContent?.trim())).not.toContain(
      'Programar orden',
    );
  });

  afterEach(() => http.verify());
});
