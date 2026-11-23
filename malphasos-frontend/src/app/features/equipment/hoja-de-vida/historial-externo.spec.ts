import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, responderA } from '../../../../testing/pantalla';
import { HOY } from '../../../core/fechas';
import { HistorialExterno } from './historial-externo';

const ID = 'e1';
const URL = `http://localhost:8081/v1/api/client-equipments/${ID}/external-interventions`;

const CALIBRACION = {
  id: 'x1',
  idEquipoCliente: ID,
  realizada: '2026-11-20',
  tipoServicio: 'CALIBRACION',
  descripcion: 'Calibración de presión en tres puntos',
  empresa: 'Metrología S.A.S.',
  resultado: 'OPERATIVO',
  documento: 'CAL-2026-118',
};

/**
 * Lo que otras empresas le hicieron al equipo (V61, 2026-11-23): se lista, se anota con lo mínimo y se
 * retira preguntando antes.
 */
describe('Historia adicional, en la hoja de vida', () => {
  let fixture: ComponentFixture<HistorialExterno>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        { provide: HOY, useValue: () => new Date(2026, 10, 23, 22) },
      ],
    });
    fixture = TestBed.createComponent(HistorialExterno);
    fixture.componentRef.setInput('id', ID);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => (raiz().textContent ?? '').replace(/\s+/g, ' ').trim();
  const boton = (etiqueta: string) =>
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === etiqueta)!;
  const campo = (id: string) => raiz().querySelector<HTMLInputElement>(`#${id}`)!;

  async function abrir(lista: object[] = []): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL, lista);
  }

  function escribir(id: string, valor: string): void {
    const control = campo(id);
    control.value = valor;
    control.dispatchEvent(new Event('input'));
  }

  async function elegir(selector: string): Promise<void> {
    raiz().querySelector<HTMLInputElement>(selector)!.click();
    await asentar(fixture);
  }

  it('lista lo anotado con su fecha, tipo, empresa, resultado y certificado', async () => {
    await abrir([CALIBRACION]);

    expect(texto()).toContain('20 nov 2026 · Calibración · Metrología S.A.S.');
    expect(texto()).toContain('Operativo · CAL-2026-118');
  });

  it('sin nada anotado, lo dice', async () => {
    await abrir();

    expect(raiz().querySelector('[data-sin-externas]')).not.toBeNull();
  });

  it('el formulario abre con la fecha de hoy, en la zona local, y no deja guardar sin tipo', async () => {
    await abrir();
    boton('Añadir al historial').click();
    await asentar(fixture);

    // A las diez de la noche en Bogotá, hoy sigue siendo el 23.
    expect(campo('externa-fecha').value).toBe('2026-11-23');
    expect(campo('externa-fecha').max).toBe('2026-11-23');
    escribir('externa-descripcion', 'Calibración');
    escribir('externa-empresa', 'Metrología S.A.S.');
    boton('Guardar').click();
    await asentar(fixture);

    expect(texto()).toContain('Elija el tipo de servicio.');
    http.expectNone({ method: 'POST', url: URL });
  });

  it('guardar manda lo escrito, sin el resultado si no se sabe, y cierra el formulario', async () => {
    await abrir();
    boton('Añadir al historial').click();
    await asentar(fixture);
    escribir('externa-fecha', '2026-11-20');
    await elegir('[data-tipo="CALIBRACION"]');
    escribir('externa-descripcion', '  Calibración de presión  ');
    escribir('externa-empresa', 'Metrología S.A.S.');
    boton('Guardar').click();
    await asentar(fixture);

    const peticion = http.expectOne({ method: 'POST', url: URL });
    expect(peticion.request.body).toEqual({
      realizada: '2026-11-20',
      tipoServicio: 'CALIBRACION',
      descripcion: 'Calibración de presión',
      empresa: 'Metrología S.A.S.',
      resultado: undefined,
      documento: undefined,
    });
    peticion.flush(CALIBRACION);
    await asentar(fixture);
    http.match(URL).forEach((p) => p.flush([CALIBRACION]));
    await asentar(fixture);

    expect(raiz().querySelector('form')).toBeNull();
    expect(raiz().querySelector('[data-externa="x1"]')).not.toBeNull();
  });

  it('una fecha futura no se guarda', async () => {
    await abrir();
    boton('Añadir al historial').click();
    await asentar(fixture);
    escribir('externa-fecha', '2026-11-24');
    await elegir('[data-tipo="OTRO"]');
    escribir('externa-descripcion', 'Algo');
    escribir('externa-empresa', 'Otra');
    boton('Guardar').click();
    await asentar(fixture);

    expect(texto()).toContain('La fecha no puede ser futura.');
    http.expectNone({ method: 'POST', url: URL });
  });

  it('retirar pregunta antes, y al confirmar la retira', async () => {
    await abrir([CALIBRACION]);
    boton('Retirar').click();
    await asentar(fixture);
    expect(boton('Cancelar')).toBeDefined();

    boton('Retirar').click();
    await asentar(fixture);

    const peticion = http.expectOne({ method: 'DELETE', url: `${URL}/x1` });
    peticion.flush(null);
    await asentar(fixture);
    http.match(URL).forEach((p) => p.flush([]));
    await asentar(fixture);
    expect(raiz().querySelector('[data-externa]')).toBeNull();
  });
});
