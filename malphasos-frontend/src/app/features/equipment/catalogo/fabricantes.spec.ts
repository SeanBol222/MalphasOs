import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, elegirEnBuscador, responderA } from '../../../../testing/pantalla';
import { instalarAlmacenamiento } from '../../../../testing/almacenamiento';
import { FABRICANTES, URL_FABRICANTES, buscarConLaLupa } from '../../../../testing/catalogo';
import { Fabricantes } from './fabricantes';

const URL_PAISES = 'http://localhost:8081/v1/api/countries';
const PAISES = [{ id: 'co', nombre: 'Colombia', codigoIso: 'CO', estadoActivo: true }];

describe('Fabricantes del catalogo', () => {
  let fixture: ComponentFixture<Fabricantes>;
  let http: HttpTestingController;

  let desinstalarAlmacenamiento: () => void;

  beforeEach(() => {
    desinstalarAlmacenamiento = instalarAlmacenamiento();
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['equipment.read', 'equipment.write'] }),
      ],
    });
    fixture = TestBed.createComponent(Fabricantes);
    http = TestBed.inject(HttpTestingController);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(
    fabricantes: object = FABRICANTES,
    paises: object = PAISES,
    opcionesDePaises?: { status: number; statusText: string },
  ): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_FABRICANTES, fabricantes);
    await responderA(fixture, http, URL_PAISES, paises, opcionesDePaises);
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

  async function enviar(): Promise<void> {
    raiz().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar(fixture);
  }

  it('dice en una linea que es: quien lo produce y en que pais', async () => {
    // La frase se acorto el 2026-11-20, pedido por el usuario: la cabecera lleva una linea.
    await abrir();

    expect(texto()).toContain('Quién lo produce y en qué país.');
  });

  it('el alta no esta a la vista: se abre con «Nuevo fabricante» y se cierra con «Cancelar»', async () => {
    await abrir();
    expect(raiz().querySelector('form')).toBeNull();

    pulsar('Nuevo fabricante');
    expect(raiz().querySelector('form')).not.toBeNull();

    pulsar('Cancelar');
    expect(raiz().querySelector('form')).toBeNull();
  });

  it('busca por el pais, y la cabecera invierte el orden por nombre', async () => {
    await abrir([
      ...FABRICANTES,
      { id: 'f2', nombre: 'Philips', estadoActivo: true },
    ]);
    const nombres = () =>
      [...raiz().querySelectorAll('tbody td:first-child')].map((n) => n.textContent?.trim());

    expect(nombres()).toEqual(['Medtronic', 'Philips']);
    raiz().querySelector<HTMLButtonElement>('[data-ordenar]')!.click();
    await asentar(fixture);
    expect(nombres()).toEqual(['Philips', 'Medtronic']);

    await buscarConLaLupa(fixture, 'filtroFabricantes', 'colombia');
    expect(nombres()).toEqual(['Medtronic']);
  });

  it('ensena el pais en palabras', async () => {
    await abrir();

    expect(texto()).toContain('Medtronic');
    expect(texto()).toContain('Colombia');
  });

  it('un fabricante sin pais dice "sin especificar", y uno con pais ilegible dice otra cosa', async () => {
    // Las dos situaciones se veian igual y no son lo mismo: sin location.read el catalogo responde
    // 403, y decir "sin especificar" de un fabricante que si tiene pais es afirmar algo falso.
    await abrir([{ id: 'f2', nombre: 'Sin país', estadoActivo: true }]);

    expect(texto()).toContain('Sin especificar');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['equipment.read'] }),
      ],
    });
    fixture = TestBed.createComponent(Fabricantes);
    http = TestBed.inject(HttpTestingController);
    await abrir(FABRICANTES, { code: 'ERR_LOCATION_001' }, { status: 403, statusText: 'Forbidden' });

    expect(texto()).toContain('No disponible');
  });

  it('crear un fabricante con pais manda las dos claves, y cierra el alta', async () => {
    await abrir();
    pulsar('Nuevo fabricante');
    escribir('fabricanteNuevo', 'Philips');
    await elegirEnBuscador(fixture, 'paisFabricante', 'Colombia');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_FABRICANTES });

    expect(alta.request.body).toEqual({ nombre: 'Philips', idPais: 'co' });
    alta.flush({ id: 'f3' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
    expect(raiz().querySelector('form')).toBeNull();
  });

  it('sin pais elegido, la clave no viaja', async () => {
    await abrir();
    pulsar('Nuevo fabricante');
    escribir('fabricanteNuevo', 'Philips');
    await enviar();

    const alta = http.expectOne({ method: 'POST', url: URL_FABRICANTES });

    expect(alta.request.body).not.toHaveProperty('idPais');
    alta.flush({ id: 'f3' });
    await asentar(fixture);
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);
  });

  it('renombrar llega con el nombre actual puesto y manda solo el nombre: el país se queda', async () => {
    await abrir();
    pulsar('Renombrar');

    const campo = raiz().querySelector<HTMLInputElement>(`#fabricante-${FABRICANTES[0].id}`)!;
    expect(campo.value).toBe('Medtronic');

    escribir(`fabricante-${FABRICANTES[0].id}`, ' Medtronic plc ');
    pulsar('Guardar');
    await asentar(fixture);

    const cambio = http.expectOne({ method: 'PATCH', url: `${URL_FABRICANTES}/${FABRICANTES[0].id}` });
    expect(cambio.request.body).toEqual({ nombre: 'Medtronic plc' });
    cambio.flush({ ...FABRICANTES[0], nombre: 'Medtronic plc' });
    await asentar(fixture);
    // La recarga trae el fabricante otra vez: si la fila desaparece es porque el campo se cerró, no
    // porque la lista llegó vacía.
    http.match(URL_FABRICANTES).forEach((p) => p.flush(FABRICANTES));
    http.match(() => true).forEach((p) => p.flush([]));
    await asentar(fixture);

    expect(raiz().querySelector(`#fabricante-${FABRICANTES[0].id}`)).toBeNull();
    expect(raiz().querySelector(`[data-fabricante="${FABRICANTES[0].id}"]`)).not.toBeNull();
  });

  it('renombrar con el nombre vacío no llega al servidor, y Cancelar deja la fila como estaba', async () => {
    await abrir();
    pulsar('Renombrar');
    escribir(`fabricante-${FABRICANTES[0].id}`, '');
    pulsar('Guardar');
    await asentar(fixture);

    http.expectNone({ method: 'PATCH', url: `${URL_FABRICANTES}/${FABRICANTES[0].id}` });
    expect(texto()).toContain('El nombre es obligatorio');

    pulsar('Cancelar');
    expect(raiz().querySelector(`#fabricante-${FABRICANTES[0].id}`)).toBeNull();
    expect(texto()).toContain('Medtronic');
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
});
