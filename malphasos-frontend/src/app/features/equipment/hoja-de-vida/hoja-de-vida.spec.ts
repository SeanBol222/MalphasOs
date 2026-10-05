import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import QRCode from 'qrcode';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { responderA } from '../../../../testing/pantalla';
import { HojaDeVida } from './hoja-de-vida';

const ID_EQUIPO = 'e1';
const URL_HOJA = `http://localhost:8081/v1/api/client-equipments/${ID_EQUIPO}/life-sheet`;

/** Una hoja de vida como la compila el servidor, con el historial que se pida. */
function hoja(
  servicioTecnico: object[] = [],
  cambios: {
    estadoActivo?: boolean;
    responsables?: string[];
    telefonosCliente?: string[];
    correosCliente?: string[];
    /** Un equipo cuyo modelo, tipo y unidad no tienen llenos los datos opcionales. */
    sinOpcionales?: boolean;
  } = {},
): object {
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
      estadoActivo: cambios.estadoActivo ?? true,
      responsables: cambios.responsables ?? ['Carla Ruiz'],
      telefonosCliente: cambios.telefonosCliente ?? ['3001112233', '6015550000'],
      correosCliente: cambios.correosCliente ?? ['compras@hospital.co'],
      codigoInterno: cambios.sinOpcionales ? null : 'MON-03',
      proveedor: cambios.sinOpcionales ? null : 'Distribuidora Médica',
    },
    tecnica: {
      tipoEquipo: 'Monitor de signos vitales',
      definicionTecnica: 'Mide y muestra signos vitales',
      tecnologiaPredominante: 'Electrónica',
      recomendacionesCuidado: 'No exponer a humedad\nLimpiar la pantalla con un paño seco',
      uso: cambios.sinOpcionales ? null : 'Monitoreo de pacientes',
      limpiezaCotidiana: cambios.sinOpcionales ? null : 'Paño con alcohol al 70 %',
      marca: 'Mindray',
      modelo: 'iMEC 10',
      registroInvima: null,
      fichaTecnica: cambios.sinOpcionales
        ? {}
        : {
            riesgo: 'IIA',
            caracteristicas: 'Pantalla táctil',
            alimentacion: 'Red eléctrica',
            voltaje: 110,
            potencia: 45,
            amperaje: 1.5,
            frecuencia: 60,
          },
    },
    fabricante: { nombre: 'Mindray Medical', pais: 'China' },
    servicioTecnico,
    empresa: {
      nombre: 'Bolívar Bioingeniería Ltda.',
      direccion: 'Calle 77 C N.º 100 B 46, Villas del Madrigal',
      ciudad: 'Bogotá',
      telefonos: [],
      movil: '312 305 5157',
      correo: 'bolivarbioingenieria@gmail.com',
    },
  };
}

const RECIENTE = {
  id: 'i2',
  idEquipoCliente: ID_EQUIPO,
  idReporteServicio: 'r2',
  fechaServicio: '2026-10-04T21:30:00',
  tipoServicio: 'CALIBRACION',
  resultado: 'OPERATIVO_CON_RESTRICCIONES',
};

