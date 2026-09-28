import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import {
  EQUIPOS,
  ID_EQUIPO,
  ID_ORDEN,
  orden,
  URL_EQUIPOS_DE_CLIENTE,
  URL_ORDENES,
} from '../../../../testing/ordenes';
import { reporte, urlReportesDeEquipo } from '../../../../testing/reportes';
import { HistorialEquipo } from './historial-equipo';

describe('Historial de servicio de un equipo', () => {
  let fixture: ComponentFixture<HistorialEquipo>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['report.read', 'equipment.read', 'work-order.read'] }),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(HistorialEquipo);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_EQUIPO);
  });

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const filas = () => [...raiz().querySelectorAll('tbody tr')];
  const celdas = (i: number) =>
    [...filas()[i].querySelectorAll('td')].map((celda) => (celda.textContent ?? '').trim());

  /** Abre el historial y responde a sus tres consultas: reportes, equipos y órdenes. */
  async function abrir(reportes: readonly object[], ordenes: readonly object[] = []): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, urlReportesDeEquipo(ID_EQUIPO), reportes);
    http.match(URL_EQUIPOS_DE_CLIENTE).forEach((p) => p.flush(EQUIPOS));
    http.match(URL_ORDENES).forEach((p) => p.flush(ordenes));
    await asentar(fixture);
  }

  it('pide los reportes del equipo con el filtro que el API exige', async () => {
    // Sin filtro el API responde 400: un reporte no se consulta suelto. La consulta lleva el equipo.
    await abrir([]);

    expect(texto()).toContain('SN-0001');
    expect(texto()).toContain('no tiene reportes todavía');
  });

  it('enseña la fecha de cierre cuando el reporte está cerrado', async () => {
    await abrir([
      reporte({
        id: 'r1',
        estado: 'FINALIZADO',
        finalizado: '2026-09-20T15:30:00',
        resultado: 'OPERATIVO',
        lecturas: [
          { id: 'd1', idPuntoVerificacion: 'pv1', secuencia: 1, valorPatron: 100, valorEquipo: 101, unidad: 'mmHg' },
        ],
      }),
    ]);

    const fila = celdas(0);
    expect(fila[0]).toContain('2026-09-20');
    expect(fila[0]).not.toContain('programada');
    expect(fila[2]).toBe('Operativo');
    expect(fila[3]).toBe('1');
  });

  it('un borrador cae a la fecha programada de su orden, y lo dice', async () => {
    // Un borrador no tiene fecha propia porque nadie cerró nada: lo único que se sabe es cuándo estaba
    // programado el trabajo. Decir esa fecha sin avisar la haría pasar por fecha de servicio.
    await abrir([reporte({ id: 'r1' })], [orden({ estadoEjecucion: 'EN_EJECUCION' })]);

    expect(celdas(0)[0]).toContain('2026-10-15');
    expect(celdas(0)[0]).toContain('programada');
  });

  it('los reportes retirados también salen: retirarlos no reescribe la historia', async () => {
    await abrir([
      reporte({ id: 'r1', estadoActivo: false, estado: 'FINALIZADO', finalizado: '2026-09-10T09:00:00' }),
    ]);

    expect(filas()).toHaveLength(1);
    expect(celdas(0)[4]).toBe('Retirado');
  });

  it('se lee del más reciente al más antiguo', async () => {
    await abrir([
      reporte({ id: 'viejo', estado: 'FINALIZADO', finalizado: '2026-01-10T09:00:00' }),
      reporte({ id: 'nuevo', estado: 'FINALIZADO', finalizado: '2026-09-20T09:00:00' }),
    ]);

    expect(celdas(0)[0]).toContain('2026-09-20');
    expect(celdas(1)[0]).toContain('2026-01-10');
  });

  it('cada fila enlaza a su reporte y a la orden en la que se hizo', async () => {
    await abrir([reporte({ id: 'r1' })], [orden()]);

    const enlaces = [...filas()[0].querySelectorAll('a')].map((a) => a.getAttribute('href'));
    expect(enlaces).toContain('/reportes/r1');
    expect(enlaces).toContain(`/ordenes/${ID_ORDEN}`);
  });

  it('si la orden no está en la lista, la fila sigue leyéndose', async () => {
    // Las órdenes se cruzan para poner el tipo de servicio y la fecha programada. Que falten no puede
    // dejar la fila en blanco: el reporte es lo que se está mirando.
    await abrir([reporte({ id: 'r1', estado: 'FINALIZADO', finalizado: '2026-09-20T09:00:00' })], []);

    expect(celdas(0)[1]).toBe('—');
    expect(celdas(0)[0]).toContain('2026-09-20');
  });
});
