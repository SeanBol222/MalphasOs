import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import { ID_MODELO, responderAlCatalogo } from '../../../../testing/catalogo';
import { DetalleArea } from './detalle-area';

const ID_AREA = 'a1';
const URL_AREA = `http://localhost:8081/v1/api/service-areas/${ID_AREA}`;
const URL_EQUIPOS = `${URL_AREA}/equipments`;
const URL_EQUIPO = 'http://localhost:8081/v1/api/client-equipments';

const AREA = { id: ID_AREA, nombre: 'Urgencias', idSede: 's1', estadoActivo: true };

const INSTALADOS = [
  {
    id: 'ec1',
    serie: 'SN-0001',
    numeroInventario: 'INV-77',
    idModelo: ID_MODELO,
    idAreaServicio: ID_AREA,
    estadoActivo: true,
  },
  {
    id: 'ec2',
    serie: 'SN-0002',
    idModelo: ID_MODELO,
    idAreaServicio: ID_AREA,
    estadoActivo: false,
  },
];

@Component({ selector: 'app-ficha-sede-falsa', template: '' })
class FichaSedeFalsa {}

describe('Equipos de un area', () => {
  let fixture: ComponentFixture<DetalleArea>;
  let http: HttpTestingController;

  function montar(
    autoridades: readonly string[] = ['equipment.read', 'equipment.write', 'equipment.assign'],
  ): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([{ path: 'sedes/:id', component: FichaSedeFalsa }]),
      ],
    });
    fixture = TestBed.createComponent(DetalleArea);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', ID_AREA);
  }

  beforeEach(() => montar());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(instalados: object = INSTALADOS): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_AREA, AREA);
    await responderA(fixture, http, URL_EQUIPOS, instalados);
    await responderAlCatalogo(fixture, http);
  }

  function pulsar(etiqueta: string): void {
    const boton = [...raiz().querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === etiqueta,
    );

    if (!boton) {
      throw new Error(`No hay ningun boton que diga "${etiqueta}"`);
    }

    boton.click();
    fixture.detectChanges();
  }

  it('ensena la serie primero y el modelo en palabras', async () => {
    // La serie identifica la maquina; el modelo dice que maquina es, y viene como identificador.
    await abrir();

    const primera = raiz().querySelector('tbody tr')!.textContent ?? '';

    expect(primera).toContain('SN-0001');
    expect(primera).toContain('Tensiómetro · Welch Allyn');
    expect(texto()).not.toContain(ID_MODELO);
  });

  it('un equipo sin numero de inventario no deja la celda en blanco', async () => {
    await abrir();

    const segunda = raiz().querySelectorAll('tbody tr')[1].textContent ?? '';

    expect(segunda).toContain('—');
  });

  it('el estado se distingue por peso tipografico y no por color', async () => {
    await abrir();

    const estados = [...raiz().querySelectorAll('tbody tr')].map(
      (fila) => fila.querySelectorAll('td')[3],
    );

    expect(estados[0].textContent).toContain('En servicio');
    expect(estados[0].className).toContain('font-semibold');
    expect(estados[1].className).not.toContain('font-semibold');
    expect(estados.every((c) => !c.className.includes('text-accent'))).toBe(true);
  });

  it('sin equipos dice como se registra uno', async () => {
    await abrir([]);

    expect(raiz().querySelector('table')).toBeNull();
    expect(texto()).toContain('sobre un modelo del catálogo, con la');
  });

  describe('Dar de baja no es un clic', () => {
    it('el primer clic pide confirmacion y dice que no se borra nada', async () => {
      await abrir();
      pulsar('Dar de baja');

      http.expectNone({ method: 'DELETE', url: `${URL_EQUIPO}/ec1` });
      expect(texto()).toContain('No se borra');
    });

    it('confirmar llama a la ruta del equipo, no a la del area', async () => {
      await abrir();
      pulsar('Dar de baja');
      pulsar('Confirmar baja');
      await asentar(fixture);

      http.expectOne({ method: 'DELETE', url: `${URL_EQUIPO}/ec1` }).flush(null);
      await asentar(fixture);
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);
    });

    it('un equipo ya de baja no ofrece darse de baja otra vez', async () => {
      await abrir();

      const botones = [...raiz().querySelectorAll('button')].filter(
        (b) => b.textContent?.trim() === 'Dar de baja',
      );

      expect(botones).toHaveLength(1);
    });
  });

  describe('La serie provisional', () => {
    // Un equipo registrado sin serie recibe del servidor una provisional (2026-11-03). Quien lee la
    // placa la cambia aqui; una serie real no se cambia, y eso lo decide el servidor con
    // serieProvisional, no la pantalla mirando el prefijo.
    const CON_PROVISIONAL = [
      {
        id: 'ec3',
        serie: 'P:3f9a1c',
        serieProvisional: true,
        idModelo: ID_MODELO,
        idAreaServicio: ID_AREA,
        estadoActivo: true,
      },
      { ...INSTALADOS[0], serieProvisional: false },
    ];

    const filas = () => [...raiz().querySelectorAll('tbody tr')];

    it('se marca como provisional, y la real no', async () => {
      await abrir(CON_PROVISIONAL);

      expect(filas()[0].querySelector('[data-provisional]')?.textContent).toContain(
        'Serie provisional',
      );
      expect(filas()[1].querySelector('[data-provisional]')).toBeNull();
    });

    it('solo la provisional ofrece poner la serie real', async () => {
      await abrir(CON_PROVISIONAL);

      expect(filas()[0].textContent).toContain('Poner la serie real');
      expect(filas()[1].textContent).not.toContain('Poner la serie real');
    });

    it('guardar manda solo la serie, recortada, a la ruta del equipo', async () => {
      await abrir(CON_PROVISIONAL);
      pulsar('Poner la serie real');
      const campo = raiz().querySelector<HTMLInputElement>('#serie-real-ec3')!;
      campo.value = '  SN-PLACA ';
      campo.dispatchEvent(new Event('input'));
      pulsar('Guardar serie');
      await asentar(fixture);

      const cambio = http.expectOne({ method: 'PATCH', url: `${URL_EQUIPO}/ec3` });
      expect(cambio.request.body).toEqual({ serie: 'SN-PLACA' });
      const corregido = { ...CON_PROVISIONAL[0], serie: 'SN-PLACA', serieProvisional: false };
      cambio.flush(corregido);
      await asentar(fixture);

      // El campo se cierra cuando termina la recarga, que EquipoApi espera en su onSuccess. La recarga
      // devuelve la fila corregida: con una lista vacia la fila desapareceria y el campo con ella, y la
      // prueba pasaria aunque el campo no se cerrara nunca.
      http
        .match((p) => p.url === URL_EQUIPOS)
        .forEach((p) => p.flush([corregido, CON_PROVISIONAL[1]]));
      http.match(() => true).forEach((p) => p.flush([]));
      await asentar(fixture);

      expect(raiz().querySelector('#serie-real-ec3')).toBeNull();
      expect(filas()[0].textContent).toContain('SN-PLACA');
      expect(filas()[0].querySelector('[data-provisional]')).toBeNull();
    });

    it('vacia no se manda, y dice que falta', async () => {
      await abrir(CON_PROVISIONAL);
      pulsar('Poner la serie real');
      const campo = raiz().querySelector<HTMLInputElement>('#serie-real-ec3')!;
      campo.value = '   ';
      campo.dispatchEvent(new Event('input'));
      pulsar('Guardar serie');
      await asentar(fixture);

      http.expectNone({ method: 'PATCH', url: `${URL_EQUIPO}/ec3` });
      expect(texto()).toContain('no puede quedar vacía');
    });

    it('un rechazo del servidor se ensena traducido', async () => {
      await abrir(CON_PROVISIONAL);
      pulsar('Poner la serie real');
      const campo = raiz().querySelector<HTMLInputElement>('#serie-real-ec3')!;
      campo.value = 'SN-PLACA';
      campo.dispatchEvent(new Event('input'));
      pulsar('Guardar serie');
      await asentar(fixture);
      http
        .expectOne({ method: 'PATCH', url: `${URL_EQUIPO}/ec3` })
        .flush(
          { code: 'ERR_EQUIPMENT_007', message: 'La serie de una unidad no cambia', details: [] },
          { status: 400, statusText: 'Bad Request' },
        );
      await asentar(fixture);

      expect(raiz().querySelector('[role="alert"]')).not.toBeNull();
      expect(raiz().querySelector('#serie-real-ec3')).not.toBeNull();
    });

    it('sin equipment.write no se ofrece', async () => {
      TestBed.resetTestingModule();
      montar(['equipment.read']);
      await abrir(CON_PROVISIONAL);

      expect(texto()).toContain('Serie provisional');
      expect(texto()).not.toContain('Poner la serie real');
    });
  });

  it('sin equipment.assign no se ofrece registrar, y sin write no se da de baja', async () => {
    // Es el caso del grupo clients: equipment.read y nada mas. El frontend oculta; el permiso lo
    // sigue comprobando el servidor en cada llamada.
    TestBed.resetTestingModule();
    montar(['equipment.read']);
    await abrir();

    expect([...raiz().querySelectorAll('a')].map((a) => a.textContent?.trim())).not.toContain(
      'Registrar equipo',
    );
    expect([...raiz().querySelectorAll('button')]).toHaveLength(0);
  });

  it('vuelve a la sede del area, que viene en la respuesta', async () => {
    await abrir();

    expect([...raiz().querySelectorAll('a')].map((a) => a.getAttribute('href'))).toContain(
      '/sedes/s1',
    );
  });

  afterEach(() => http.verify());
});
