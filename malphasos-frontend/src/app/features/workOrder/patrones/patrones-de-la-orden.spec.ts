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
import { ID_ORDEN, orden, URL_ORDENES } from '../../../../testing/ordenes';
import { HOY } from '../../../core/fechas';
import { PatronesDeLaOrden } from './patrones-de-la-orden';

const URL_ORDEN = `${URL_ORDENES}/${ID_ORDEN}`;
const URL_PATRONES_DE_LA_ORDEN = `${URL_ORDEN}/reference-standards`;
const URL_CATALOGO_DEL_TIPO = `${URL_TIPOS}/${ID_TIPO}/reference-standards`;
const URL_ASIGNAR = `${URL_ORDEN}/equipment-types/${ID_TIPO}/reference-standards`;

/** Una segunda pesa vigente: una báscula se verifica con las de 5, 10 y 20 kg a la vez. */
const OTRA_PESA = {
  id: 'pat4',
  nombre: 'Pesa de 20 kg',
  serie: 'P-20',
  certificado: 'CAL-2026-20',
  vencimientoCertificado: '2099-06-30',
  estadoActivo: true,
};

/**
 * Los patrones que la orden lleva por tipo de equipo (V60, 2026-11-19): decidido por el usuario que la
 * orden los elija y cada reporte del tipo los reciba. Los datos traen un vigente, uno vencido, uno
 * retirado y, aquí, otra pesa vigente.
 */
describe('Patrones de la orden', () => {
  let fixture: ComponentFixture<PatronesDeLaOrden>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        provideRouter([]),
        { provide: HOY, useValue: () => new Date(2026, 10, 19) },
      ],
    });
    fixture = TestBed.createComponent(PatronesDeLaOrden);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_ORDEN);
  });

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => (raiz().textContent ?? '').replace(/\s+/g, ' ');
  const nombres = () =>
    [...raiz().querySelectorAll('li[data-patron] span.block:first-child')].map((n) =>
      n.textContent?.trim(),
    );
  const casilla = (idPatron: string) =>
    raiz().querySelector<HTMLInputElement>(`li[data-patron="${idPatron}"] input[type=checkbox]`)!;
  const guardar = () =>
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.includes('Guardar')) as
      HTMLButtonElement | undefined;

  async function abrir({
    laOrden = orden({ estadoEjecucion: 'EN_EJECUCION' }) as object,
    elegidos = [] as string[],
    admitidos = [ID_PATRON, 'pat2', 'pat3', OTRA_PESA.id] as string[],
    tipos = [{ idTipoEquipo: ID_TIPO, patrones: elegidos }] as object[],
  } = {}): Promise<void> {
    fixture.detectChanges();
    await asentar(fixture);
    http.match(URL_ORDEN).forEach((p) => p.flush(laOrden));
    http.match(URL_PATRONES_DE_LA_ORDEN).forEach((p) => p.flush(tipos));
    http.match(URL_TIPOS).forEach((p) => p.flush(TIPOS));
    http.match(URL_PATRONES).forEach((p) => p.flush([...PATRONES, OTRA_PESA]));
    await asentar(fixture);
    http
      .match(URL_CATALOGO_DEL_TIPO)
      .forEach((p) => p.flush([{ idVerificacion: 'v1', patrones: admitidos }]));
    await asentar(fixture);
  }

  it('ofrece por tipo los vigentes que el catalogo admite, y no el vencido ni el retirado', async () => {
    await abrir();

    expect(texto()).toContain('Tensiómetro');
    expect(nombres()).toEqual(['Juego de pesas clase F1', 'Pesa de 20 kg']);
    expect(texto()).toContain('ninguno elegido');
  });

  it('se eligen varios para el tipo, y guardarlo manda la lista entera de ese tipo', async () => {
    await abrir();

    casilla(ID_PATRON).click();
    casilla(OTRA_PESA.id).click();
    await asentar(fixture);
    expect(texto()).toContain('2 elegidos');
    guardar()!.click();
    await asentar(fixture);

    const peticion = http.expectOne(URL_ASIGNAR);
    expect(peticion.request.method).toBe('PATCH');
    expect(peticion.request.body).toEqual({ patrones: [ID_PATRON, OTRA_PESA.id] });
    peticion.flush({ idTipoEquipo: ID_TIPO, patrones: [ID_PATRON, OTRA_PESA.id] });
    await asentar(fixture);
    // Guardar caduca la orden y sus reportes: lo que se vuelva a pedir se responde con lo guardado.
    http
      .match(URL_PATRONES_DE_LA_ORDEN)
      .forEach((p) => p.flush([{ idTipoEquipo: ID_TIPO, patrones: [ID_PATRON, OTRA_PESA.id] }]));
    http.match(URL_ORDEN).forEach((p) => p.flush(orden({ estadoEjecucion: 'EN_EJECUCION' })));
    await asentar(fixture);

    expect(texto()).toContain('Guardado. Ya llegó a los reportes abiertos.');
  });

  it('sin cambios no deja guardar', async () => {
    await abrir({ elegidos: [ID_PATRON] });

    expect(casilla(ID_PATRON).checked).toBe(true);
    expect(guardar()!.disabled).toBe(true);
  });

  it('un elegido retirado o que el catalogo ya no admite se ve marcado, para poder quitarlo', async () => {
    await abrir({ elegidos: ['pat3'], admitidos: [ID_PATRON] });

    // La otra pesa esta vigente, pero el catalogo no la admite para el tipo: no se ofrece.
    expect(nombres()).toEqual(['Barómetro retirado', 'Juego de pesas clase F1']);
    const fila = raiz().querySelector('li[data-patron="pat3"]')!.textContent ?? '';
    expect(casilla('pat3').checked).toBe(true);
    expect(fila).toContain('Retirado');
    expect(fila).toContain('Ya no está en el catálogo del tipo');
  });

  it('si el catalogo no declara ninguno para el tipo, lo dice y manda alli', async () => {
    await abrir({ admitidos: [] });

    expect(raiz().querySelector('[data-sin-catalogo]')?.textContent).toContain(
      'Catálogo › Tipos › «Patrones»',
    );
  });

  it('una orden ejecutada los ensena sin dejar cambiarlos', async () => {
    await abrir({ laOrden: orden({ estadoEjecucion: 'EJECUTADA' }), elegidos: [ID_PATRON] });

    expect(raiz().querySelector('[data-no-editable]')).not.toBeNull();
    expect(casilla(ID_PATRON).disabled).toBe(true);
    expect(guardar()).toBeUndefined();
  });

  it('una orden sin equipos no tiene tipos para los que elegir', async () => {
    await abrir({ tipos: [] });

    expect(texto()).toContain('La orden no tiene equipos todavía');
  });

  it('si el servidor rechaza la lista, dice por que y no da nada por guardado', async () => {
    await abrir();

    casilla(ID_PATRON).click();
    await asentar(fixture);
    guardar()!.click();
    await asentar(fixture);
    http
      .expectOne(URL_ASIGNAR)
      .flush(
        { code: 'ERR_WORK_ORDER_003', message: 'x', details: ['ya ejecutada'] },
        { status: 409, statusText: 'Conflict' },
      );
    await asentar(fixture);

    expect(raiz().querySelector('[role=alert]')).not.toBeNull();
    expect(texto()).not.toContain('Guardado.');
  });
});
