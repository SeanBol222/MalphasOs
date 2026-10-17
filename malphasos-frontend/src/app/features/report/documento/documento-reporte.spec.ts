import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import {
  EQUIPOS as EQUIPOS_DE_CATALOGO,
  ID_VERIFICACION,
  MODELOS,
  TIPOS,
  URL_EQUIPOS as URL_EQUIPOS_DE_CATALOGO,
  URL_MODELOS,
  URL_TIPOS,
} from '../../../../testing/catalogo';
import {
  EQUIPOS,
  ID_EQUIPO,
  ID_INGENIERO,
  ID_ORDEN,
  orden,
  PERSONAS,
  URL_EQUIPOS_DE_CLIENTE,
  URL_ORDENES,
  URL_PERSONAS,
} from '../../../../testing/ordenes';
import { ID_REPORTE, reporte, urlReporte } from '../../../../testing/reportes';
import { ReporteDeServicio } from '../../../core/api/tipos';
import { DocumentoReporte, proximoServicio } from './documento-reporte';

const URL_HOJA = `http://localhost:8081/v1/api/client-equipments/${ID_EQUIPO}/life-sheet`;

/** La hoja de vida del equipo, como la compila el servidor: lo que el documento toma de ella. */
const GARANTIA_DE_EJEMPLO = 'La garantía cubre lo que se reporte en los cinco días siguientes.';

const EMPRESA = {
  nombre: 'Servicios de Ejemplo S.A.S.',
  direccion: 'Carrera 1 N.º 2-3',
  ciudad: 'Ciudad de Ejemplo',
  telefonos: [],
  movil: '300 000 0000',
  aclaracion: 'Mantenimiento realizado bajo el protocolo de la empresa.',
  garantia: GARANTIA_DE_EJEMPLO,
  codigoInforme: 'FT-002 · Versión 1',
};

/** La hoja de vida con una empresa que no configuró ninguno de sus textos ni su logo. */
function hojaSinTextos() {
  return { ...hoja(), empresa: { nombre: 'Servicios de Ejemplo S.A.S.', telefonos: [] } };
}

function hoja(servicioTecnico: object[] = []) {
  return {
    identificacion: {
      idEquipoCliente: ID_EQUIPO,
      numeroHojaVida: 'HV-HCE-0007',
      serie: 'SN-0001',
      numeroInventario: 'INV-77',
      codigoInterno: 'MON-03',
      cliente: 'Hospital Central',
      documentoCliente: '900123456',
      sede: 'Sede Norte',
      direccionSede: 'Calle 10 # 20 - 30',
      ciudadSede: 'Bogotá',
      areaServicio: 'Urgencias',
      estadoActivo: true,
      responsables: ['Carla Ruiz'],
      telefonosCliente: ['3001112233'],
      correosCliente: ['compras@hospital.co'],
    },
    tecnica: {
      tipoEquipo: 'Tensiómetro',
      tecnologiaPredominante: 'Electrónica',
      uso: 'Medir la presión arterial',
      marca: 'Welch Allyn',
      modelo: 'ProBP 2400',
      fichaTecnica: { riesgo: 'IIA', alimentacion: 'AC', voltaje: 110, frecuencia: 60 },
      protocoloPreventivo: [],
    },
    fabricante: { nombre: 'Welch Allyn' },
    servicioTecnico,
    // Una empresa inventada, con sus textos: MalphasOS no lleva los de ninguna real (2026-10-16).
    empresa: EMPRESA,
  };
}

/** Dos lecturas en el punto de 100 mmHg con ±0,5: una dentro y otra fuera. */
const LECTURAS = [
  {
    id: 'l1',
    idVerificacion: ID_VERIFICACION,
    idPuntoVerificacion: 'pv1',
    secuencia: 1,
    valorPatron: 100,
    valorEquipo: 100.2,
    unidad: 'mmHg',
    tolerancia: 0.5,
    error: 0.2,
    conforme: true,
  },
  {
    id: 'l2',
    idVerificacion: ID_VERIFICACION,
    idPuntoVerificacion: 'pv1',
    secuencia: 2,
    valorPatron: 100,
    valorEquipo: 100.9,
    unidad: 'mmHg',
    tolerancia: 0.5,
    error: 0.9,
    conforme: false,
  },
];

