import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, elegirEnBuscador, escribirEnBuscador } from '../../../../testing/pantalla';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
import {
  EQUIPOS,
  ID_MARCA,
  ID_TIPO,
  MARCAS,
  responderAlCatalogo,
  TIPOS,
  URL_EQUIPOS,
  URL_MARCAS,
  URL_TIPOS,
  buscarConLaLupa,
} from '../../../../testing/catalogo';
import { EquiposDeCatalogo } from './equipos';

describe('Equipos del catalogo', () => {
  let fixture: ComponentFixture<EquiposDeCatalogo>;
  let http: HttpTestingController;

  let desinstalarAlmacenamiento: () => void;

  beforeEach(() => {
    // Los campos que predicen guardan lo ya elegido en localStorage.
    desinstalarAlmacenamiento = instalarAlmacenamiento();
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['equipment.read', 'equipment.write'] }),
      ],
    });
    fixture = TestBed.createComponent(EquiposDeCatalogo);
    http = TestBed.inject(HttpTestingController);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(datos: Parameters<typeof responderAlCatalogo>[2] = {}): Promise<void> {
    await responderAlCatalogo(fixture, http, datos);
  }

  function pulsar(etiqueta: string): void {
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === etiqueta)!.click();
    fixture.detectChanges();
  }

  it('dice en una linea que es: que marcas hacen cada tipo', async () => {
    // La descripcion se acorto el 2026-11-20, pedido por el usuario: la cabecera lleva una linea.
    await abrir();

    expect(texto()).toContain('Qué marcas hacen cada tipo.');
  });

  it('cada fila se compone cruzando tipos y marcas, porque el API devuelve identificadores', async () => {
    await abrir();

    expect(texto()).toContain('Tensiómetro');
    expect(texto()).toContain('Welch Allyn');
    // Ningun identificador en pantalla.
    expect(texto()).not.toContain(ID_TIPO);
    expect(texto()).not.toContain(ID_MARCA);
  });

  it('si una pieza no esta en la lista, lo dice en vez de ensenar un UUID', async () => {
    // Puede pasar de verdad: el catalogo se pagina o alguien retira un tipo mientras se mira.
    await abrir({ tipos: [] });

    expect(texto()).toContain('Tipo no disponible');
  });

  it('el formulario no esta a la vista: se abre con «Nuevo equipo» y se cierra con «Cancelar»', async () => {
    await abrir();
    expect(raiz().querySelector('form')).toBeNull();

    pulsar('Nuevo equipo');
    expect(raiz().querySelector('form')).not.toBeNull();
    // Abierto, el boton sobra: ya se esta creando uno.
    expect(texto()).not.toContain('Nuevo equipo');

    pulsar('Cancelar');
    expect(raiz().querySelector('form')).toBeNull();
  });

  it('tipo y marca ofrecen su lista entera al hacer clic, solo con lo activo', async () => {
    // Desde el 2026-11-20 son campos que predicen y se recorren al abrirlos, como en el alta de un
    // modelo. El backend rechaza una referencia retirada, así que la retirada no se ofrece.
    await abrir({ marcas: [...MARCAS, { id: 'm3', nombre: 'Omron', estadoActivo: true }] });
    pulsar('Nuevo equipo');
    const ofrecidas = (campo: string) =>
      [...raiz().querySelectorAll(`#${campo}-lista [role="option"]`)].map((o) =>
        o.textContent?.trim(),
      );

    await escribirEnBuscador(fixture, 'tipoDelEquipo', '');
    expect(ofrecidas('tipoDelEquipo')).toEqual(['Tensiómetro']);

    await escribirEnBuscador(fixture, 'marcaDelEquipo', '');
    expect(ofrecidas('marcaDelEquipo')).toEqual(['Omron', 'Welch Allyn']);

    await escribirEnBuscador(fixture, 'marcaDelEquipo', 'we');
    expect(ofrecidas('marcaDelEquipo')).toEqual(['Welch Allyn']);
  });

  it('combinar manda los dos identificadores y nada mas, y cierra el formulario', async () => {
    await abrir();
    pulsar('Nuevo equipo');
    await elegirEnBuscador(fixture, 'tipoDelEquipo', 'Tensiómetro');
    await elegirEnBuscador(fixture, 'marcaDelEquipo', 'Welch Allyn');
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);

    const alta = http.expectOne({ method: 'POST', url: URL_EQUIPOS });

    expect(alta.request.body).toEqual({ idTipoEquipo: ID_TIPO, idMarca: ID_MARCA });
    alta.flush({ id: 'e2' });
    await asentar(fixture);
    // La escritura invalida el catalogo entero: crear un equipo cambia lo que puede elegirse al
    // crear un modelo.
    for (const url of [URL_MARCAS, URL_TIPOS, URL_EQUIPOS]) {
      http.match(url).forEach((p) => p.flush(url === URL_MARCAS ? MARCAS : url === URL_TIPOS ? TIPOS : EQUIPOS));
    }
    await asentar(fixture);
    expect(raiz().querySelector('form')).toBeNull();
  });

  it('sin permiso de escritura no hay boton para crear', async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [...proveerApiSimulado(), ...proveerSesionFalsa({ autoridades: ['equipment.read'] })],
    });
    fixture = TestBed.createComponent(EquiposDeCatalogo);
    http = TestBed.inject(HttpTestingController);
    await abrir();

    expect(texto()).not.toContain('Nuevo equipo');
  });

  it('sin elegir las dos piezas no llega al servidor', async () => {
    await abrir();
    pulsar('Nuevo equipo');
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);

    http.expectNone({ method: 'POST', url: URL_EQUIPOS });
    expect(texto()).toContain('Elija un tipo y una marca');
  });

  describe('el buscador', () => {
    const DOS_TIPOS = [...TIPOS, { ...TIPOS[0], id: 't2', nombre: 'Báscula' }];
    const TRES_EQUIPOS = [
      ...EQUIPOS,
      { id: 'e2', idTipoEquipo: 't2', idMarca: ID_MARCA, estadoActivo: true },
      { id: 'e3', idTipoEquipo: ID_TIPO, idMarca: 'm3', estadoActivo: true },
    ];
    const TRES_MARCAS = [...MARCAS, { id: 'm3', nombre: 'Omron', estadoActivo: true }];

    /** Cada fila como «tipo · marca», sin las acciones. */
    const filas = () =>
      [...raiz().querySelectorAll('tbody tr')].map((tr) =>
        [...tr.querySelectorAll('td')]
          .slice(0, 2)
          .map((td) => td.textContent!.trim())
          .join(' · '),
      );

    const buscar = (valor: string) => buscarConLaLupa(fixture, 'filtroEquipos', valor);

    beforeEach(() => abrir({ tipos: DOS_TIPOS, equipos: TRES_EQUIPOS, marcas: TRES_MARCAS }));

    it('la cabecera invierte el orden, por tipo y dentro de cada tipo por marca', async () => {
      raiz().querySelector<HTMLButtonElement>('[data-ordenar]')!.click();
      await asentar(fixture);

      expect(filas()).toEqual([
        'Tensiómetro · Welch Allyn',
        'Tensiómetro · Omron',
        'Báscula · Welch Allyn',
      ]);
    });

    it('en un telefono, encima de las tarjetas, dice cuantos hay y el boton de orden lo invierte', async () => {
      const cuantos = () => raiz().querySelector('[data-cuantos]')!.textContent!.trim();
      expect(cuantos()).toBe('3 resultados');
      expect(raiz().querySelector('[data-ordenar-movil]')!.textContent).toContain('de la A a la Z');

      raiz().querySelector<HTMLButtonElement>('[data-ordenar-movil]')!.click();
      await asentar(fixture);

      expect(filas()[0]).toBe('Tensiómetro · Welch Allyn');
      expect(raiz().querySelector('[data-ordenar-movil]')!.textContent).toContain('de la Z a la A');

      await buscar('bascula');
      expect(cuantos()).toBe('1 resultado');
    });

    it('la tabla se lee como tarjetas en un telefono: cada dato lleva su etiqueta', async () => {
      expect(raiz().querySelector('table')!.classList).toContain('tabla-tarjetas');
      const fila = raiz().querySelector('tbody tr')!;
      expect(fila.querySelector('td:nth-child(2)')!.getAttribute('data-etiqueta')).toBe('Marca');
      expect(fila.querySelector('td[data-acciones]')!.textContent).toContain('Retirar');
    });

    it('cerrar la lupa borra lo buscado y vuelve a ensenar todo', async () => {
      await buscar('bascula');
      expect(filas()).toHaveLength(1);

      raiz().querySelector<HTMLButtonElement>('[data-lupa]')!.click();
      await asentar(fixture);

      expect(raiz().querySelector('#filtroEquipos')).toBeNull();
      expect(filas()).toHaveLength(3);
    });

    it('sin buscar ensena todos, por tipo y luego por marca', () => {
      expect(filas()).toEqual([
        'Báscula · Welch Allyn',
        'Tensiómetro · Omron',
        'Tensiómetro · Welch Allyn',
      ]);
    });

    it('encuentra sin tildes ni mayusculas', async () => {
      await buscar('BASCULA');

      expect(filas()).toEqual(['Báscula · Welch Allyn']);
    });

    it('cada palabra puede caer en el tipo o en la marca', async () => {
      await buscar('tensiometro welch');

      expect(filas()).toEqual(['Tensiómetro · Welch Allyn']);
    });

    it('si nada coincide, lo dice con lo buscado', async () => {
      await buscar('desfibrilador');

      expect(filas()).toEqual([]);
      expect(texto()).toContain('Ningún equipo coincide con «desfibrilador»');
    });
  });

  afterEach(() => {
    // En finally: si verify() lanza, el doble de localStorage no debe pasar al fichero siguiente.
    try {
      http.verify();
    } finally {
      desinstalarAlmacenamiento();
    }
  });
});
