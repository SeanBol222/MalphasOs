import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
import { asentar, elegirEnBuscador, responderA } from '../../../../testing/pantalla';
import {
  CLIENTES,
  ID_AREA,
  ID_EQUIPO,
  ID_INGENIERO,
  ID_ORDEN,
  ID_SEDE,
  orden,
  PERSONAS,
  SEDE,
  URL_CLIENTES,
  URL_ORDENES,
  URL_PERSONAS,
  urlSede,
} from '../../../../testing/ordenes';
import { ID_REPORTE, reporte, urlReportesDeOrden } from '../../../../testing/reportes';
import { DetalleOrden } from './detalle-orden';

const URL = `${URL_ORDENES}/${ID_ORDEN}`;
const API = 'http://localhost:8081/v1/api';
const URL_RESUMEN = `${API}/client-equipments/overview`;
const URL_ENCARGADOS = `${API}/managers`;

/** Lo que el listado de equipos sabe de los dos equipos de prueba, pedidos por identificador. */
const RESUMEN = {
  total: 2,
  pagina: 0,
  tamano: 100,
  filas: [
    {
      id: ID_EQUIPO,
      numeroHojaVida: 'HV-HCE-0001',
      serie: 'SN-0001',
      tipoEquipo: 'Monitor',
      marca: 'Mindray',
      modelo: 'iMEC 10',
      estadoActivo: true,
      idAreaServicio: ID_AREA,
      areaServicio: 'Urgencias',
    },
    {
      id: 'ec2',
      numeroHojaVida: 'HV-HCE-0002',
      serie: 'SN-0002',
      tipoEquipo: 'Balanza',
      marca: 'Kern',
      modelo: 'PCB',
      estadoActivo: false,
      idAreaServicio: 'a3',
      areaServicio: 'Laboratorio',
    },
  ],
};

const ENCARGADOS = [
  // Desde V41 una sola persona puede estar a cargo de la sede y de un area: una fila, dos listas.
  { idPersona: 'p2', idsSede: [ID_SEDE], idsAreaServicio: ['a9'], estadoActivo: true },
];

/** Todas las autoridades que esta pantalla mira: un administrador. */
const TODAS = [
  'work-order.read',
  'work-order.write',
  'work-order.assign',
  'person.read',
  // Los reportes cuelgan de la orden y se abren desde aquí: report.write es del módulo de reportes,
  // no de este, y el realm se la da a ingenieros y administradores.
  'report.read',
  'report.write',
];

@Component({ selector: 'app-listado-falso', template: '' })
class ListadoFalso {}

