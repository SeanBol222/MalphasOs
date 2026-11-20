import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
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

  beforeEach(() => {
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

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('change'));
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

  it('solo ofrece tipos y marcas activos: el backend rechaza una referencia retirada', async () => {
    await abrir();
    pulsar('Nuevo equipo');

    const marcas = [...raiz().querySelectorAll<HTMLOptionElement>('#marcaDelEquipo option')];

    expect(marcas.map((o) => o.textContent?.trim())).toEqual(['Elija una marca', 'Welch Allyn']);
    expect(texto()).not.toContain('Marca retirada');
  });

  it('combinar manda los dos identificadores y nada mas, y cierra el formulario', async () => {
    await abrir();
    pulsar('Nuevo equipo');
    escribir('tipoDelEquipo', ID_TIPO);
    escribir('marcaDelEquipo', ID_MARCA);
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

  afterEach(() => http.verify());
});
