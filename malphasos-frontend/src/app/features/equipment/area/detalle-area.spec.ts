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
    expect(texto()).toContain('con su serie, sobre un modelo del catálogo');
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
