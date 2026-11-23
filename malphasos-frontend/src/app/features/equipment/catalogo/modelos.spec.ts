import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, elegirEnBuscador, escribirEnBuscador } from '../../../../testing/pantalla';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
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
  buscarConLaLupa,
} from '../../../../testing/catalogo';
import { Modelos } from './modelos';

describe('Modelos del catalogo', () => {
  let fixture: ComponentFixture<Modelos>;
  let http: HttpTestingController;
  let desinstalarAlmacenamiento: () => void;

  beforeEach(() => {
    // Los campos que predicen guardan lo ya elegido en localStorage.
    desinstalarAlmacenamiento = instalarAlmacenamiento();
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

  /** Tipo, marca y fabricante se escriben y se eligen de lo que casa, desde el 2026-11-20. */
  const elegir = (campo: string, etiqueta: string) => elegirEnBuscador(fixture, campo, etiqueta);

  /** Lo que el campo de la marca sugiere al escribir «n», que tienen Welch Allyn y Omron. */
  async function marcasSugeridas(): Promise<(string | undefined)[]> {
    await escribirEnBuscador(fixture, 'marcaDelModelo', 'n');

    return [...raiz().querySelectorAll('#marcaDelModelo-lista [role="option"]')].map((o) =>
      o.textContent?.trim(),
    );
  }

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

  describe('retirar pregunta antes (2026-11-20)', () => {
    // Se pidio tras retirar uno por accidente: ninguna pantalla devuelve un modelo retirado.
    it('«Retirar» no retira: pregunta, y «Cancelar» lo deja como estaba', async () => {
      await abrir();
      pulsar('Retirar');
      await asentar(fixture);

      http.expectNone({ method: 'DELETE', url: `${URL_MODELOS}/${MODELOS[0].id}` });
      expect(texto()).toContain('¿Seguro que quiere retirar?');
      // Mientras pregunta, la fila solo enseña la pregunta: la ficha técnica se oculta.
      expect(texto()).not.toContain('Ficha técnica');

      pulsar('Cancelar');
      expect(texto()).not.toContain('¿Seguro que quiere retirar');
      expect(texto()).toContain('Retirar');
    });

    it('el «Retirar» de la pregunta lo retira, y la pregunta se cierra', async () => {
      await abrir();
      pulsar('Retirar');
      raiz().querySelector<HTMLButtonElement>('[data-confirmar-retiro]')!.click();
      fixture.detectChanges();
      await asentar(fixture);

      http.expectOne({ method: 'DELETE', url: `${URL_MODELOS}/${MODELOS[0].id}` }).flush(null);
      await asentar(fixture);
      // La recarga trae el modelo otra vez: si la pregunta se va es porque se cerró, no porque la
      // lista llegó vacía.
      http.match(URL_MODELOS).forEach((p) => p.flush(MODELOS));
      await atenderRecargas();

      expect(raiz().querySelector('tbody tr')).not.toBeNull();
      expect(texto()).not.toContain('¿Seguro que quiere retirar');
    });
  });

  it('cada modelo enlaza a su ficha tecnica, que tiene pagina propia', async () => {
    // Voltaje, amperaje y el resto de la placa son del modelo desde el 2026-10-05, y se llenan ahi.
    await abrir();

    const enlace = [...raiz().querySelectorAll('a')].find(
      (a) => a.textContent?.trim() === 'Ficha técnica',
    );
    expect(enlace?.getAttribute('href')).toBe(`/catalogo/modelos/${MODELOS[0].id}/ficha`);
  });

  it('dice en una linea que es: lo que se instala en un cliente', async () => {
    // Es la causa mas probable de que el alta de un equipo aparezca sin opciones. La frase se acorto el
    // 2026-11-20, pedido por el usuario: la cabecera lleva una linea.
    await abrir();

    expect(texto()).toContain('Lo que se instala en un cliente.');
  });

  it('una fila se lee cruzando cuatro listas: tipo, marca, fabricante e INVIMA', async () => {
    await abrir();

    const celdas = [...raiz().querySelectorAll('tbody tr:first-child td')].map((td) =>
      td.textContent!.trim(),
    );

    expect(celdas.slice(0, 4)).toEqual([
      'Connex Spot',
      'Tensiómetro · Welch Allyn',
      'Medtronic',
      'INV-1',
    ]);
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

  it('el alta va en dos recuadros, como la de un patrón: de qué es el modelo, y el modelo con los botones', async () => {
    await abrir();
    pulsar('Nuevo modelo');
    const clasificacion = raiz().querySelector('[data-columna="clasificacion"]')!;
    const modelo = raiz().querySelector('[data-columna="modelo"]')!;

    expect(clasificacion.classList).toContain('superficie');
    expect(modelo.classList).toContain('superficie');
    expect(clasificacion.contains(modelo)).toBe(false);
    expect(clasificacion.querySelector('h3')?.textContent?.trim()).toBe('Nuevo modelo');
    for (const id of ['tipoDelModelo', 'marcaDelModelo', 'fabricanteDelModelo']) {
      expect(clasificacion.querySelector(`#${id}`)).not.toBeNull();
    }
    for (const id of ['nombreDelModelo', 'invimaDelModelo']) {
      expect(modelo.querySelector(`#${id}`)).not.toBeNull();
    }
    expect([...modelo.querySelectorAll('button')].map((b) => b.textContent?.trim())).toEqual([
      'Crear modelo',
      'Cancelar',
    ]);
  });

  it('la marca espera al tipo, y despues ofrece primero las que ya fabrican ese tipo', async () => {
    await abrir({ marcas: CON_OMRON });
    pulsar('Nuevo modelo');

    expect(raiz().querySelector<HTMLInputElement>('#marcaDelModelo')!.disabled).toBe(true);

    await elegir('tipoDelModelo', 'Tensiómetro');

    expect(raiz().querySelector<HTMLInputElement>('#marcaDelModelo')!.disabled).toBe(false);
    // Welch Allyn ya fabrica tensiometros: va antes que Omron aunque el alfabeto diga lo contrario. La
    // retirada no se ofrece: el backend rechaza una referencia retirada.
    expect(await marcasSugeridas()).toEqual(['Welch Allyn', 'Omron']);
  });

  it('al hacer clic sin escribir, cada campo ofrece su lista entera', async () => {
    await abrir({ marcas: CON_OMRON, tipos: CON_BASCULA });
    pulsar('Nuevo modelo');
    const ofrecidas = (campo: string) =>
      [...raiz().querySelectorAll(`#${campo}-lista [role="option"]`)].map((o) =>
        o.textContent?.trim(),
      );

    await escribirEnBuscador(fixture, 'tipoDelModelo', '');
    expect(ofrecidas('tipoDelModelo')).toEqual(['Báscula', 'Tensiómetro']);

    await elegir('tipoDelModelo', 'Tensiómetro');
    await escribirEnBuscador(fixture, 'marcaDelModelo', '');
    // Entera, y con la que ya fabrica tensiometros primero.
    expect(ofrecidas('marcaDelModelo')).toEqual(['Welch Allyn', 'Omron']);

    await escribirEnBuscador(fixture, 'fabricanteDelModelo', '');
    expect(ofrecidas('fabricanteDelModelo')).toEqual(['Medtronic']);
  });

  it('las asociadas son las del tipo elegido, no las de cualquiera', async () => {
    await abrir({ marcas: CON_OMRON, tipos: CON_BASCULA });
    pulsar('Nuevo modelo');
    await elegir('tipoDelModelo', 'Báscula');

    // Ninguna fabrica basculas: el orden es solo el alfabetico.
    expect(await marcasSugeridas()).toEqual(['Omron', 'Welch Allyn']);
  });

  it('cambiar de tipo suelta la marca elegida', async () => {
    await abrir({ tipos: CON_BASCULA });
    pulsar('Nuevo modelo');
    await elegir('tipoDelModelo', 'Tensiómetro');
    await elegir('marcaDelModelo', 'Welch Allyn');
    await elegir('tipoDelModelo', 'Báscula');

    expect(raiz().querySelector<HTMLInputElement>('#marcaDelModelo')!.value).toBe('');
  });

  it('cambiar de tipo borra tambien una marca escrita a medias, sin elegir', async () => {
    await abrir({ tipos: CON_BASCULA });
    pulsar('Nuevo modelo');
    await elegir('tipoDelModelo', 'Tensiómetro');
    await escribirEnBuscador(fixture, 'marcaDelModelo', 'wel');
    await elegir('tipoDelModelo', 'Báscula');

    expect(raiz().querySelector<HTMLInputElement>('#marcaDelModelo')!.value).toBe('');
  });

  it('borrar el tipo desactiva otra vez la marca', async () => {
    await abrir();
    pulsar('Nuevo modelo');
    await elegir('tipoDelModelo', 'Tensiómetro');
    await escribirEnBuscador(fixture, 'tipoDelModelo', '');

    expect(raiz().querySelector<HTMLInputElement>('#marcaDelModelo')!.disabled).toBe(true);
  });

  it('sin nombre no llega al servidor', async () => {
    // La validacion del formulario corre antes: es lo que evita un 400 que nadie sabria leer.
    await abrir();
    pulsar('Nuevo modelo');
    await elegir('tipoDelModelo', 'Tensiómetro');
    await elegir('marcaDelModelo', 'Welch Allyn');
    await elegir('fabricanteDelModelo', 'Medtronic');
    await enviar();

    http.expectNone({ method: 'POST', url: URL_MODELOS });
    http.expectNone({ method: 'POST', url: URL_EQUIPOS });
  });

  it('con una marca ya asociada reutiliza la combinacion y manda los cuatro campos', async () => {
    await abrir();
    pulsar('Nuevo modelo');
    await elegir('tipoDelModelo', 'Tensiómetro');
    await elegir('marcaDelModelo', 'Welch Allyn');
    await elegir('fabricanteDelModelo', 'Medtronic');
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
    await elegir('tipoDelModelo', 'Tensiómetro');
    await elegir('marcaDelModelo', 'Omron');
    await elegir('fabricanteDelModelo', 'Medtronic');
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
    await elegir('tipoDelModelo', 'Tensiómetro');
    await elegir('marcaDelModelo', 'Welch Allyn');
    await elegir('fabricanteDelModelo', 'Medtronic');
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
      [...raiz().querySelectorAll('tbody tr td:first-child')].map((td) => td.textContent!.trim());

    const buscar = (valor: string) => buscarConLaLupa(fixture, 'filtroModelos', valor);

    it('la cabecera invierte el orden por nombre', async () => {
      raiz().querySelector<HTMLButtonElement>('[data-ordenar]')!.click();
      await asentar(fixture);

      expect(nombres()).toEqual(['Vital Signs', 'Connex Spot', 'Accutorr 7']);
    });

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

  afterEach(() => {
    // En finally: si verify() lanza, el doble de localStorage no debe pasar al fichero siguiente.
    try {
      http.verify();
    } finally {
      desinstalarAlmacenamiento();
    }
  });
});
