import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import { HOY } from '../../../core/fechas';
import { Patrones } from './patrones';

const URL = 'http://localhost:8081/v1/api/reference-standards';

const PATRONES = [
  { id: 'p1', nombre: 'Termómetro patrón', marca: 'Fluke', serie: 'T-1', certificado: 'C-1', vencimientoCertificado: '2027-01-31', estadoActivo: true },
  { id: 'p2', nombre: 'Juego de pesas F1', serie: 'P-1', certificado: 'C-2', vencimientoCertificado: '2026-10-10', estadoActivo: true },
  { id: 'p3', nombre: 'Manómetro', marca: 'Wika', serie: 'M-1', certificado: 'C-3', vencimientoCertificado: '2026-11-01', estadoActivo: true },
  { id: 'p4', nombre: 'Barómetro viejo', serie: 'B-1', certificado: 'C-4', vencimientoCertificado: '2025-01-01', estadoActivo: false },
];

/** El catálogo de equipos patrón (2026-10-14). Hoy es el 14 de octubre de 2026. */
describe('Equipos patrón', () => {
  let fixture: ComponentFixture<Patrones>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[] = ['equipment.read', 'equipment.write']): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        { provide: HOY, useValue: () => new Date(2026, 9, 14, 9, 0) },
      ],
    });
    fixture = TestBed.createComponent(Patrones);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const ids = () => [...raiz().querySelectorAll('[data-patron]')].map((f) => f.getAttribute('data-patron'));
  const celdas = (id: string) =>
    [...raiz().querySelectorAll(`[data-patron="${id}"] td`)].map((td) =>
      td.textContent?.replace(/\s+/g, ' ').trim(),
    );
  const escribir = (id: string, valor: string) => {
    const campo = raiz().querySelector<HTMLInputElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  };
  const boton = (texto: string) =>
    [...raiz().querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === texto || b.getAttribute('aria-label') === texto,
    ) as HTMLButtonElement;

  async function abrir(): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL, PATRONES);
  }

  it('los vigentes primero, del que vence antes al que vence después; los retirados al final', async () => {
    montar();
    await abrir();

    expect(ids()).toEqual(['p2', 'p3', 'p1', 'p4']);
  });

  it('dice si el certificado está vencido, por vencer o vigente, y pesa más cuanto más urge', async () => {
    montar();
    await abrir();

    expect(celdas('p2')[3]).toBe('10 oct 2026 Vencido');
    expect(celdas('p3')[3]).toBe('01 nov 2026 Por vencer');
    expect(celdas('p1')[3]).toBe('31 ene 2027 Vigente');
    const estado = (id: string) => raiz().querySelector(`[data-patron="${id}"] td:nth-child(4) span:last-child`)!;
    expect(estado('p2').className).toContain('font-extrabold');
    expect(estado('p3').className).toContain('font-bold');
    expect(estado('p1').className).toContain('font-normal');
  });

  it('el día del vencimiento todavía vale', async () => {
    montar();
    fixture.detectChanges();
    await responderA(fixture, http, URL, [{ ...PATRONES[0], id: 'hoy', vencimientoCertificado: '2026-10-14' }]);

    expect(celdas('hoy')[3]).toBe('14 oct 2026 Por vencer');
  });

  it('un patrón sin marca lo dice, y uno retirado también', async () => {
    montar();
    await abrir();

    expect(celdas('p2')[0]).toBe('Juego de pesas F1 Sin marca');
    expect(celdas('p4')[0]).toBe('Barómetro viejo Sin marca · Retirado');
  });

  it('registrar manda los cinco campos, sin marca si quedó en blanco', async () => {
    montar();
    await abrir();
    escribir('patronNombre', ' Higrómetro ');
    escribir('patronSerie', 'H-1');
    escribir('patronCertificado', 'C-9');
    escribir('patronVence', '2027-06-30');
    await asentar(fixture);
    boton('Registrar patrón').click();
    await asentar(fixture);

    const alta = http.expectOne({ method: 'POST', url: URL });
    expect(alta.request.body).toEqual({
      nombre: 'Higrómetro',
      marca: undefined,
      serie: 'H-1',
      certificado: 'C-9',
      vencimientoCertificado: '2027-06-30',
    });
    alta.flush({ ...PATRONES[0], id: 'p9' });
    await asentar(fixture);
    http.match(URL).forEach((p) => p.flush(PATRONES));
  });

  it('sin vencimiento no llega al servidor y dice qué falta', async () => {
    montar();
    await abrir();
    escribir('patronNombre', 'Higrómetro');
    escribir('patronSerie', 'H-1');
    escribir('patronCertificado', 'C-9');
    boton('Registrar patrón').click();
    await asentar(fixture);

    http.expectNone({ method: 'POST', url: URL });
    expect(raiz().textContent).toContain('su vencimiento son obligatorios');
  });

  it('editar carga el patrón en el formulario y guarda por su ruta: así se renueva un certificado', async () => {
    montar();
    await abrir();
    boton('Editar Juego de pesas F1').click();
    await asentar(fixture);

    expect(raiz().querySelector<HTMLInputElement>('#patronSerie')!.value).toBe('P-1');
    escribir('patronCertificado', 'C-2b');
    escribir('patronVence', '2027-10-10');
    boton('Guardar cambios').click();
    await asentar(fixture);

    const cambio = http.expectOne({ method: 'PATCH', url: `${URL}/p2` });
    expect(cambio.request.body.certificado).toBe('C-2b');
    expect(cambio.request.body.vencimientoCertificado).toBe('2027-10-10');
    cambio.flush({ ...PATRONES[1], certificado: 'C-2b' });
    await asentar(fixture);
    http.match(URL).forEach((p) => p.flush(PATRONES));
  });

  it('sin equipment.write se ve y no se toca', async () => {
    montar(['equipment.read']);
    await abrir();

    expect(raiz().querySelector('#patronNombre')).toBeNull();
    expect(boton('Editar Juego de pesas F1')).toBeUndefined();
  });
});
