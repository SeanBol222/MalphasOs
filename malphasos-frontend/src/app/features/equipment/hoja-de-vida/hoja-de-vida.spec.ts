import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import { HojaDeVida } from './hoja-de-vida';

const ID = 'e1';
const URL_PDF = `http://localhost:8081/v1/api/client-equipments/${ID}/life-sheet/pdf`;
const PDF = new Blob(['%PDF-1.7'], { type: 'application/pdf' });

/**
 * La hoja de vida como el PDF del servidor (2026-10-18). Lo que el PDF imprime lo fijan las pruebas del
 * backend —{@code LifeSheetHtmlRendererTest} y {@code LifeSheetPrintComposerTest}—, que heredaron los casos
 * de esta cuando la hoja la pintaba Angular y se imprimía desde el navegador. Aquí, que se pida, se
 * muestre, se exporte y que sus errores se expliquen.
 */
describe('Hoja de vida', () => {
  let fixture: ComponentFixture<HojaDeVida>;
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
        ...proveerSesionFalsa({ autoridades: ['equipment.read'] }),
      ],
    });
    fixture = TestBed.createComponent(HojaDeVida);
    fixture.componentRef.setInput('id', ID);
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

  async function abrir(
    responder: (peticion: ReturnType<HttpTestingController['expectOne']>) => void = (peticion) =>
      peticion.flush(PDF, {
        headers: { 'Content-Disposition': 'inline; filename="hoja-de-vida-HV-CDN-0001.pdf"' },
      }),
  ): Promise<void> {
    fixture.detectChanges();
    await asentar(fixture);
    responder(http.expectOne(URL_PDF));
    await asentar(fixture);
  }

  it('muestra el PDF del servidor en el visor, sin pasar por la impresión del navegador', async () => {
    await abrir();

    expect(raiz().querySelector('iframe[data-visor]')!.getAttribute('src')).toBe('blob:prueba/1');
    expect(texto()).not.toContain('Imprimir');
  });

  it('«Exportar PDF» lo descarga con el nombre que da el servidor', async () => {
    await abrir();
    const exportar = raiz().querySelector('[data-exportar]')!;

    expect(exportar.textContent?.trim()).toBe('Exportar PDF');
    expect(exportar.getAttribute('href')).toBe('blob:prueba/1');
    expect(exportar.getAttribute('download')).toBe('hoja-de-vida-HV-CDN-0001.pdf');
  });

  it('sin nombre en la respuesta, se descarga con uno que dice qué es', async () => {
    await abrir((peticion) => peticion.flush(PDF));

    expect(raiz().querySelector('[data-exportar]')!.getAttribute('download')).toBe(
      'hoja-de-vida.pdf',
    );
  });

  it('mientras llega, lo dice; y no ofrece exportar lo que no hay', async () => {
    fixture.detectChanges();
    await asentar(fixture);

    expect(texto()).toContain('Preparando el PDF…');
    expect(raiz().querySelector('[data-exportar]')).toBeNull();

    http.expectOne(URL_PDF).flush(PDF);
    await asentar(fixture);
  });

  it('enlaza los reportes del equipo y la vuelta al listado', async () => {
    await abrir();

    expect(raiz().querySelector(`a[href="/equipos/${ID}/historial"]`)?.textContent?.trim()).toBe(
      'Ver los reportes',
    );
    expect(raiz().querySelector('a[href="/equipos"]')).not.toBeNull();
  });

  it('si el conversor no responde, lo dice con el mensaje del catálogo, leído del cuerpo binario', async () => {
    // Una petición binaria recibe el error también como Blob: sin leerlo, el mensaje sería el genérico.
    await abrir((peticion) =>
      peticion.flush(
        new Blob([JSON.stringify({ code: 'ERR_EQUIPMENT_020' })], { type: 'application/json' }),
        {
          status: 503,
          statusText: 'Service Unavailable',
        },
      ),
    );

    expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
      'No se pudo generar el PDF de la hoja de vida',
    );
    expect(raiz().querySelector('iframe')).toBeNull();
    expect(raiz().querySelector('[data-exportar]')).toBeNull();
  });

  it('un equipo que no existe, o que no se ve, lo dice', async () => {
    await abrir((peticion) =>
      peticion.flush(
        new Blob([JSON.stringify({ code: 'ERR_EQUIPMENT_006' })], { type: 'application/json' }),
        {
          status: 404,
          statusText: 'Not Found',
        },
      ),
    );

    expect(raiz().querySelector('[role="alert"]')).not.toBeNull();
    expect(raiz().querySelector('iframe')).toBeNull();
  });

  it('al salir de la pantalla libera la memoria del PDF', async () => {
    await abrir();

    fixture.destroy();

    expect(liberadas).toEqual(['blob:prueba/1']);
  });
});
