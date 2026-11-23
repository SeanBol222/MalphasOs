import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, atenderRefresco, responderA } from '../../../../testing/pantalla';
import { MARCAS, URL_MARCAS, buscarConLaLupa } from '../../../../testing/catalogo';
import { Marcas } from './marcas';

describe('Marcas del catalogo', () => {
  let fixture: ComponentFixture<Marcas>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[] = ['equipment.read', 'equipment.write']): void {
    TestBed.configureTestingModule({
      providers: [...proveerApiSimulado(), ...proveerSesionFalsa({ autoridades })],
    });
    fixture = TestBed.createComponent(Marcas);
    http = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => montar());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';

  async function abrir(marcas: object = MARCAS): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_MARCAS, marcas);
  }

  function pulsar(etiqueta: string, posicion = 0): void {
    const botones = [...raiz().querySelectorAll('button')].filter(
      (b) => b.textContent?.trim() === etiqueta,
    );

    if (!botones.length) {
      throw new Error(`No hay ningun boton que diga "${etiqueta}"`);
    }

    botones[posicion].click();
    fixture.detectChanges();
  }

  it('lista las marcas y distingue la retirada por peso, no por color', async () => {
    await abrir();

    const nombres = [...raiz().querySelectorAll('tbody td:first-child')];

    expect(texto()).toContain('Welch Allyn');
    // En orden alfabetico, «Marca retirada» va antes que «Welch Allyn».
    expect(nombres.map((n) => n.textContent?.trim())).toEqual(['Marca retirada', 'Welch Allyn']);
    expect(nombres[1].className).toContain('font-bold');
    expect(nombres[0].className).not.toContain('font-bold');
    expect(nombres.every((n) => !n.className.includes('text-accent'))).toBe(true);
  });

  it('el alta no esta a la vista: se abre con «Nueva marca» y se cierra con «Cancelar»', async () => {
    await abrir();
    expect(raiz().querySelector('#marcaNueva')).toBeNull();

    pulsar('Nueva marca');
    expect(raiz().querySelector('#marcaNueva')).not.toBeNull();

    pulsar('Cancelar');
    expect(raiz().querySelector('#marcaNueva')).toBeNull();
  });

  it('busca sin tildes ni mayusculas, y la cabecera invierte el orden', async () => {
    await abrir();
    const nombres = () =>
      [...raiz().querySelectorAll('tbody td:first-child')].map((n) => n.textContent?.trim());

    raiz().querySelector<HTMLButtonElement>('[data-ordenar]')!.click();
    await asentar(fixture);
    expect(nombres()).toEqual(['Welch Allyn', 'Marca retirada']);

    await buscarConLaLupa(fixture, 'filtroMarcas', 'WELCH');
    expect(nombres()).toEqual(['Welch Allyn']);

    await buscarConLaLupa(fixture, 'filtroMarcas', 'omron');
    expect(texto()).toContain('Ninguna marca coincide con «omron»');
  });

  it('anadir una marca manda el nombre, refresca la lista y cierra el alta', async () => {
    await abrir();
    pulsar('Nueva marca');

    const campo = raiz().querySelector<HTMLInputElement>('#marcaNueva')!;
    campo.value = 'Omron';
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    pulsar('Añadir');
    await asentar(fixture);

    const alta = http.expectOne({ method: 'POST', url: URL_MARCAS });

    expect(alta.request.body).toEqual({ nombre: 'Omron' });
    alta.flush({ id: 'm3' });
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL_MARCAS, MARCAS);
    expect(raiz().querySelector('#marcaNueva')).toBeNull();
  });

  it('una marca sin nombre no llega al servidor', async () => {
    await abrir();
    pulsar('Nueva marca');
    pulsar('Añadir');
    await asentar(fixture);

    http.expectNone({ method: 'POST', url: URL_MARCAS });
    expect(texto()).toContain('El nombre es obligatorio');
  });

  it('renombrar llega con el nombre actual puesto y manda un PATCH', async () => {
    await abrir();
    // La segunda fila: en orden alfabetico «Marca retirada» va primero.
    pulsar('Renombrar', 1);

    const campo = raiz().querySelector<HTMLInputElement>('#marca-m1')!;

    expect(campo.value).toBe('Welch Allyn');

    campo.value = 'Welch Allyn Inc';
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    pulsar('Guardar');
    await asentar(fixture);

    const cambio = http.expectOne({ method: 'PATCH', url: `${URL_MARCAS}/m1` });

    expect(cambio.request.body).toEqual({ nombre: 'Welch Allyn Inc' });
    cambio.flush({ id: 'm1' });
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL_MARCAS, MARCAS);
  });

  it('solo la marca activa ofrece retirarse', async () => {
    await abrir();

    const retirar = [...raiz().querySelectorAll('button')].filter(
      (b) => b.textContent?.trim() === 'Retirar',
    );

    expect(retirar).toHaveLength(1);

    // Desde el 2026-11-20 pregunta antes: el primer clic no retira.
    retirar[0].click();
    await asentar(fixture);
    http.expectNone({ method: 'DELETE', url: `${URL_MARCAS}/m1` });
    expect(texto()).toContain('¿Seguro que quiere retirar?');
    // Mientras pregunta, la fila solo enseña la pregunta: «Renombrar» se oculta.
    expect(
      [...raiz().querySelectorAll('[data-marca="m1"] button')].map((b) => b.textContent?.trim()),
    ).toEqual(['Retirar', 'Cancelar']);

    raiz().querySelector<HTMLButtonElement>('[data-confirmar-retiro]')!.click();
    fixture.detectChanges();
    await asentar(fixture);

    http.expectOne({ method: 'DELETE', url: `${URL_MARCAS}/m1` }).flush(null);
    await asentar(fixture);
    // La recarga trae la marca otra vez: si la pregunta se va es porque se cerró.
    await atenderRefresco(fixture, http, URL_MARCAS, MARCAS);
    expect(texto()).not.toContain('¿Seguro que quiere retirar');
  });

  it('«Cancelar» deja la marca como estaba, sin retirarla', async () => {
    await abrir();
    pulsar('Retirar');
    await asentar(fixture);
    pulsar('Cancelar');
    await asentar(fixture);

    http.expectNone({ method: 'DELETE', url: `${URL_MARCAS}/m1` });
    expect(texto()).not.toContain('¿Seguro que quiere retirar');
  });

  it('sin equipment.write se ve el catalogo y no se toca', async () => {
    // Es el caso del grupo clients, que tiene equipment.read y nada mas. El frontend oculta; el
    // permiso lo sigue comprobando el servidor.
    TestBed.resetTestingModule();
    montar(['equipment.read']);
    await abrir();

    expect(texto()).toContain('Welch Allyn');
    expect(texto()).not.toContain('Nueva marca');
    // Solo queda la lupa y lo que ordena —la cabecera y, en un telefono, el boton de encima—: buscar y
    // ordenar no tocan nada.
    expect([
      ...raiz().querySelectorAll(
        'button:not([data-lupa]):not([data-ordenar]):not([data-ordenar-movil])',
      ),
    ]).toHaveLength(0);
  });

  it('un fallo del API se ensena traducido, con el codigo real del modulo', async () => {
    await abrir();
    pulsar('Nueva marca');
    const campo = raiz().querySelector<HTMLInputElement>('#marcaNueva')!;
    campo.value = 'Omron';
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    pulsar('Añadir');
    await asentar(fixture);
    http.expectOne({ method: 'POST', url: URL_MARCAS }).flush(
      { code: 'ERR_EQUIPMENT_007', message: 'Invalid equipment data', details: [] },
      { status: 400, statusText: 'Bad Request' },
    );
    await asentar(fixture);

    const aviso = raiz().querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Revise los datos del equipo');
    expect(aviso?.textContent).not.toContain('Invalid');
  });

  afterEach(() => http.verify());
});