const ANTIGUA = {
  id: 'i1',
  idEquipoCliente: ID_EQUIPO,
  idReporteServicio: 'r1',
  fechaServicio: '2026-03-01T09:00:00',
  tipoServicio: 'PREVENTIVO',
  resultado: 'OPERATIVO',
};

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
  /** El valor de un dato por su etiqueta, dentro de un bloque: «Riesgo» → «—». */
  const dato = (bloque: Element | null, etiqueta: string) =>
    [...(bloque?.querySelectorAll('dt') ?? [])]
      .find((dt) => dt.textContent?.trim() === etiqueta)
      ?.nextElementSibling?.textContent?.trim();
  const estado = () => raiz().querySelector('[aria-label="Estado del equipo"]');
  const filasDelHistorial = () => [...raiz().querySelectorAll('.hv-tabla tbody tr.hv-fila')];

  async function abrir(cuerpo: object): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_HOJA, cuerpo);
  }

  it('pide la hoja de vida en una sola petición, ya compilada por el servidor', async () => {
    // Antes pintarla eran nueve consultas cruzadas aquí. Si alguien vuelve a armarla en el cliente,
    // http.verify() falla por las peticiones de más.
    await abrir(hoja());

    expect(raiz().querySelector('h1')?.textContent?.trim()).toBe(
      'Monitor de signos vitales Mindray iMEC 10',
    );
  });

  it('tiene los bloques del formato aprobado, que juntos son las cuatro secciones de RF-22', async () => {
    // RF-22 pide identificación, técnica, fabricante y servicio técnico. El formato los reparte en más
    // bloques —el cliente aparte, el fabricante dentro de «Qué es»—, pero ninguno de los cuatro falta.
    await abrir(hoja());

    const titulos = [...raiz().querySelectorAll('section h2')].map((h) => h.textContent?.trim());
    expect(titulos).toEqual([
      'Identificación',
      'Cliente',
      'Qué es',
      'Características técnicas',
      'Protocolo preventivo',
      'Cuidado y limpieza',
      'Historial de servicio técnico',
      'Servicio técnico',
    ]);
    expect(seccion('que-es')?.textContent).toContain('Mindray Medical');
  });

  it('enseña los datos que ya existen, cada uno en su sitio', async () => {
    await abrir(hoja());

    expect(dato(seccion('identificacion'), 'Marca · Modelo')).toBe('Mindray · iMEC 10');
    expect(dato(seccion('identificacion'), 'Área')).toBe('UCI');
    expect(dato(seccion('cliente'), 'Razón social · NIT')).toBe('Hospital Central · 900123456');
    expect(dato(seccion('cliente'), 'Dirección')).toBe('Calle 10 # 20 - 30-40');
    expect(dato(seccion('caracteristicas'), 'Voltaje')).toBe('110 V');
    expect(dato(seccion('caracteristicas'), 'Corriente')).toBe('1.5 A');
    expect(seccion('que-es')?.textContent).toContain('China');
    expect(seccion('que-es')?.textContent).toContain('01 feb 2025');
  });

  it('lo que el sistema todavía no guarda sale con «—», sin inventarlo', async () => {
    // El formato pide datos que llegan en tandas posteriores. Hasta entonces, la raya: un valor
    // inventado en un documento que se firma sería peor que uno vacío.
    await abrir(hoja());

    expect(dato(seccion('identificacion'), 'Registro INVIMA')).toBe('—');
  });

  it('la ficha del modelo, el uso del tipo y el codigo y proveedor de la unidad salen en su sitio', async () => {
    // Entraron el 2026-10-05. Voltaje y amperaje salen ahora de la ficha del MODELO, no del tipo.
    await abrir(hoja());

    expect(dato(estado(), 'Riesgo')).toBe('Clase IIa');
    expect(dato(seccion('caracteristicas'), 'Uso')).toBe('Monitoreo de pacientes');
    expect(dato(seccion('caracteristicas'), 'Alimentación')).toBe('Red eléctrica');
    expect(dato(seccion('caracteristicas'), 'Potencia')).toBe('45 W');
    expect(dato(seccion('caracteristicas'), 'Frecuencia')).toBe('60 Hz');
    expect(dato(seccion('caracteristicas'), 'Específicas')).toBe('Pantalla táctil');
    expect(dato(seccion('identificacion'), 'Placa · Código interno')).toBe('INV-7 · MON-03');
    expect(seccion('que-es')?.textContent).toContain('Proveedor Distribuidora Médica');
    expect(seccion('cuidado')?.textContent).toContain('Cada día: Paño con alcohol al 70 %');
  });

  it('un equipo sin los datos opcionales llenos los imprime con la raya', async () => {
    await abrir(hoja([], { sinOpcionales: true }));

    expect(dato(estado(), 'Riesgo')).toBe('—');
    expect(dato(seccion('caracteristicas'), 'Uso')).toBe('—');
    expect(dato(seccion('caracteristicas'), 'Voltaje')).toBe('— V');
    expect(dato(seccion('identificacion'), 'Placa · Código interno')).toBe('INV-7 · —');
    expect(seccion('cuidado')?.textContent).not.toContain('Cada día');
    expect(dato(seccion('identificacion'), 'Registro INVIMA')).toBe('—');
  });

  it('el responsable y los contactos del cliente salen en su tarjeta, varios separados por «·»', async () => {
    await abrir(hoja());

    expect(dato(seccion('cliente'), 'Responsable')).toBe('Carla Ruiz');
    expect(dato(seccion('cliente'), 'Teléfonos')).toBe('3001112233 · 6015550000');
    expect(dato(seccion('cliente'), 'Correo')).toBe('compras@hospital.co');
  });

  it('sin encargado ni contactos registrados, la raya: no se inventa a nadie', async () => {
    await abrir(hoja([], { responsables: [], telefonosCliente: [], correosCliente: [] }));

    expect(dato(seccion('cliente'), 'Responsable')).toBe('—');
    expect(dato(seccion('cliente'), 'Teléfonos')).toBe('—');
    expect(dato(seccion('cliente'), 'Correo')).toBe('—');
  });

  it('el servicio técnico lleva los datos de la empresa que manda el servidor, sin fijos vacíos', async () => {
    // Vienen de la configuración del backend. La empresa ya no tiene fijos: la línea de teléfonos
    // dice solo el móvil, sin un «·» colgando ni una etiqueta vacía.
    await abrir(hoja());

    const empresa = seccion('empresa')?.textContent?.replace(/\s+/g, ' ');
    expect(empresa).toContain('Bolívar Bioingeniería Ltda.');
    expect(empresa).toContain('Calle 77 C N.º 100 B 46, Villas del Madrigal, Bogotá');
    expect(empresa).toContain('Móvil 312 305 5157');
    expect(empresa).not.toContain(' · Móvil');
    expect(empresa).toContain('bolivarbioingenieria@gmail.com');
    expect(raiz().querySelector('.hv-firmas')?.textContent).toContain(
      'Elaboró · Bolívar Bioingeniería Ltda.',
    );
  });

  it('las recomendaciones de cuidado salen como lista, una por línea', async () => {
    await abrir(hoja());

    const consejos = [...(seccion('cuidado')?.querySelectorAll('li') ?? [])].map((li) =>
      li.textContent?.trim(),
    );
    // Las recomendaciones del tipo, una por linea, y al final la limpieza diaria, tambien del tipo.
    expect(consejos).toEqual([
      'No exponer a humedad',
      'Limpiar la pantalla con un paño seco',
      'Cada día: Paño con alcohol al 70 %',
    ]);
  });

  it('la fila de estado sale del historial: la intervención más reciente es el estado actual', async () => {
    await abrir(hoja([RECIENTE, ANTIGUA]));

    expect(dato(estado(), 'Estado actual')).toBe('Operativo con restricciones');
    expect(dato(estado(), 'Último servicio')).toBe('04 oct 2026');
    expect(dato(estado(), 'Intervenciones')).toBe('2');
  });

  it('la fecha del servicio es la del día en que se cerró, aunque fuera de noche', async () => {
    // El servidor manda fecha y hora locales. Pasarlas por Date en UTC corre un cierre de las 21:30 al
    // día siguiente en Bogotá.
    await abrir(hoja([RECIENTE]));

    expect(filasDelHistorial()[0].textContent).toContain('04 oct 2026');
  });

  it('un equipo sin mantenimientos tiene hoja de vida, con el historial vacío y dicho', async () => {
    // Es la aclaración que ordenó este trabajo: la hoja de vida no sale de los mantenimientos.
    await abrir(hoja([]));

    expect(dato(estado(), 'Estado actual')).toBe('Sin servicios');
    expect(dato(estado(), 'Intervenciones')).toBe('0');
    expect(seccion('servicio-tecnico')?.textContent).toContain('Sin intervenciones todavía');
    expect(filasDelHistorial().length).toBe(0);
  });

  it('el historial se pinta en el orden en que llega, con su resultado marcado y su reporte enlazado', async () => {
    // El orden lo fija el servidor —es parte del contrato de RF-27— y aquí no se reordena.
    await abrir(hoja([RECIENTE, ANTIGUA]));

    const filas = filasDelHistorial();
    expect(filas.length).toBe(2);
    expect(filas[0].textContent).toContain('Calibración');
    expect(filas[0].textContent).toContain('Operativo con restricciones');
    expect(filas[1].textContent).toContain('Preventivo');
    // La marca distingue el resultado por la forma; la clase es la que la dibuja.
    expect(filas[0].querySelector('.hv-marca--OPERATIVO_CON_RESTRICCIONES')).not.toBeNull();
    expect(filas[1].querySelector('.hv-marca--OPERATIVO')).not.toBeNull();
    expect(filas[0].querySelector('a')?.getAttribute('href')).toBe('/reportes/r2');
  });

  it('deja filas en blanco para anotar a mano, que no cuentan como intervenciones', async () => {
    await abrir(hoja([ANTIGUA]));

    expect(raiz().querySelectorAll('.hv-tabla tbody tr.hv-en-blanco').length).toBe(6);
    expect(dato(estado(), 'Intervenciones')).toBe('1');
  });

  it('el código QR lleva a esta misma hoja de vida en la aplicación', async () => {
    // Decidido el 2026-10-05: abre la aplicación y pide iniciar sesión. Se comprueba contra la matriz
    // que genera la librería para esa dirección, no contra una imagen.
    await abrir(hoja());

    const direccion = `${window.location.origin}/equipos/${ID_EQUIPO}/hoja-de-vida`;
    const qr = raiz().querySelector('.hv-qr svg');
    const { modules } = QRCode.create(direccion, { errorCorrectionLevel: 'M' });

    expect(qr?.getAttribute('aria-label')).toContain(direccion);
    expect(qr?.getAttribute('viewBox')).toBe(`0 0 ${modules.size} ${modules.size}`);
    const oscuros = Array.from(modules.data).filter(Boolean).length;
    expect(qr?.querySelector('path')?.getAttribute('d')?.match(/M/g)?.length).toBe(oscuros);
  });

  it('un equipo dado de baja lo dice en la cabecera', async () => {
    await abrir(hoja([], { estadoActivo: false }));

    expect(raiz().querySelector('.hv-banda')?.textContent).toContain('De baja');
  });

  it('no tiene un solo control editable: es un documento, no un formulario', async () => {
    // La decisión sobre RF-24. Cada dato se corrige donde vive.
    await abrir(hoja());

    expect(raiz().querySelectorAll('input, select, textarea').length).toBe(0);
  });

  it('el botón de imprimir llama a la impresión del navegador', async () => {
    // Decidido el 2026-10-05: se imprime desde el navegador, sin PDF del servidor.
    await abrir(hoja());
    const imprimir = vi.spyOn(window, 'print').mockImplementation(() => undefined);

    try {
      [...raiz().querySelectorAll('button')]
        .find((b) => b.textContent?.trim() === 'Imprimir')!
        .click();

      expect(imprimir).toHaveBeenCalledTimes(1);
    } finally {
      imprimir.mockRestore();
    }
  });

  it('un equipo ajeno o inexistente responde con el mensaje del catálogo, sin pintar la hoja', async () => {
    await responderA(
      fixture,
      http,
      URL_HOJA,
      { code: 'ERR_EQUIPMENT_006', message: 'Client equipment not found', details: [] },
      { status: 404, statusText: 'Not Found' },
    );

    expect(raiz().querySelector('[role="alert"]')).not.toBeNull();
    expect(raiz().querySelector('article')).toBeNull();
  });
});
