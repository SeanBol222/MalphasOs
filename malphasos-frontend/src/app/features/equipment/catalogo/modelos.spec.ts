import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import {
  MARCAS,
  MODELOS,
  ID_EQUIPO,
  ID_FABRICANTE,
  ID_MARCA,
  ID_TIPO,
  responderAlCatalogo,
  TIPOS,
  URL_EQUIPOS,
  URL_MODELOS,
} from '../../../../testing/catalogo';
import { Modelos } from './modelos';

describe('Modelos del catalogo', () => {
  let fixture: ComponentFixture<Modelos>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['equipment.read', 'equipment.write'] }),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(Modelos);
    http = TestBed.inject(HttpTestingController);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(datos: Parameters<typeof responderAlCatalogo>[2] = {}): Promise<void> {
    await responderAlCatalogo(fixture, http, datos);
  }

  function escribir(id: string, valor: string): void {
    const campo = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function pulsar(etiqueta: string): void {
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === etiqueta)!.click();
    fixture.detectChanges();
  }

  /** Una marca que todavia no fabrica ningun tipo, y un segundo tipo que no tiene ninguna marca. */
  const CON_OMRON = [...MARCAS, { id: 'm3', nombre: 'Omron', estadoActivo: true }];
  const CON_BASCULA = [...TIPOS, { ...TIPOS[0], id: 't2', nombre: 'Báscula' }];

  const grupo = (etiqueta: string) =>
    [
      ...raiz().querySelectorAll<HTMLOptionElement>(
        `#marcaDelModelo optgroup[label="${etiqueta}"] option`,
      ),
    ].map((o) => o.textContent?.trim());

  /** Lo que una escritura del catalogo vuelve a pedir: todo cuelga de la misma clave. */
  async function atenderRecargas(): Promise<void> {
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  }

  async function enviar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);
  }

  it('cada modelo enlaza a su ficha tecnica, que tiene pagina propia', async () => {
    // Voltaje, amperaje y el resto de la placa son del modelo desde el 2026-10-05, y se llenan ahi.
    await abrir();

    const enlace = [...raiz().querySelectorAll('a')].find(
      (a) => a.textContent?.trim() === 'Ficha técnica',
    );
    expect(enlace?.getAttribute('href')).toBe(`/catalogo/modelos/${MODELOS[0].id}/ficha`);
  });

  it('dice que un modelo es lo unico que se puede instalar', async () => {
    // Es la causa mas probable de que el alta de un equipo aparezca sin opciones.
    await abrir();

    expect(texto()).toContain('Es lo único que se puede instalar');
  });

  it('una fila se lee cruzando cuatro listas: tipo, marca, fabricante e INVIMA', async () => {
    await abrir();

    const fila = raiz().querySelector('li')!.textContent ?? '';

    expect(fila).toContain('Tensiómetro');
    expect(fila).toContain('Welch Allyn');
    expect(fila).toContain('Medtronic');
    expect(fila).toContain('INVIMA INV-1');
  });

  it('el formulario no esta a la vista: se abre con «Nuevo modelo» y se cierra con «Cancelar»', async () => {
    await abrir();
    expect(raiz().querySelector('form')).toBeNull();

    pulsar('Nuevo modelo');
    expect(raiz().querySelector('form')).not.toBeNull();
    // Abierto, el boton sobra: ya se esta creando uno.
    expect([...raiz().querySelectorAll('button')].map((b) => b.textContent?.trim())).not.toContain(
      'Nuevo modelo',
    );

    pulsar('Cancelar');
    expect(raiz().querySelector('form')).toBeNull();
  });

  it('la marca espera al tipo, y despues ofrece primero las que ya fabrican ese tipo', async () => {
    await abrir({ marcas: CON_OMRON });
    pulsar('Nuevo modelo');

    expect(raiz().querySelector<HTMLSelectElement>('#marcaDelModelo')!.disabled).toBe(true);

    escribir('tipoDelModelo', ID_TIPO);

    expect(raiz().querySelector<HTMLSelectElement>('#marcaDelModelo')!.disabled).toBe(false);
    expect(grupo('Ya asociadas a este tipo')).toEqual(['Welch Allyn']);
    // La retirada no se ofrece: el backend rechaza una referencia retirada.
    expect(grupo('Otras marcas')).toEqual(['Omron']);
  });

  it('las asociadas son las del tipo elegido, no las de cualquiera', async () => {
    await abrir({ marcas: CON_OMRON, tipos: CON_BASCULA });
    pulsar('Nuevo modelo');
    escribir('tipoDelModelo', 't2');

    expect(grupo('Ya asociadas a este tipo')).toEqual([]);
    expect(grupo('Otras marcas')).toEqual(['Omron', 'Welch Allyn']);
  });

  it('cambiar de tipo suelta la marca elegida', async () => {
    await abrir({ tipos: CON_BASCULA });
    pulsar('Nuevo modelo');
    escribir('tipoDelModelo', ID_TIPO);
    escribir('marcaDelModelo', ID_MARCA);
    escribir('tipoDelModelo', 't2');

    expect(raiz().querySelector<HTMLSelectElement>('#marcaDelModelo')!.value).toBe('');
  });

  it('volver a «Elija un tipo» desactiva otra vez la marca', async () => {
    await abrir();
    pulsar('Nuevo modelo');
    escribir('tipoDelModelo', ID_TIPO);
    escribir('tipoDelModelo', '');

    expect(raiz().querySelector<HTMLSelectElement>('#marcaDelModelo')!.disabled).toBe(true);
  });

  it('sin nombre no llega al servidor', async () => {
    // La validacion del formulario corre antes: es lo que evita un 400 que nadie sabria leer.
    await abrir();
    pulsar('Nuevo modelo');
    escribir('tipoDelModelo', ID_TIPO);
    escribir('marcaDelModelo', ID_MARCA);
    escribir('fabricanteDelModelo', ID_FABRICANTE);
    await enviar();

    http.expectNone({ method: 'POST', url: URL_MODELOS });
    http.expectNone({ method: 'POST', url: URL_EQUIPOS });
  });

  it('con una marca ya asociada reutiliza la combinacion y manda los cuatro campos', async () => {
    await abrir();
    pulsar('Nuevo modelo');
    escribir('tipoDelModelo', ID_TIPO);
    escribir('marcaDelModelo', ID_MARCA);
    escribir('fabricanteDelModelo', ID_FABRICANTE);
    escribir('nombreDelModelo', 'IdeaPad 3');
    escribir('invimaDelModelo', 'INV-2');
    await enviar();

    // Ninguna combinacion nueva: ya existia y otra igual seria un duplicado.
    http.expectNone({ method: 'POST', url: URL_EQUIPOS });
    const alta = http.expectOne({ method: 'POST', url: URL_MODELOS });

    expect(alta.request.body).toEqual({
      nombre: 'IdeaPad 3',
      idEquipo: ID_EQUIPO,
      idFabricante: ID_FABRICANTE,
      invima: 'INV-2',
    });
    alta.flush({ id: 'mo2' });
    await atenderRecargas();
    expect(raiz().querySelector('form')).toBeNull();
  });

  it('con una marca sin asociar crea antes la combinacion, y el modelo cuelga de ella', async () => {
    await abrir({ marcas: CON_OMRON });
    pulsar('Nuevo modelo');
    escribir('tipoDelModelo', ID_TIPO);
    escribir('marcaDelModelo', 'm3');
    escribir('fabricanteDelModelo', ID_FABRICANTE);
    escribir('nombreDelModelo', 'HEM-7120');
    await enviar();

    const combinacion = http.expectOne({ method: 'POST', url: URL_EQUIPOS });
    expect(combinacion.request.body).toEqual({ idTipoEquipo: ID_TIPO, idMarca: 'm3' });
    combinacion.flush({ id: 'e-nuevo' });
    await asentar(fixture);

    const alta = http.expectOne({ method: 'POST', url: URL_MODELOS });
    // Sin INVIMA la clave no viaja: un modelo sin registro sanitario es normal mientras se tramita.
    expect(alta.request.body).toEqual({
      nombre: 'HEM-7120',
      idEquipo: 'e-nuevo',
      idFabricante: ID_FABRICANTE,
    });
    alta.flush({ id: 'mo3' });
    await atenderRecargas();
  });

  it('si el alta falla, lo dice y el formulario sigue abierto con lo escrito', async () => {
    await abrir();
    pulsar('Nuevo modelo');
    escribir('tipoDelModelo', ID_TIPO);
    escribir('marcaDelModelo', ID_MARCA);
    escribir('fabricanteDelModelo', ID_FABRICANTE);
    escribir('nombreDelModelo', 'IdeaPad 3');
    await enviar();

    http
      .expectOne({ method: 'POST', url: URL_MODELOS })
      .flush({ codigo: 'X' }, { status: 409, statusText: 'Conflict' });
    await asentar(fixture);

    expect(raiz().querySelector('[role="alert"]')).not.toBeNull();
    expect(raiz().querySelector<HTMLInputElement>('#nombreDelModelo')!.value).toBe('IdeaPad 3');
  });

  it('sin nada elegido no llega al servidor y dice que falta', async () => {
    await abrir();
    pulsar('Nuevo modelo');
    await enviar();

    http.expectNone({ method: 'POST', url: URL_MODELOS });
    expect(texto()).toContain('Elija el tipo, la marca y el fabricante');
  });

  describe('el buscador', () => {
    const TRES_MODELOS = [
      MODELOS[0],
      { ...MODELOS[0], id: 'mo2', nombre: 'Accutorr 7', invima: 'INV-77' },
      { ...MODELOS[0], id: 'mo3', nombre: 'Vital Signs', idEquipo: 'e3', invima: undefined },
    ];
    const DOS_EQUIPOS = [
      { id: ID_EQUIPO, idTipoEquipo: ID_TIPO, idMarca: ID_MARCA, estadoActivo: true },
      { id: 'e3', idTipoEquipo: ID_TIPO, idMarca: 'm3', estadoActivo: true },
    ];

    const nombres = () =>
      [...raiz().querySelectorAll('li > span')].map((s) => s.firstChild!.textContent!.trim());

    async function buscar(valor: string): Promise<void> {
      const campo = raiz().querySelector<HTMLInputElement>('#filtroModelos')!;
      campo.value = valor;
      campo.dispatchEvent(new Event('input'));
      await asentar(fixture);
    }

    beforeEach(() => abrir({ modelos: TRES_MODELOS, equipos: DOS_EQUIPOS, marcas: CON_OMRON }));

    it('sin buscar ensena todos, en orden alfabetico', () => {
      expect(nombres()).toEqual(['Accutorr 7', 'Connex Spot', 'Vital Signs']);
    });

    it('encuentra por el nombre sin tildes ni mayusculas', async () => {
      await buscar('ACCUTORR');
      expect(nombres()).toEqual(['Accutorr 7']);
    });

    it('cada palabra puede caer en el tipo, la marca, el fabricante o el INVIMA', async () => {
      await buscar('tensiometro omron');
      expect(nombres()).toEqual(['Vital Signs']);

      await buscar('inv-77');
      expect(nombres()).toEqual(['Accutorr 7']);

      await buscar('medtronic welch');
      expect(nombres()).toEqual(['Accutorr 7', 'Connex Spot']);
    });

    it('si nada coincide, lo dice con lo buscado', async () => {
      await buscar('desfibrilador');

      expect(nombres()).toEqual([]);
      expect(texto()).toContain('Ningún modelo coincide con «desfibrilador»');
    });
  });

  afterEach(() => http.verify());
});
