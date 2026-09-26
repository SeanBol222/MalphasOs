import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, atenderRefresco, responderA } from '../../../../testing/pantalla';
import { MARCAS, URL_MARCAS } from '../../../../testing/catalogo';
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

    const nombres = [...raiz().querySelectorAll('li > span:first-child')];

    expect(texto()).toContain('Welch Allyn');
    expect(nombres[0].className).toContain('font-semibold');
    expect(nombres[1].className).not.toContain('font-semibold');
    expect(nombres.every((n) => !n.className.includes('text-accent'))).toBe(true);
  });

  it('anadir una marca manda el nombre y refresca la lista', async () => {
    await abrir();

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
  });

  it('una marca sin nombre no llega al servidor', async () => {
    await abrir();
    pulsar('Añadir');
    await asentar(fixture);

    http.expectNone({ method: 'POST', url: URL_MARCAS });
    expect(texto()).toContain('El nombre es obligatorio');
  });

  it('renombrar llega con el nombre actual puesto y manda un PATCH', async () => {
    await abrir();
    pulsar('Renombrar');

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

    retirar[0].click();
    await asentar(fixture);

    http.expectOne({ method: 'DELETE', url: `${URL_MARCAS}/m1` }).flush(null);
    await asentar(fixture);
    await atenderRefresco(fixture, http, URL_MARCAS, MARCAS);
  });

  it('sin equipment.write se ve el catalogo y no se toca', async () => {
    // Es el caso del grupo clients, que tiene equipment.read y nada mas. El frontend oculta; el
    // permiso lo sigue comprobando el servidor.
    TestBed.resetTestingModule();
    montar(['equipment.read']);
    await abrir();

    expect(texto()).toContain('Welch Allyn');
    expect(raiz().querySelector('#marcaNueva')).toBeNull();
    expect([...raiz().querySelectorAll('button')]).toHaveLength(0);
  });

  it('un fallo del API se ensena traducido, con el codigo real del modulo', async () => {
    await abrir();
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
