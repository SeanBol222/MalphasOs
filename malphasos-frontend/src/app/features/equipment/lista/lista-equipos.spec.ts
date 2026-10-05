import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import { ID_MODELO, responderAlCatalogo } from '../../../../testing/catalogo';
import { ListaEquipos } from './lista-equipos';

const API = 'http://localhost:8081/v1/api';
const URL = `${API}/client-equipments`;

const EQUIPOS = [
  {
    id: 'ec1',
    serie: 'SN-0001',
    numeroInventario: 'INV-77',
    idModelo: ID_MODELO,
    idAreaServicio: 'a1',
    estadoActivo: true,
  },
  {
    id: 'ec2',
    serie: 'SN-0002',
    idModelo: ID_MODELO,
    idAreaServicio: 'a2',
    estadoActivo: false,
  },
  // Tercero en la misma area que el primero: comprueba que no se pide dos veces.
  {
    id: 'ec3',
    serie: 'SN-0003',
    idModelo: ID_MODELO,
    idAreaServicio: 'a1',
    estadoActivo: true,
  },
];

const AREAS = {
  a1: { id: 'a1', nombre: 'Urgencias', idSede: 's1', estadoActivo: true },
  a2: { id: 'a2', nombre: 'Quirófanos', idSede: 's1', estadoActivo: true },
};

describe('Listado de equipos de cliente', () => {
  let fixture: ComponentFixture<ListaEquipos>;
  let http: HttpTestingController;

  function montar(
    autoridades: readonly string[] = ['equipment.read', 'equipment.assign'],
  ): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(ListaEquipos);
    http = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => montar());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(equipos: object = EQUIPOS): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL, equipos);
    await responderAlCatalogo(fixture, http);
    // Las areas se piden de una en una, por identificador: el API no publica listado global.
    for (const [id, area] of Object.entries(AREAS)) {
      for (const peticion of http.match(`${API}/service-areas/${id}`)) {
        peticion.flush(area);
      }
    }
    await asentar(fixture);
  }

  it('pinta una fila por equipo, con la serie primero', async () => {
    await abrir();

    const filas = [...raiz().querySelectorAll('tbody tr')];

    expect(filas).toHaveLength(3);
    expect(filas[0].textContent).toContain('SN-0001');
  });

  it('el modelo se ensena en palabras, cruzando el catalogo', async () => {
    await abrir();

    expect(raiz().querySelector('tbody tr')!.textContent).toContain(
      'Tensiómetro · Welch Allyn · Medtronic',
    );
    expect(texto()).not.toContain(ID_MODELO);
  });

  it('el area se resuelve a su nombre y enlaza a su ficha', async () => {
    await abrir();

    expect(texto()).toContain('Urgencias');
    expect(texto()).toContain('Quirófanos');
    expect([...raiz().querySelectorAll('tbody a')].map((a) => a.getAttribute('href'))).toContain(
      '/areas/a1',
    );
  });

  it('cada equipo enlaza a su hoja de vida y a sus reportes, que son dos cosas distintas', async () => {
    // Nada probaba este enlace: cambiar «Ver» por dos enlaces dejo las 410 pruebas en verde. La hoja de
    // vida es el documento del equipo; los reportes, lo que se esta haciendo o se hizo, borradores
    // incluidos. Conviven, y una prueba fija que esten los dos.
    await abrir();

    const destinos = [...raiz().querySelectorAll('tbody a')].map((a) => a.getAttribute('href'));
    expect(destinos).toContain('/equipos/ec1/hoja-de-vida');
    expect(destinos).toContain('/equipos/ec1/historial');
  });

  it('pide cada area una sola vez, aunque dos equipos compartan area', async () => {
    // Son dos equipos en 'a1'. Pedirla dos veces seria trafico sin ninguna informacion nueva, y con
    // cien equipos en la misma area serian cien peticiones.
    fixture.detectChanges();
    await responderA(fixture, http, URL, EQUIPOS);
    await responderAlCatalogo(fixture, http);

    expect(http.match(`${API}/service-areas/a1`)).toHaveLength(1);

    http.match(`${API}/service-areas/a2`).forEach((p) => p.flush(AREAS.a2));
    await asentar(fixture);
  });

  it('un equipo sin numero de inventario no deja la celda en blanco', async () => {
    await abrir();

    expect([...raiz().querySelectorAll('tbody tr')][1].textContent).toContain('—');
  });

  it('el estado se distingue por peso tipografico y no por color', async () => {
    await abrir();

    const estados = [...raiz().querySelectorAll('tbody tr')].map(
      (fila) => fila.querySelectorAll('td')[4],
    );

    expect(estados[0].className).toContain('font-semibold');
    expect(estados[1].className).not.toContain('font-semibold');
    expect(estados.every((c) => !c.className.includes('text-accent'))).toBe(true);
  });

  it('sin equipos dice como se registra uno, en vez de una tabla vacia', async () => {
    await abrir([]);

    expect(raiz().querySelector('table')).toBeNull();
    expect(texto()).toContain('se registra en un área de servicio');
  });

  it('sin equipment.assign no se ofrece registrar', async () => {
    TestBed.resetTestingModule();
    montar(['equipment.read']);
    await abrir();

    expect([...raiz().querySelectorAll('a')].map((a) => a.textContent?.trim())).not.toContain(
      'Registrar equipo',
    );
  });

  afterEach(() => http.verify());
});
