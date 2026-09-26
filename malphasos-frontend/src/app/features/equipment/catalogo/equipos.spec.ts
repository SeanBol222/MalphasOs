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

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('dice que un equipo del catalogo no es una maquina', async () => {
    // Es la confusion mas probable de la pantalla, y quien la sufra buscara aqui su tensiometro.
    await abrir();

    expect(texto()).toContain('no una máquina');
    expect(texto()).toContain('se registra en un área');
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

  it('solo ofrece tipos y marcas activos: el backend rechaza una referencia retirada', async () => {
    await abrir();

    const marcas = [...raiz().querySelectorAll<HTMLOptionElement>('#marcaDelEquipo option')];

    expect(marcas.map((o) => o.textContent?.trim())).toEqual(['Elija una marca', 'Welch Allyn']);
    expect(texto()).not.toContain('Marca retirada');
  });

  it('combinar manda los dos identificadores y nada mas', async () => {
    await abrir();
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
  });

  it('sin elegir las dos piezas no llega al servidor', async () => {
    await abrir();
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);

    http.expectNone({ method: 'POST', url: URL_EQUIPOS });
    expect(texto()).toContain('Elija un tipo y una marca');
  });

  afterEach(() => http.verify());
});