const PATRON = {
  idVerificacion: ID_VERIFICACION,
  id: 'pat1',
  nombre: 'Manómetro patrón',
  marca: 'Fluke',
  modelo: '700G',
  serie: 'M-1',
  certificado: 'CAL-1',
  vencimiento: '2099-04-30',
  codigoInterno: 'BB-PAT-003',
  rango: '0 a 300 mmHg',
  resolucion: '0,1 mmHg',
  fechaCalibracion: '2026-04-30',
  empresaCalibradora: 'Metrología Andina',
};

/** El reporte como documento (2026-10-16). */
describe('Documento del reporte', () => {
  let fixture: ComponentFixture<DocumentoReporte>;
  let http: HttpTestingController;

  beforeEach(() => {
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

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = (selector?: string) =>
    ((selector ? raiz().querySelector(selector) : raiz())?.textContent ?? '')
      .replace(/\s+/g, ' ')
      .trim();
  /** Las celdas de una tabla, una a una: el texto de la fila entera las pega. */
  const celdas = (selector: string) =>
    [...raiz().querySelectorAll(`${selector} td`)].map((td) =>
      td.textContent?.replace(/\s+/g, ' ').trim(),
    );

  /** Responde a todo lo que el documento pide, en las vueltas que haga falta: unas consultas esperan a otras. */
  async function abrir(
    datos: Partial<ReporteDeServicio> = {},
    contexto: { orden?: object; hoja?: object; tipos?: object[] } = {},
  ): Promise<void> {
    const respuestas = new Map<string, unknown>([
      [urlReporte(ID_REPORTE), reporte(datos)],
      [`${URL_ORDENES}/${ID_ORDEN}`, contexto.orden ?? orden({ idIngeniero: ID_INGENIERO })],
      [URL_HOJA, contexto.hoja ?? hoja()],
      [URL_PERSONAS, PERSONAS],
      [URL_EQUIPOS_DE_CLIENTE, EQUIPOS],
      [URL_MODELOS, MODELOS],
      [URL_EQUIPOS_DE_CATALOGO, EQUIPOS_DE_CATALOGO],
      [URL_TIPOS, contexto.tipos ?? TIPOS],
    ]);

    fixture.detectChanges();
    for (let vuelta = 0; vuelta < 4; vuelta++) {
      await asentar(fixture);
      http
        .match((peticion) => respuestas.has(peticion.url))
        .forEach((peticion) => peticion.flush(respuestas.get(peticion.request.url) as object));
    }
    await asentar(fixture);
  }

  it('la banda dice el número, el título único y el equipo', async () => {
    // Un solo informe, decidido el 2026-10-16: el tipo de servicio lo dicen las casillas, no el título.
    await abrir({ numero: '1DC6F5' });

    expect(texto('[data-numero]')).toBe('Informe N.º 1DC6F5');
    expect(texto('[data-numero-orden]')).toBe('Orden N.º 4F2A9C');
    expect(texto('h1')).toBe('Informe de mantenimiento');
    expect(texto('.rs-banda__equipo')).toBe(
      'Tensiómetro · Welch Allyn ProBP 2400 · Serie SN-0001 · HV-HCE-0007',
    );
  });

  it('un borrador lleva la fecha programada, y el próximo servicio suma la periodicidad', async () => {
    // La orden es trimestral y está programada para el 15 de octubre.
    await abrir();

    expect(texto('[data-proximo]')).toBe('15 ene 2027');
    expect(raiz().querySelector('.rs-marca-borrador')).not.toBeNull();
  });

  it('uno cerrado lleva la fecha del cierre, sin marca de borrador', async () => {
    await abrir({
      estado: 'FINALIZADO',
      finalizado: '2026-10-20T16:30:00',
      resultado: 'OPERATIVO',
    });

    expect(texto('[data-proximo]')).toBe('20 ene 2027');
    expect(raiz().querySelector('.rs-marca-borrador')).toBeNull();
    expect(texto('[data-resultado]')).toBe('Apto para el servicio');
  });

  // Tres valores guardados, dos impresos (2026-10-16): la restricción va en las observaciones.
  for (const [resultado, impreso] of [
    ['OPERATIVO', 'Apto para el servicio'],
    ['OPERATIVO_CON_RESTRICCIONES', 'Apto para el servicio'],
    ['FUERA_DE_SERVICIO', 'No apto para el servicio'],
  ] as const) {
    it(`el resultado ${resultado} se imprime «${impreso}»`, async () => {
      await abrir({ estado: 'FINALIZADO', finalizado: '2026-10-20T16:30:00', resultado });

      expect(texto('[data-resultado]')).toBe(impreso);
    });
  }

  it('el supervisor es el encargado del área, en la ficha del cliente y en las firmas', async () => {
    await abrir();

    expect(texto('[data-supervisor]')).toBe('Carla Ruiz');
    expect(texto('.rs-firmas')).toContain('Carla Ruiz Supervisor responsable');
  });

  it('marca el tipo de servicio de la orden, y la verificación metrológica sola si hay lecturas', async () => {
    await abrir({ lecturas: LECTURAS });

    const marcadas = [...raiz().querySelectorAll('[data-casilla].rs-marcada')].map((c) =>
      c.getAttribute('data-casilla'),
    );
    expect(marcadas).toEqual(['Preventivo', 'Verificación metrológica']);
    expect(
      [...raiz().querySelectorAll('[data-casilla]')].map((c) => c.getAttribute('data-casilla')),
    ).toEqual(['Preventivo', 'Correctivo', 'Calibración', 'Verificación metrológica', 'Garantía']);
  });

  it('un correctivo en garantía marca las dos casillas', async () => {
    await abrir(
      {},
      {
        orden: orden({ idIngeniero: ID_INGENIERO, tipoServicio: 'CORRECTIVO', garantia: true }),
      },
    );

    const marcadas = [...raiz().querySelectorAll('[data-casilla].rs-marcada')].map((c) =>
      c.getAttribute('data-casilla'),
    );
    expect(marcadas).toEqual(['Correctivo', 'Garantía']);
  });

  it('sin lecturas la verificación no se marca, y lo dice', async () => {
    await abrir();

    expect(raiz().querySelector('[data-casilla="Verificación metrológica"].rs-marcada')).toBeNull();
    expect(texto('[data-verificacion]')).toBe('Sin verificación');
  });

  it('cuenta las conformes entre las que tienen tolerancia', async () => {
    await abrir({ lecturas: LECTURAS });

    expect(texto('[data-verificacion]')).toBe('1 de 2 conformes');
  });

  it('las lecturas sin tolerancia no cuentan como conformes ni como no conformes: no se sabe', async () => {
    const sinTolerancia = LECTURAS.map((lectura) => ({
      ...lectura,
      tolerancia: undefined,
      conforme: undefined,
    }));
    await abrir({ lecturas: [...sinTolerancia, LECTURAS[0]] });

    expect(texto('[data-verificacion]')).toBe('1 de 1 conformes');
  });

  it('un patrón usado en dos verificaciones sale una sola vez en la trazabilidad', async () => {
    await abrir({ lecturas: LECTURAS, patrones: [PATRON, { ...PATRON, idVerificacion: 'ver2' }] });

    expect(raiz().querySelectorAll('[data-trazabilidad]')).toHaveLength(1);
  });

  it('el ingreso, los accesorios y quien recibe salen como se registraron', async () => {
    await abrir({
      ingresoFisico: { estado: 'NO_CONFORME', observacion: 'Carcasa rayada' },
      ingresoOperativo: { estado: 'CONFORME' },
      accesorios: [{ orden: 1, nombre: 'Brazalete', conforme: true }],
      recibe: { nombre: 'Ana Pérez', cedula: '10000001', cargo: 'Jefe de enfermería' },
    });

    expect(celdas('.rs-tabla').slice(0, 3)).toEqual([
      'Estado de la estructura física del equipo',
      'No conforme',
      'Carcasa rayada',
    ]);
    expect(celdas('[data-accesorio]')).toEqual(['Accesorio: Brazalete', 'Conforme', '—']);
    expect(texto('[data-firma-recibe]')).toBe('Ana Pérez · C.C. 10000001');
    expect(texto('.rs-firmas')).toContain('Recibe · Jefe de enfermería');
  });

  it('sin registrar, el ingreso lo dice y los accesorios dicen que no tiene', async () => {
    await abrir();

    expect(celdas('.rs-tabla').slice(3, 5)).toEqual([
      'Estado operativo del equipo',
      'Sin registrar',
    ]);
    expect(celdas('.rs-tabla').slice(6, 8)).toEqual(['Accesorios', 'No tiene']);
  });

  it('en un borrador el profesional es el ingeniero de la orden', async () => {
    await abrir();

    expect(texto('[data-firma-profesional]')).toBe('Ana Ruiz');
  });

  it('cerrado, el profesional es el que quedó congelado en la hoja de vida', async () => {
    const linea = { id: 'i1', idReporteServicio: ID_REPORTE, responsable: 'Grace Hopper' };
    await abrir(
      { estado: 'FINALIZADO', finalizado: '2026-10-20T16:30:00' },
      { hoja: hoja([linea]) },
    );

    expect(texto('[data-firma-profesional]')).toBe('Grace Hopper');
  });

  it('la aclaración y la garantía son los textos que la empresa configuró', async () => {
    await abrir({ lecturas: LECTURAS, patrones: [PATRON] });

    expect(texto('[data-aclaracion]')).toBe(
      'Aclaración del proceso. Mantenimiento realizado bajo el protocolo de la empresa.',
    );
    expect(texto('[data-garantia]')).toBe(`Garantía. ${GARANTIA_DE_EJEMPLO}`);
  });

  it('sin textos configurados no hay aclaración ni garantía, en vez de un rótulo vacío', async () => {
    await abrir({ lecturas: LECTURAS, patrones: [PATRON] }, { hoja: hojaSinTextos() });

    expect(raiz().querySelector('[data-aclaracion]')).toBeNull();
    expect(raiz().querySelector('[data-garantia]')).toBeNull();
  });

  it('el pie lleva el código del formato que la empresa configuró y su dirección', async () => {
    await abrir();

    expect(texto('[data-pie]')).toBe(
      'FT-002 · Versión 1 · Carrera 1 N.º 2-3, Ciudad de Ejemplo · 300 000 0000',
    );
  });

  it('el membrete es el nombre y el logo configurados; sin logo, solo el nombre', async () => {
    await abrir(
      { lecturas: LECTURAS, patrones: [PATRON] },
      { hoja: { ...hoja(), empresa: { ...EMPRESA, logo: 'data:image/png;base64,AAAA' } } },
    );

    expect(texto('.rs-logo')).toBe('Servicios de Ejemplo S.A.S.');
    expect(raiz().querySelectorAll('img.rs-escudo')).toHaveLength(2);
    expect(raiz().querySelector('.rs-logo img')?.getAttribute('src')).toBe(
      'data:image/png;base64,AAAA',
    );
  });

  it('sin logo configurado no hay imagen ni marca de agua', async () => {
    await abrir({ lecturas: LECTURAS, patrones: [PATRON] }, { hoja: hojaSinTextos() });

    expect(raiz().querySelectorAll('img')).toHaveLength(0);
    expect(texto('.rs-logo')).toBe('Servicios de Ejemplo S.A.S.');
  });

  it('con verificación hay página 2: la tabla, la gráfica y los patrones', async () => {
    await abrir({ lecturas: LECTURAS, patrones: [PATRON] });

    const pagina = raiz().querySelector('[data-pagina="2"]')!;
    expect(pagina).not.toBeNull();
    expect(celdas('[data-trazabilidad]')).toEqual([
      'Manómetro patrón Fluke 700G · Presión',
      'M-1 BB-PAT-003',
      '0 a 300 mmHg 0,1 mmHg',
      'CAL-1',
      '30 abr 2026 vence 30 abr 2099',
      'Metrología Andina',
    ]);
    expect(celdas('[data-verificacion-documento]')).toEqual(
      ['100 mmHg · 1', '100', '100,2', '+0,2', 'Sí'].concat([
        '100 mmHg · 2',
        '100',
        '100,9',
        '+0,9',
        'No',
      ]),
    );
    expect(pagina.querySelectorAll('[data-verificacion-documento] tbody tr.rs-fuera')).toHaveLength(
      1,
    );
    expect(pagina.querySelectorAll('circle[data-fuera]')).toHaveLength(1);
    expect(pagina.querySelector('rect[data-banda]')).not.toBeNull();
    expect(texto('[data-pagina="2"]')).toContain(GARANTIA_DE_EJEMPLO);
    expect(texto('[data-pagina="2"]')).toContain('Página 2 de 2');
  });

  it('sin lecturas el informe es de una hoja, y la garantía lo cierra en ella', async () => {
    // Las secciones salen según los datos (2026-10-16): sin lecturas no hay nada que verificar.
    await abrir();

    expect(raiz().querySelector('[data-pagina="2"]')).toBeNull();
    expect(texto()).toContain('Página 1 de 1');
    expect(raiz().querySelectorAll('[data-garantia]')).toHaveLength(1);
    expect(texto('[data-garantia]')).toBe(`Garantía. ${GARANTIA_DE_EJEMPLO}`);
  });

  it('con lecturas la garantía va solo en la segunda hoja, no repetida', async () => {
    await abrir({ lecturas: LECTURAS, patrones: [PATRON] });

    expect(raiz().querySelectorAll('[data-garantia]')).toHaveLength(1);
    expect(raiz().querySelector('[data-pagina="2"] [data-garantia]')).not.toBeNull();
  });

  it('es un documento: no tiene un solo control que se pueda editar', async () => {
    await abrir({ lecturas: LECTURAS, patrones: [PATRON] });

    expect(raiz().querySelectorAll('input, textarea, select')).toHaveLength(0);
  });

  it('el QR lleva a este mismo documento en la aplicación', async () => {
    await abrir();

    expect(raiz().querySelector('.rs-qr svg')!.getAttribute('aria-label')).toBe(
      `Código QR: ${location.origin}/reportes/${ID_REPORTE}/documento`,
    );
  });
});

describe('El próximo servicio', () => {
  it('suma la periodicidad en meses de calendario', () => {
    expect(proximoServicio('2026-01-07', 'SEMESTRAL')).toBe('2026-07-07');
    expect(proximoServicio('2026-10-15', 'TRIMESTRAL')).toBe('2027-01-15');
    expect(proximoServicio('2026-12-31', 'MENSUAL')).toBe('2027-01-31');
  });

  it('se recorta al fin de mes: el 31 de agosto más seis meses es el último día de febrero', () => {
    expect(proximoServicio('2026-08-31', 'SEMESTRAL')).toBe('2027-02-28');
    expect(proximoServicio('2028-02-29', 'ANUAL')).toBe('2029-02-28');
    expect(proximoServicio('2027-08-31', 'SEMESTRAL')).toBe('2028-02-29');
  });

  it('sin fecha o sin periodicidad no hay próximo servicio', () => {
    expect(proximoServicio(undefined, 'ANUAL')).toBeUndefined();
    expect(proximoServicio('2026-01-07', undefined)).toBeUndefined();
  });
});