describe('Ficha de una orden de trabajo', () => {
  let fixture: ComponentFixture<DetalleOrden>;
  let http: HttpTestingController;
  let desinstalarAlmacenamiento: () => void;

  function montar(autoridades: readonly string[] = TODAS): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([{ path: 'ordenes', component: ListadoFalso }]),
      ],
    });
    fixture = TestBed.createComponent(DetalleOrden);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_ORDEN);
  }

  beforeEach(() => {
    desinstalarAlmacenamiento = instalarAlmacenamiento();
    montar();
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  /**
   * Abre la ficha y responde a las consultas que necesita para poner nombres.
   *
   * <p>Desde el 2026-10-11 los equipos del alcance salen del listado de equipos pedido por sus
   * identificadores —una petición, con tipo, modelo, hoja de vida y área—, en lugar de la lista entera
   * más una petición por área.
   */
  async function abrir(datos: object = orden(), reportes: readonly object[] = []): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL, datos);
    await responderA(fixture, http, URL_CLIENTES, CLIENTES);
    await responderA(fixture, http, URL_PERSONAS, PERSONAS);
    http.match(urlReportesDeOrden(ID_ORDEN)).forEach((peticion) => peticion.flush(reportes));
    http.match(urlSede(ID_SEDE)).forEach((peticion) => peticion.flush(SEDE));
    http.match(URL_ENCARGADOS).forEach((peticion) => peticion.flush(ENCARGADOS));
    await asentar(fixture);
    http.match((peticion) => peticion.url === URL_RESUMEN).forEach((p) => p.flush(RESUMEN));
    await asentar(fixture);
  }

  function pulsar(etiqueta: string): void {
    const boton = [...raiz().querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes(etiqueta),
    );

    if (!boton) {
      throw new Error(`No hay ningun boton que diga "${etiqueta}"`);
    }

    boton.click();
    fixture.detectChanges();
  }

  it('enseña los datos con el cliente, la sede y el servicio en palabras', async () => {
    await abrir();

    expect(texto()).toContain('Hospital Central');
    expect(texto()).toContain('Sede Norte');
    expect(texto()).toContain('Preventivo · Trimestral');
    expect(texto()).toContain('Creada');
  });

  it('la cabecera dice el número de la orden, el que imprime el informe', async () => {
    await abrir();

    expect(raiz().querySelector('[data-numero-orden]')?.textContent?.trim()).toBe(
      'Orden N.º 4F2A9C',
    );
  });

  it('un correctivo en garantía lo dice junto al servicio', async () => {
    await abrir(orden({ tipoServicio: 'CORRECTIVO', garantia: true }));

    expect(texto()).toContain('Correctivo en garantía · Trimestral');
  });

  describe('El estado se avanza, no se edita', () => {
    it('una orden creada solo ofrece iniciarla', async () => {
      // Un desplegable de estados invitaria a saltarse el orden, y el backend responderia «el estado
      // no lo permite» a algo que la pantalla habia ofrecido.
      await abrir(orden({ estadoEjecucion: 'CREADA' }));

      const botones = [...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim());

      expect(botones).toContain('Iniciar trabajo');
      expect(botones).not.toContain('Marcar como ejecutada');
    });

    it('iniciar llama a su propia ruta', async () => {
      await abrir();
      pulsar('Iniciar trabajo');
      await asentar(fixture);

      http
        .expectOne({ method: 'PATCH', url: `${URL}/start` })
        .flush(orden({ estadoEjecucion: 'EN_EJECUCION' }));
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('una orden en ejecución solo ofrece cerrarla', async () => {
      await abrir(orden({ estadoEjecucion: 'EN_EJECUCION' }));

      const botones = [...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim());

      expect(botones).toContain('Marcar como ejecutada');
      expect(botones).not.toContain('Iniciar trabajo');
    });

    it('una orden ejecutada no ofrece ninguna de las dos', async () => {
      await abrir(orden({ estadoEjecucion: 'EJECUTADA' }));

      const botones = [...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim());

      expect(botones).not.toContain('Iniciar trabajo');
      expect(botones).not.toContain('Marcar como ejecutada');
    });

    it('ejecutar llama a su propia ruta', async () => {
      await abrir(orden({ estadoEjecucion: 'EN_EJECUCION' }));
      pulsar('Marcar como ejecutada');
      await asentar(fixture);

      http
        .expectOne({ method: 'PATCH', url: `${URL}/execute` })
        .flush(orden({ estadoEjecucion: 'EJECUTADA' }));
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    describe('ejecutar exige todos los reportes cerrados (2026-10-14)', () => {
      const DOS = [
        { idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE },
        { idEquipoCliente: 'ec2', idReporteServicio: 'r2' },
      ];
      const boton = () =>
        [...raiz().querySelectorAll('button')].find(
          (b) => b.textContent?.trim() === 'Marcar como ejecutada',
        ) as HTMLButtonElement;

      it('con un reporte en borrador el botón se desactiva y dice cuántos faltan', async () => {
        await abrir(orden({ estadoEjecucion: 'EN_EJECUCION', equipos: DOS }), [
          reporte({ idEquipoCliente: ID_EQUIPO, estado: 'FINALIZADO', resultado: 'OPERATIVO' }),
          reporte({ id: 'r2', idEquipoCliente: 'ec2', estado: 'BORRADOR' }),
        ]);

        expect(boton().disabled).toBe(true);
        expect(boton().getAttribute('aria-describedby')).toBe('por-que-no-se-ejecuta');
        expect(raiz().querySelector('#por-que-no-se-ejecuta')?.textContent).toContain(
          'falta cerrar el reporte de 1 equipo',
        );
      });

      it('con todos cerrados se puede ejecutar y no hay aviso', async () => {
        await abrir(orden({ estadoEjecucion: 'EN_EJECUCION', equipos: DOS }), [
          reporte({ idEquipoCliente: ID_EQUIPO, estado: 'FINALIZADO', resultado: 'OPERATIVO' }),
          reporte({
            id: 'r2',
            idEquipoCliente: 'ec2',
            estado: 'FINALIZADO',
            resultado: 'OPERATIVO',
          }),
        ]);

        expect(boton().disabled).toBe(false);
        expect(raiz().querySelector('#por-que-no-se-ejecuta')).toBeNull();
      });

      it('si el servidor responde que faltan reportes, se lee traducido', async () => {
        await abrir(orden({ estadoEjecucion: 'EN_EJECUCION' }));
        pulsar('Marcar como ejecutada');
        await asentar(fixture);

        http
          .expectOne({ method: 'PATCH', url: `${URL}/execute` })
          .flush(
            { code: 'ERR_WORK_ORDER_009', message: 'x', details: [] },
            { status: 409, statusText: 'Conflict' },
          );
        await asentar(fixture);

        expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
          'Cierre el reporte de cada equipo',
        );
      });
    });

    it('si el backend dice que el estado no lo permite, se lee traducido', async () => {
      await abrir();
      pulsar('Iniciar trabajo');
      await asentar(fixture);
      http.expectOne({ method: 'PATCH', url: `${URL}/start` }).flush(
        {
          code: 'ERR_WORK_ORDER_003',
          message: 'Work order state does not allow it',
          details: [],
        },
        { status: 409, statusText: 'Conflict' },
      );
      await asentar(fixture);

      expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
        'El estado de la orden no permite',
      );
    });

    it('si falta el ingeniero, lo dice y dice qué hacer, no que el estado no lo permite', async () => {
      // 2026-10-26: el código de estado le decía a una orden creada que una orden se inicia creada.
      await abrir();
      pulsar('Iniciar trabajo');
      await asentar(fixture);
      http.expectOne({ method: 'PATCH', url: `${URL}/start` }).flush(
        {
          code: 'ERR_WORK_ORDER_010',
          message: 'Work order has no engineer assigned',
          details: [],
        },
        { status: 409, statusText: 'Conflict' },
      );
      await asentar(fixture);

      const alerta = raiz().querySelector('[role="alert"]')?.textContent ?? '';
      expect(alerta).toContain('no tiene un ingeniero asignado');
      expect(alerta).not.toContain('El estado de la orden');
    });

    it('si faltan equipos, lo dice', async () => {
      await abrir();
      pulsar('Iniciar trabajo');
      await asentar(fixture);
      http
        .expectOne({ method: 'PATCH', url: `${URL}/start` })
        .flush(
          { code: 'ERR_WORK_ORDER_011', message: 'Work order has no equipment', details: [] },
          { status: 409, statusText: 'Conflict' },
        );
      await asentar(fixture);

      expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
        'La orden no tiene equipos',
      );
    });
  });

  describe('Anular no es un clic', () => {
    it('el primer clic pide confirmación y dice que no se borra nada', async () => {
      await abrir();
      pulsar('Anular');

      http.expectNone({ method: 'DELETE', url: URL });
      expect(texto()).toContain('No se borra nada');
    });

    it('confirmar anula y vuelve al listado', async () => {
      await abrir();
      pulsar('Anular');
      pulsar('Confirmar anulación');
      await asentar(fixture);

      http.expectOne({ method: 'DELETE', url: URL }).flush(null);
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);

      expect(TestBed.inject(Router).url).toBe('/ordenes');
    });
  });

  describe('El ingeniero', () => {
    it('sin asignar lo dice, porque la orden está programada y nadie la ejecuta', async () => {
      await abrir();

      expect(texto()).toContain('Sin asignar');
    });

    it('asignado se enseña por su nombre, no por su identificador', async () => {
      await abrir(orden({ idIngeniero: ID_INGENIERO }));

      expect(texto()).toContain('Ana Ruiz');
      expect(texto()).not.toContain(ID_INGENIERO);
    });

    it('solo ofrece ingenieros: un encargado de sede no ejecuta mantenimientos', async () => {
      await abrir();
      pulsar('Asignar un ingeniero');
      await asentar(fixture);

      const campo = raiz().querySelector<HTMLInputElement>('#idIngeniero')!;
      campo.dispatchEvent(new Event('focus'));
      campo.value = 'a';
      campo.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      await asentar(fixture);

      const opciones = [...raiz().querySelectorAll('#idIngeniero-lista [role="option"]')].map((o) =>
        o.textContent?.trim(),
      );

      expect(opciones).toContain('Ana Ruiz');
      expect(opciones).not.toContain('Luis Peña');
    });

    it('asignar usa la ruta del ingeniero, con su identificador en el camino', async () => {
      await abrir();
      pulsar('Asignar un ingeniero');
      await asentar(fixture);
      await elegirEnBuscador(fixture, 'idIngeniero', 'Ana Ruiz');
      pulsar('Asignar');
      await asentar(fixture);

      http
        .expectOne({ method: 'PATCH', url: `${URL}/engineer/${ID_INGENIERO}` })
        .flush(orden({ idIngeniero: ID_INGENIERO }));
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('sin work-order.assign no se ofrece asignar, aunque se pueda editar el resto', async () => {
      // La separacion es del backend: repartir trabajo no es lo mismo que alterarlo.
      TestBed.resetTestingModule();
      montar(['work-order.read', 'work-order.write', 'person.read']);
      await abrir();

      expect(
        [...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim()),
      ).not.toContain('Asignar un ingeniero');
      // Y lo demas sigue disponible.
      expect([...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim())).toContain(
        'Iniciar trabajo',
      );
    });
  });

  describe('La cabecera', () => {
    it('dice cliente y sede en el título, y la fecha programada en palabras', async () => {
      await abrir();

      expect(raiz().querySelector('h1')?.textContent?.trim()).toBe('Hospital Central — Sede Norte');
      expect(texto()).toContain('Programada para el 15 oct 2026');
    });

    it('la línea de estado marca el paso actual', async () => {
      await abrir(orden({ estadoEjecucion: 'EN_EJECUCION' }));

      const actual = raiz().querySelector('[aria-current="step"]');
      expect(actual?.textContent?.trim()).toBe('2 · En ejecución');
      expect(raiz().querySelectorAll('ol[aria-label="Estado de la orden"] li').length).toBe(3);
    });

    it('una orden anulada lo dice en lugar de la línea de estado', async () => {
      await abrir(orden({ estadoActivo: false }));

      expect(raiz().querySelector('[aria-current="step"]')).toBeNull();
      expect(texto()).toContain('Anulada. Su historia queda intacta');
    });
  });

  describe('Los encargados del sitio', () => {
    it('con engineer.read enseña los de la sede y los de las áreas de la orden, no los demás', async () => {
      TestBed.resetTestingModule();
      montar([...TODAS, 'engineer.read']);
      await abrir(
        orden({ equipos: [{ idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE }] }),
      );

      const seccion = raiz().querySelector('section[aria-labelledby="encargados"]')!;
      expect(seccion.textContent).toContain('Luis Peña');
      expect(seccion.textContent).toContain('Toda la sede');
      // El segundo es de un área que la orden no toca.
      expect(seccion.querySelectorAll('p').length).toBe(1);
    });

    it('sin engineer.read no se ve la sección ni se piden los encargados', async () => {
      // Sin abrir(): ese ayudante responde a los encargados si se piden, y un expectNone despues no
      // veria nada. La primera version de esta prueba paso con la guarda quitada por eso.
      fixture.detectChanges();
      await responderA(fixture, http, URL, orden());
      await asentar(fixture);

      expect(http.match(URL_ENCARGADOS)).toHaveLength(0);
      expect(raiz().querySelector('section[aria-labelledby="encargados"]')).toBeNull();
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });
  });

  describe('El alcance', () => {
    it('sin equipos dice cuál es el límite de lo que se puede añadir', async () => {
      await abrir();

      expect(texto()).toContain('áreas de su propia sede');
    });

    it('cada equipo se enseña por lo que es, su hoja de vida y su serie, bajo su área', async () => {
      await abrir(
        orden({ equipos: [{ idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE }] }),
      );

      const fila = raiz().querySelector(`[data-equipo="${ID_EQUIPO}"]`)!;
      expect(fila.textContent).toContain('Monitor Mindray iMEC 10');
      expect(fila.textContent).toContain('HV-HCE-0001 · Serie SN-0001');
      expect(fila.closest('section')?.querySelector('h3')?.textContent?.trim()).toBe('Urgencias');
      expect(texto()).not.toContain(ID_EQUIPO);
    });

    it('pide sus equipos al listado por identificador, en una sola petición', async () => {
      fixture.detectChanges();
      await responderA(
        fixture,
        http,
        URL,
        orden({
          equipos: [
            { idEquipoCliente: 'ec2', idReporteServicio: 'r2' },
            { idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE },
          ],
        }),
      );
      await asentar(fixture);

      const peticiones = http.match((peticion) => peticion.url === URL_RESUMEN);
      expect(peticiones.length).toBe(1);
      expect(peticiones[0].request.params.get('ids')).toBe(`${ID_EQUIPO},ec2`);
      peticiones[0].flush(RESUMEN);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('agrupa por área, dice cuántos tienen el reporte cerrado y marca el dado de baja', async () => {
      await abrir(
        orden({
          estadoEjecucion: 'EN_EJECUCION',
          equipos: [
            { idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE },
            { idEquipoCliente: 'ec2', idReporteServicio: 'r2' },
          ],
        }),
        [reporte({ idEquipoCliente: ID_EQUIPO, estado: 'FINALIZADO', resultado: 'OPERATIVO' })],
      );

      const areas = [...raiz().querySelectorAll('h3')].map((h) => h.textContent?.trim());
      expect(areas).toEqual(['Laboratorio', 'Urgencias']);
      expect(texto()).toContain('1 de 2 con reporte cerrado');
      expect(raiz().querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe(
        '50',
      );
      expect(raiz().querySelector('[data-equipo="ec2"]')?.textContent).toContain('Dado de baja');
    });

    it('quitar un equipo llama al sub-recurso con su identificador', async () => {
      await abrir(
        orden({ equipos: [{ idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE }] }),
      );
      pulsar('Quitar');
      await asentar(fixture);

      http.expectOne({ method: 'DELETE', url: `${URL}/equipments/${ID_EQUIPO}` }).flush(null);
      await asentar(fixture);
      // Sacar el equipo retira su borrador (V50): los reportes de la orden también se vuelven a pedir.
      expect(http.match(urlReportesDeOrden(ID_ORDEN)).length).toBeGreaterThan(0);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('cada equipo enseña su reporte con el estado en que va', async () => {
      await abrir(
        orden({
          estadoEjecucion: 'EN_EJECUCION',
          equipos: [{ idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE }],
        }),
        [reporte({ idEquipoCliente: ID_EQUIPO, estado: 'FINALIZADO' })],
      );

      const fila = raiz().querySelector(`[data-equipo="${ID_EQUIPO}"]`)!;
      const enlace = fila.querySelector('a');
      expect(fila.textContent).toContain('Cerrado');
      expect(enlace?.textContent?.trim()).toBe('Ver reporte');
      expect(enlace?.getAttribute('href')).toBe(`/reportes/${ID_REPORTE}`);
    });

    it('un borrador pesa más que lo cerrado y se continúa, no se abre otro', async () => {
      await abrir(
        orden({
          estadoEjecucion: 'EN_EJECUCION',
          equipos: [{ idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE }],
        }),
        [reporte({ idEquipoCliente: ID_EQUIPO, estado: 'BORRADOR' })],
      );

      const fila = raiz().querySelector(`[data-equipo="${ID_EQUIPO}"]`)!;
      expect(fila.querySelector('a')?.textContent?.trim()).toBe('Continuar');
      expect(
        [...fila.querySelectorAll('span')].find((s) => s.textContent?.trim() === 'Borrador')
          ?.classList,
      ).toContain('font-extrabold');
    });

    it('ningún reporte se abre desde aquí: el borrador nace al añadir el equipo (V50)', async () => {
      await abrir(
        orden({
          estadoEjecucion: 'EN_EJECUCION',
          equipos: [{ idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE }],
        }),
      );

      expect(
        [...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim()),
      ).not.toContain('Abrir reporte');
      // La orden dice cuál es su reporte aunque la consulta de reportes no lo haya traído todavía.
      expect(raiz().querySelector(`[data-equipo="${ID_EQUIPO}"] a`)?.getAttribute('href')).toBe(
        `/reportes/${ID_REPORTE}`,
      );
    });

    it('con la orden todavía creada el borrador se ve, y dice por qué no se llena', async () => {
      // Es la regla del servidor: un reporte cuenta lo que se hizo, y en una orden sin empezar no se ha
      // hecho nada. El borrador ya existe desde que se añadió el equipo; lo que espera es poder escribirse.
      await abrir(
        orden({ equipos: [{ idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE }] }),
        [reporte({ idEquipoCliente: ID_EQUIPO, estado: 'BORRADOR' })],
      );

      expect(raiz().querySelector(`[data-equipo="${ID_EQUIPO}"] a`)?.textContent?.trim()).toBe(
        'Ver borrador',
      );
      expect(texto()).toContain('Los reportes se llenan cuando la orden empieza');
    });

    it('un equipo con el reporte cerrado no se quita de la orden: su reporte se sustituye', async () => {
      await abrir(
        orden({
          estadoEjecucion: 'EN_EJECUCION',
          equipos: [{ idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE }],
        }),
        [reporte({ idEquipoCliente: ID_EQUIPO, estado: 'FINALIZADO', resultado: 'OPERATIVO' })],
      );

      expect(
        [...raiz().querySelectorAll(`[data-equipo="${ID_EQUIPO}"] button`)].map((b) =>
          b.textContent?.trim(),
        ),
      ).not.toContain('Quitar');
    });

    it('una orden anulada no ofrece tocar su alcance', async () => {
      await abrir(
        orden({
          estadoActivo: false,
          equipos: [{ idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE }],
        }),
      );

      expect(
        [...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim()),
      ).not.toContain('Quitar');
      expect([...raiz().querySelectorAll('a')].map((a) => a.textContent?.trim())).not.toContain(
        'Añadir equipos',
      );
    });
  });

  describe('Los informes de la orden en un ZIP (2026-10-31)', () => {
    const DOS = [
      { idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE },
      { idEquipoCliente: 'ec2', idReporteServicio: 'r2' },
    ];
    const URL_ZIP = `${API}/reports/archive?idOrdenTrabajo=${ID_ORDEN}`;
    const boton = () => raiz().querySelector<HTMLButtonElement>('[data-descargar-informes]');
    const cerrado = (id: string, idEquipoCliente: string) =>
      reporte({ id, idEquipoCliente, estado: 'FINALIZADO', resultado: 'OPERATIVO' });

    const URL_RESUMEN_PDF = `${API}/reports/summary?idOrdenTrabajo=${ID_ORDEN}`;
    const botonDelResumen = () =>
      raiz().querySelector<HTMLButtonElement>('[data-descargar-resumen]');

    it('con algún reporte en borrador no se ofrece', async () => {
      await abrir(orden({ estadoEjecucion: 'EN_EJECUCION', equipos: DOS }), [
        cerrado(ID_REPORTE, ID_EQUIPO),
        reporte({ id: 'r2', idEquipoCliente: 'ec2', estado: 'BORRADOR' }),
      ]);

      expect(boton()).toBeNull();
      expect(botonDelResumen()).toBeNull();
    });

    it('una orden sin equipos no tiene informes que descargar', async () => {
      await abrir(orden({ estadoEjecucion: 'EN_EJECUCION', equipos: [] }));

      expect(boton()).toBeNull();
      expect(botonDelResumen()).toBeNull();
    });

    it('con todos cerrados se ofrece aunque no se pueda escribir, y guarda el ZIP con el nombre del servidor', async () => {
      TestBed.resetTestingModule();
      montar(['work-order.read', 'report.read']);
      const { createObjectURL, revokeObjectURL } = globalThis.URL; // URL es aquí la de la orden
      const guardados: { href: string; download: string }[] = [];
      const click = HTMLAnchorElement.prototype.click;
      globalThis.URL.createObjectURL = () => 'blob:prueba/1';
      globalThis.URL.revokeObjectURL = () => undefined;
      HTMLAnchorElement.prototype.click = function (this: HTMLAnchorElement) {
        guardados.push({ href: this.href, download: this.download });
      };
      try {
        await abrir(orden({ estadoEjecucion: 'EN_EJECUCION', equipos: DOS }), [
          cerrado(ID_REPORTE, ID_EQUIPO),
          cerrado('r2', 'ec2'),
        ]);

        boton()!.click();
        await asentar(fixture);
        expect(boton()!.textContent).toContain('Preparando los informes');
        http.expectOne(URL_ZIP).flush(new Blob(['PK'], { type: 'application/zip' }), {
          headers: { 'Content-Disposition': 'attachment; filename="informes-orden-ABC123.zip"' },
        });
        await asentar(fixture);

        expect(guardados).toEqual([
          { href: 'blob:prueba/1', download: 'informes-orden-ABC123.zip' },
        ]);
      } finally {
        globalThis.URL.createObjectURL = createObjectURL;
        globalThis.URL.revokeObjectURL = revokeObjectURL;
        HTMLAnchorElement.prototype.click = click;
      }
    });

    it('el resumen se descarga aparte, con el nombre que da el servidor', async () => {
      const { createObjectURL, revokeObjectURL } = globalThis.URL; // URL es aquí la de la orden
      const guardados: string[] = [];
      const click = HTMLAnchorElement.prototype.click;
      globalThis.URL.createObjectURL = () => 'blob:prueba/2';
      globalThis.URL.revokeObjectURL = () => undefined;
      HTMLAnchorElement.prototype.click = function (this: HTMLAnchorElement) {
        guardados.push(this.download);
      };
      try {
        await abrir(orden({ estadoEjecucion: 'EJECUTADA', equipos: DOS }), [
          cerrado(ID_REPORTE, ID_EQUIPO),
          cerrado('r2', 'ec2'),
        ]);

        botonDelResumen()!.click();
        await asentar(fixture);
        expect(botonDelResumen()!.textContent).toContain('Preparando el resumen');
        http.expectOne(URL_RESUMEN_PDF).flush(new Blob(['%PDF'], { type: 'application/pdf' }), {
          headers: { 'Content-Disposition': 'attachment; filename="orden-ABC123.pdf"' },
        });
        await asentar(fixture);

        expect(guardados).toEqual(['orden-ABC123.pdf']);
        // Es su propia petición: el ZIP no se pidió.
        http.expectNone(URL_ZIP);
      } finally {
        globalThis.URL.createObjectURL = createObjectURL;
        globalThis.URL.revokeObjectURL = revokeObjectURL;
        HTMLAnchorElement.prototype.click = click;
      }
    });

    it('si el resumen falla, lo dice con el mensaje del catálogo', async () => {
      await abrir(orden({ estadoEjecucion: 'EJECUTADA', equipos: DOS }), [
        cerrado(ID_REPORTE, ID_EQUIPO),
        cerrado('r2', 'ec2'),
      ]);

      botonDelResumen()!.click();
      await asentar(fixture);
      http.expectOne(URL_RESUMEN_PDF).flush(
        new Blob([JSON.stringify({ code: 'ERR_SERVICE_REPORT_010', message: 'x', details: [] })], {
          type: 'application/json',
        }),
        { status: 503, statusText: 'Service Unavailable' },
      );
      await asentar(fixture);

      expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
        'No se pudo generar el PDF del informe',
      );
    });

    it('si el conversor no responde, lo dice con el mensaje del catálogo', async () => {
      await abrir(orden({ estadoEjecucion: 'EJECUTADA', equipos: DOS }), [
        cerrado(ID_REPORTE, ID_EQUIPO),
        cerrado('r2', 'ec2'),
      ]);

      boton()!.click();
      await asentar(fixture);
      http.expectOne(URL_ZIP).flush(
        new Blob([JSON.stringify({ code: 'ERR_SERVICE_REPORT_010', message: 'x', details: [] })], {
          type: 'application/json',
        }),
        { status: 503, statusText: 'Service Unavailable' },
      );
      await asentar(fixture);

      expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
        'No se pudo generar el PDF del informe',
      );
    });
  });

  afterEach(() => {
    // En finally a proposito: si verify() lanza por una peticion abierta, sin esto el doble de
    // localStorage se queda instalado y el fichero SIGUIENTE hereda lo que este guardo. Es la variante
    // con contaminacion del fallo que este proyecto ya pago -- un fallo en afterEach se lee como
    // ochenta y seis -- y asi el rojo se queda donde ocurrio.
    try {
      http.verify();
    } finally {
      desinstalarAlmacenamiento();
    }
  });

  describe('Una orden ejecutada se lee de su documento (V49)', () => {
    /** Con nombres distintos de los vivos: si la ficha leyera en vivo, la prueba lo vería. */
    function documento(cambios: object = {}) {
      return {
        version: 1,
        numero: '4F2A9C',
        tipoServicio: 'PREVENTIVO',
        garantia: false,
        periodicidad: 'TRIMESTRAL',
        fechaMantenimiento: '2026-10-15',
        fechaEjecucion: '2026-10-20',
        cliente: { nombre: 'Clínica del día', documento: '900' },
        sede: { nombre: 'Sede del día', direccion: 'Calle 1 # 2 - 3', ciudad: 'Bogotá' },
        ingeniero: 'Ingeniera del día',
        equipos: [
          {
            id: ID_EQUIPO,
            area: 'Área de entonces',
            tipo: 'Monitor',
            marca: 'Mindray',
            modelo: 'iMEC 10',
            serie: 'SN-0001',
            numeroHojaVida: 'HV-HCE-0001',
            numeroReporte: 'AAA111',
          },
        ],
        ...cambios,
      };
    }

    function ejecutada(cambios: object = {}, tardio = false) {
      return {
        ...orden({
          estadoEjecucion: 'EJECUTADA',
          idIngeniero: ID_INGENIERO,
          equipos: [{ idEquipoCliente: ID_EQUIPO, idReporteServicio: ID_REPORTE }],
        }),
        documento: documento(cambios),
        documentoTardio: tardio,
      };
    }

    it('cliente, sede, ingeniero y el área de cada equipo son los del día en que se ejecutó', async () => {
      await abrir(ejecutada(), [
        reporte({ idEquipoCliente: ID_EQUIPO, estado: 'FINALIZADO', numero: 'AAA111' }),
      ]);

      expect(texto()).toContain('Clínica del día — Sede del día');
      expect(texto()).toContain('Ingeniera del día');
      const grupo = raiz().querySelector('[aria-label="Área de entonces"]');
      expect(grupo?.textContent).toContain('Monitor Mindray iMEC 10');
      // El área viva del listado de equipos es «Urgencias»: no se usa.
      expect(raiz().querySelector('[aria-label="Urgencias"]')).toBeNull();
      expect(raiz().querySelector('[data-congelada]')?.textContent).toContain(
        'Congelada al ejecutarse',
      );
      expect(texto()).not.toContain('se sustituyó');
    });

    it('si su reporte se sustituyó después, lo dice y enlaza al vigente', async () => {
      await abrir(ejecutada(), [
        reporte({ idEquipoCliente: ID_EQUIPO, estado: 'BORRADOR', numero: 'BBB222' }),
      ]);

      const fila = raiz().querySelector(`[data-equipo="${ID_EQUIPO}"]`)!;
      expect(fila.textContent).toContain('Su reporte se sustituyó después de ejecutarla');
      expect(fila.querySelector('a')?.getAttribute('href')).toBe(`/reportes/${ID_REPORTE}`);
    });

    it('una congelada después lo dice, para que nadie la lea como la del día', async () => {
      await abrir(ejecutada({ fechaEjecucion: null }, true), [
        reporte({ idEquipoCliente: ID_EQUIPO, estado: 'FINALIZADO', numero: 'AAA111' }),
      ]);

      expect(raiz().querySelector('[data-congelada]')?.textContent).toContain(
        'Congelada después de ejecutarse',
      );
    });

    it('una orden sin ejecutar no dice que esté congelada', async () => {
      await abrir(orden({ estadoEjecucion: 'EN_EJECUCION' }));

      expect(raiz().querySelector('[data-congelada]')).toBeNull();
    });
  });
});
