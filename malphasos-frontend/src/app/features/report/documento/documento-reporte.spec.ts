import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import { ID_REPORTE, reporte, urlReporte } from '../../../../testing/reportes';
import { InformeDeReporte, ReporteDeServicio } from '../../../core/api/tipos';
import { nombreDelArchivo } from '../reporte-api';
import { DocumentoReporte } from './documento-reporte';

const URL_PDF = `${urlReporte(ID_REPORTE)}/pdf`;
const PDF = new Blob(['%PDF-1.7'], { type: 'application/pdf' });

/**
 * El informe de un reporte como el PDF del servidor (2026-10-17, RF-17). Lo que el PDF imprime lo fijan
 * las pruebas del backend —{@code ReportHtmlRendererTest}—, que heredaron los casos de esta cuando el
 * documento lo pintaba Angular. Aquí, que se pida, se muestre, se exporte y que sus avisos salgan.
 */
describe('Documento del reporte', () => {
  let fixture: ComponentFixture<DocumentoReporte>;
  let http: HttpTestingController;
  let creadas: string[];
  let liberadas: string[];
  const { createObjectURL, revokeObjectURL } = URL;

  beforeEach(() => {
    // jsdom no implementa las direcciones blob:.
    creadas = [];
    liberadas = [];
    let siguiente = 0;
    URL.createObjectURL = () => {
      const direccion = `blob:prueba/${++siguiente}`;
      creadas.push(direccion);
      return direccion;
    };
    URL.revokeObjectURL = (direccion: string) => liberadas.push(direccion);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['report.read'] }),
      ],
    });
    fixture = TestBed.createComponent(DocumentoReporte);
    fixture.componentRef.setInput('id', ID_REPORTE);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // En finally: si verify() lanza, el doble no se queda puesto para el archivo siguiente.
    try {
      http.verify();
    } finally {
      URL.createObjectURL = createObjectURL;
      URL.revokeObjectURL = revokeObjectURL;
    }
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => (raiz().textContent ?? '').replace(/\s+/g, ' ').trim();

  /** Responde a las tres peticiones: el PDF, el reporte y su informe, que dice si es tardío. */
  async function abrir(
    datos: Partial<ReporteDeServicio> = {},
    informe: Partial<InformeDeReporte> = {},
    responderPdf: (peticion: ReturnType<HttpTestingController['expectOne']>) => void = (peticion) =>
      peticion.flush(PDF, {
        headers: { 'Content-Disposition': 'inline; filename="informe-1DC6F5.pdf"' },
      }),
  ): Promise<void> {
    fixture.detectChanges();
    await asentar(fixture);
    responderPdf(http.expectOne(URL_PDF));
    http.expectOne(urlReporte(ID_REPORTE)).flush(reporte(datos));
    http.expectOne(`${urlReporte(ID_REPORTE)}/document`).flush({ tardio: false, ...informe });
    await asentar(fixture);
  }

  it('muestra el PDF del servidor en el visor', async () => {
    await abrir();

    expect(raiz().querySelector('iframe[data-visor]')!.getAttribute('src')).toBe('blob:prueba/1');
  });

  it('«Exportar PDF» lo descarga con el nombre que da el servidor', async () => {
    await abrir();
    const exportar = raiz().querySelector('[data-exportar]')!;

    expect(exportar.textContent?.trim()).toBe('Exportar PDF');
    expect(exportar.getAttribute('href')).toBe('blob:prueba/1');
    expect(exportar.getAttribute('download')).toBe('informe-1DC6F5.pdf');
  });

  it('mientras llega, lo dice; y no ofrece exportar lo que no hay', async () => {
    fixture.detectChanges();
    await asentar(fixture);

    expect(texto()).toContain('Preparando el PDF…');
    expect(raiz().querySelector('[data-exportar]')).toBeNull();

    http.expectOne(URL_PDF).flush(PDF);
    http.expectOne(urlReporte(ID_REPORTE)).flush(reporte());
    http.expectOne(`${urlReporte(ID_REPORTE)}/document`).flush({ tardio: false });
    await asentar(fixture);
  });

  it('un borrador lo avisa fuera del PDF', async () => {
    await abrir();

    expect(texto()).toContain('Este reporte está en borrador');
  });

  it('uno cerrado no lleva el aviso de borrador', async () => {
    await abrir({ estado: 'FINALIZADO', finalizado: '2026-10-01T09:00:00' });

    expect(texto()).not.toContain('Este reporte está en borrador');
  });

  it('un informe congelado tarde lo dice', async () => {
    // Decidido por el usuario el 2026-10-17: los reportes cerrados antes de V37 se congelaron después.
    await abrir({ estado: 'FINALIZADO', finalizado: '2026-10-01T09:00:00' }, { tardio: true });

    expect(raiz().querySelector('[data-tardio]')?.textContent).toContain(
      'se congeló después de cerrarse el reporte',
    );
  });

  it('si el conversor no responde, lo dice con el mensaje del catálogo, leído del cuerpo binario', async () => {
    // Una petición binaria recibe el error también como Blob: sin leerlo, el mensaje sería el genérico.
    await abrir({}, {}, (peticion) =>
      peticion.flush(
        new Blob([JSON.stringify({ code: 'ERR_SERVICE_REPORT_010' })], {
          type: 'application/json',
        }),
        { status: 503, statusText: 'Service Unavailable' },
      ),
    );

    expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
      'No se pudo generar el PDF del informe',
    );
    expect(raiz().querySelector('iframe')).toBeNull();
    expect(raiz().querySelector('[data-exportar]')).toBeNull();
  });

  it('un reporte que no existe, o que no se ve, lo dice', async () => {
    await abrir({}, {}, (peticion) =>
      peticion.flush(
        new Blob([JSON.stringify({ code: 'ERR_SERVICE_REPORT_001' })], {
          type: 'application/json',
        }),
        { status: 404, statusText: 'Not Found' },
      ),
    );

    expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
      'Ese reporte no existe o fue retirado.',
    );
  });

  it('enlaza de vuelta al reporte', async () => {
    await abrir();

    const volver = [...raiz().querySelectorAll('a')].find(
      (a) => a.textContent?.trim() === 'Volver al reporte',
    );
    expect(volver?.getAttribute('href')).toBe(`/reportes/${ID_REPORTE}`);
  });

  it('al salir de la pantalla libera la memoria del PDF', async () => {
    await abrir();

    fixture.destroy();

    expect(liberadas).toEqual(creadas);
  });
});

describe('El nombre del archivo', () => {
  it('sale de la cabecera, con o sin comillas', () => {
    expect(nombreDelArchivo('inline; filename="informe-1DC6F5.pdf"')).toBe('informe-1DC6F5.pdf');
    expect(nombreDelArchivo('attachment; filename=informe.pdf')).toBe('informe.pdf');
  });

  it('sin cabecera o sin nombre, nulo', () => {
    expect(nombreDelArchivo(null)).toBeNull();
    expect(nombreDelArchivo('inline')).toBeNull();
  });
});
