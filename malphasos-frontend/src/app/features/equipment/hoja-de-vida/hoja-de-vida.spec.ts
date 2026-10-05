import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { responderA } from '../../../../testing/pantalla';
import { HojaDeVida } from './hoja-de-vida';

const ID_EQUIPO = 'e1';
const URL_HOJA = `http://localhost:8081/v1/api/client-equipments/${ID_EQUIPO}/life-sheet`;

/** Una hoja de vida como la compila el servidor, con el historial que se pida. */
function hoja(servicioTecnico: object[] = []): object {
  return {
    identificacion: {
      idEquipoCliente: ID_EQUIPO,
      serie: 'SN-0001',
      numeroInventario: 'INV-7',
      fechaCompra: '2025-02-01',
      valorCompra: 12500000,
      cliente: 'Hospital Central',
      documentoCliente: '900123456',
      sede: 'Sede Norte',
      direccionSede: 'Calle 10 # 20 - 30-40',
      ciudadSede: 'Bogotá',
      areaServicio: 'UCI',
      estadoActivo: true,
    },
    tecnica: {
      tipoEquipo: 'Monitor de signos vitales',
      definicionTecnica: 'Mide y muestra signos vitales',
      tecnologiaPredominante: 'Electrónica',
      recomendacionesCuidado: 'No exponer a humedad',
      voltaje: 110,
      amperaje: 1.5,
      marca: 'Mindray',
      modelo: 'iMEC 10',
      registroInvima: null,
    },
    fabricante: { nombre: 'Mindray Medical', pais: 'China' },
    servicioTecnico,
  };
}

describe('Hoja de vida de un equipo', () => {
  let fixture: ComponentFixture<HojaDeVida>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['equipment.read'] }),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(HojaDeVida);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_EQUIPO);
  });

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const seccion = (id: string) => raiz().querySelector(`section[aria-labelledby="${id}"]`);

  async function abrir(cuerpo: object): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_HOJA, cuerpo);
  }

  it('pide la hoja de vida en una sola petición, ya compilada por el servidor', async () => {
    // Antes pintarla eran nueve consultas cruzadas aquí. Si alguien vuelve a armarla en el cliente,
    // http.verify() falla por las peticiones de más.
    await abrir(hoja());

    expect(texto()).toContain('Hoja de vida · SN-0001');
  });

  it('tiene las cuatro secciones de RF-22, con esos nombres y en ese orden', async () => {
    await abrir(hoja());

    const titulos = [...raiz().querySelectorAll('section h2')].map((h) => h.textContent?.trim());
    expect(titulos).toEqual(['Identificación', 'Técnica', 'Fabricante', 'Servicio técnico']);
  });

  it('enseña los datos de cada sección', async () => {
    await abrir(hoja());

    expect(seccion('identificacion')?.textContent).toContain('Hospital Central');
    expect(seccion('identificacion')?.textContent).toContain('Calle 10 # 20 - 30-40');
    expect(seccion('identificacion')?.textContent).toContain('UCI');
    expect(seccion('tecnica')?.textContent).toContain('Mindray');
    expect(seccion('tecnica')?.textContent).toContain('110 V');
    expect(seccion('fabricante')?.textContent).toContain('China');
  });

  it('un equipo sin mantenimientos tiene hoja de vida, con el historial vacío y dicho', async () => {
    // Es la aclaración que ordenó este trabajo: la hoja de vida no sale de los mantenimientos. La
    // cuarta sección no se esconde; dice que está vacía.
    await abrir(hoja([]));

    expect(seccion('servicio-tecnico')).not.toBeNull();
    expect(seccion('servicio-tecnico')?.textContent).toContain('Sin intervenciones todavía');
    expect(seccion('servicio-tecnico')?.querySelector('table')).toBeNull();
  });

  it('el historial se pinta en el orden en que llega, con su reporte enlazado', async () => {
    // El orden lo fija el servidor —es parte del contrato de RF-27— y aquí no se reordena: si la
    // pantalla ordenara por su cuenta, podría contradecir al documento impreso del servidor.
    await abrir(
      hoja([
        {
          id: 'i2',
          idEquipoCliente: ID_EQUIPO,
          idReporteServicio: 'r2',
          fechaServicio: '2026-10-04T15:30:00',
          tipoServicio: 'CALIBRACION',
          resultado: 'OPERATIVO_CON_RESTRICCIONES',
        },
        {
          id: 'i1',
          idEquipoCliente: ID_EQUIPO,
          idReporteServicio: 'r1',
          fechaServicio: '2026-03-01T09:00:00',
          tipoServicio: 'PREVENTIVO',
          resultado: 'OPERATIVO',
        },
      ]),
    );

    const filas = [...(seccion('servicio-tecnico')?.querySelectorAll('tbody tr') ?? [])];
    expect(filas.length).toBe(2);
    expect(filas[0].textContent).toContain('2026-10-04');
    expect(filas[0].textContent).toContain('Calibración');
    expect(filas[0].textContent).toContain('Operativo con restricciones');
    expect(filas[1].textContent).toContain('Preventivo');
    expect(filas[0].querySelector('a')?.getAttribute('href')).toBe('/reportes/r2');
  });

  it('no tiene un solo control editable: es un documento, no un formulario', async () => {
    // La decisión sobre RF-24. Cada dato se corrige donde vive; si un campo apareciera aquí, la
    // pantalla invitaría a cambiar la marca de un equipo sin decir que la cambia para todos.
    await abrir(hoja());

    expect(raiz().querySelectorAll('input, select, textarea').length).toBe(0);
  });

  it('un equipo ajeno o inexistente responde con el mensaje del catálogo, sin pintar secciones', async () => {
    await responderA(
      fixture,
      http,
      URL_HOJA,
      { code: 'ERR_EQUIPMENT_006', message: 'Client equipment not found', details: [] },
      { status: 404, statusText: 'Not Found' },
    );

    expect(raiz().querySelector('[role="alert"]')).not.toBeNull();
    expect(raiz().querySelectorAll('section').length).toBe(0);
  });
});
