import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
import { asentar, elegirEnBuscador, responderA } from '../../../../testing/pantalla';
import {
  AREAS,
  CLIENTES,
  EQUIPOS,
  ID_AREA,
  ID_EQUIPO,
  ID_INGENIERO,
  ID_ORDEN,
  ID_SEDE,
  orden,
  PERSONAS,
  SEDE,
  URL_CLIENTES,
  URL_EQUIPOS_DE_CLIENTE,
  URL_ORDENES,
  URL_PERSONAS,
  urlArea,
  urlSede,
} from '../../../../testing/ordenes';
import { DetalleOrden } from './detalle-orden';

const URL = `${URL_ORDENES}/${ID_ORDEN}`;

/** Todas las autoridades del módulo: un administrador. */
const TODAS = ['work-order.read', 'work-order.write', 'work-order.assign', 'person.read'];

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

  /** Abre la ficha y responde a las cinco consultas que necesita para poner nombres. */
  async function abrir(datos: object = orden()): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL, datos);
    await responderA(fixture, http, URL_CLIENTES, CLIENTES);
    await responderA(fixture, http, URL_EQUIPOS_DE_CLIENTE, EQUIPOS);
    await responderA(fixture, http, URL_PERSONAS, PERSONAS);
    http.match(urlSede(ID_SEDE)).forEach((peticion) => peticion.flush(SEDE));
    http.match(urlArea(ID_AREA)).forEach((peticion) => peticion.flush(AREAS[0]));
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

      http.expectOne({ method: 'PATCH', url: `${URL}/start` }).flush(
        orden({ estadoEjecucion: 'EN_EJECUCION' }),
      );
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

      http.expectOne({ method: 'PATCH', url: `${URL}/execute` }).flush(
        orden({ estadoEjecucion: 'EJECUTADA' }),
      );
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('si el backend dice que el estado no lo permite, se lee traducido', async () => {
      await abrir();
      pulsar('Iniciar trabajo');
      await asentar(fixture);
      http.expectOne({ method: 'PATCH', url: `${URL}/start` }).flush(
        { code: 'ERR_WORK_ORDER_003', message: 'Work order state does not allow it', details: [] },
        { status: 409, statusText: 'Conflict' },
      );
      await asentar(fixture);

      expect(raiz().querySelector('[role="alert"]')?.textContent).toContain(
        'El estado de la orden no permite',
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

      expect([...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim())).not.toContain(
        'Asignar un ingeniero',
      );
      // Y lo demas sigue disponible.
      expect([...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim())).toContain(
        'Iniciar trabajo',
      );
    });
  });

  describe('El alcance', () => {
    it('sin equipos dice cuál es el límite de lo que se puede añadir', async () => {
      await abrir();

      expect(texto()).toContain('áreas de su propia sede');
    });

    it('cada equipo se enseña por su serie y su área', async () => {
      await abrir(
        orden({ equipos: [{ idEquipoCliente: ID_EQUIPO, idAreaServicio: ID_AREA }] }),
      );

      expect(texto()).toContain('SN-0001');
      expect(texto()).toContain('Urgencias');
      expect(texto()).not.toContain(ID_EQUIPO);
    });

    it('quitar un equipo llama al sub-recurso con su identificador', async () => {
      await abrir(
        orden({ equipos: [{ idEquipoCliente: ID_EQUIPO, idAreaServicio: ID_AREA }] }),
      );
      pulsar('Quitar');
      await asentar(fixture);

      http.expectOne({ method: 'DELETE', url: `${URL}/equipments/${ID_EQUIPO}` }).flush(null);
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('una orden anulada no ofrece tocar su alcance', async () => {
      await abrir(
        orden({
          estadoActivo: false,
          equipos: [{ idEquipoCliente: ID_EQUIPO, idAreaServicio: ID_AREA }],
        }),
      );

      expect([...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim())).not.toContain(
        'Quitar',
      );
      expect([...raiz().querySelectorAll('a')].map((a) => a.textContent?.trim())).not.toContain(
        'Añadir equipos',
      );
    });
  });

  afterEach(() => {
    http.verify();
    desinstalarAlmacenamiento();
  });
});
