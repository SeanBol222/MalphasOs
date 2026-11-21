import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
import { asentar, responderA } from '../../../../testing/pantalla';
import { HOY } from '../../../core/fechas';
import { CampanaDeAvisos } from './campana-de-avisos';

const API = 'http://localhost:8081/v1/api';

const PATRONES = [
  {
    id: 'th02',
    nombre: 'Termohigrómetro',
    codigoInterno: 'TH-02',
    vencimientoCertificado: '2026-11-15',
    estadoActivo: true,
  },
  {
    id: 'pesas',
    nombre: 'Juego de pesas F1',
    vencimientoCertificado: '2026-11-30',
    estadoActivo: true,
  },
  { id: 'lejos', nombre: 'Lejano', vencimientoCertificado: '2027-06-01', estadoActivo: true },
];

const ORDENES = [{ id: 'o1', numero: '4F2A9C', idCliente: 'c1', fechaMantenimiento: '2026-11-18' }];

describe('Campanita de avisos', () => {
  let fixture: ComponentFixture<CampanaDeAvisos>;
  let http: HttpTestingController;
  let desinstalar: () => void;

  beforeEach(() => {
    desinstalar = instalarAlmacenamiento();
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      desinstalar();
    }
  });

  function montar(autoridades: readonly string[]): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([]),
        { provide: HOY, useValue: () => new Date(2026, 10, 21, 9) },
      ],
    });
    fixture = TestBed.createComponent(CampanaDeAvisos);
    http = TestBed.inject(HttpTestingController);
  }

  async function conTodo(): Promise<void> {
    montar(['equipment.write', 'work-order.approve']);
    await responderA(fixture, http, `${API}/reference-standards`, PATRONES);
    await responderA(fixture, http, `${API}/work-orders/pending-approval`, ORDENES);
    await responderA(fixture, http, `${API}/clients`, [
      { id: 'c1', razonSocial: 'Laboratorio Demo' },
    ]);
  }

  const raiz = () => fixture.nativeElement as HTMLElement;
  const campana = () => raiz().querySelector<HTMLButtonElement>('[data-campana]');
  const avisos = () => [...raiz().querySelectorAll<HTMLButtonElement>('[data-aviso]')];
  const boton = (texto: string) =>
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim().startsWith(texto))!;

  async function abrir(): Promise<void> {
    campana()!.click();
    await asentar(fixture);
  }

  it('sin patrones que administrar ni órdenes que aprobar, no hay campanita ni consultas', async () => {
    montar(['equipment.read', 'work-order.read']);
    await asentar(fixture);

    expect(campana()).toBeNull();
  });

  it('el número cuenta los avisos sin leer, y el patrón lejano no avisa', async () => {
    await conTodo();

    expect(raiz().querySelector('[data-sin-leer]')!.textContent!.trim()).toBe('3');
    expect(campana()!.getAttribute('aria-label')).toBe('Avisos, 3 sin leer');

    await abrir();
    expect(avisos().map((a) => a.textContent)).toEqual([
      expect.stringContaining('Termohigrómetro TH-02'),
      expect.stringContaining('Juego de pesas F1'),
      expect.stringContaining('Laboratorio Demo'),
    ]);
  });

  it('los filtros separan patrones y órdenes', async () => {
    await conTodo();
    await abrir();

    boton('Órdenes · 1').click();
    await asentar(fixture);

    expect(avisos().length).toBe(1);
    expect(avisos()[0]!.textContent).toContain('Orden por aprobar');
  });

  it('tocar un aviso lo marca como leído, cierra el cuadro y lleva a su pantalla', async () => {
    await conTodo();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await abrir();

    avisos()[2]!.click();
    await asentar(fixture);

    expect(navegar).toHaveBeenCalledWith(['/ordenes', 'o1']);
    expect(raiz().querySelector('[role="dialog"]')).toBeNull();
    expect(raiz().querySelector('[data-sin-leer]')!.textContent!.trim()).toBe('2');
  });

  it('marcar todo como leído quita el número, y se recuerda en el navegador', async () => {
    await conTodo();
    await abrir();

    boton('Marcar todo como leído').click();
    await asentar(fixture);

    expect(raiz().querySelector('[data-sin-leer]')).toBeNull();
    expect(avisos().every((a) => a.dataset['leido'] === 'true')).toBe(true);
    expect(JSON.parse(localStorage.getItem('malphasos.avisos-leidos.sbolivar')!).length).toBe(3);
  });

  it('un administrador del catálogo sin aprobar órdenes ve solo patrones y sin filtros', async () => {
    montar(['equipment.write']);
    await responderA(fixture, http, `${API}/reference-standards`, PATRONES);
    await abrir();

    expect(avisos().length).toBe(2);
    expect(raiz().querySelector('[aria-label="Filtrar avisos"]')).toBeNull();
  });

  it('sin avisos, lo dice', async () => {
    montar(['work-order.approve']);
    await responderA(fixture, http, `${API}/work-orders/pending-approval`, []);
    await responderA(fixture, http, `${API}/clients`, []);
    await abrir();

    expect(raiz().textContent).toContain('No hay avisos. Todo está al día.');
  });
});
