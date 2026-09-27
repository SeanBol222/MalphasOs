import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import { rutasDeNavegacion } from '../../../core/navegacion';

/**
 * El catálogo dejó de ser una página con cinco secciones y pasó a ser cinco páginas.
 *
 * <p>Se prueba navegando de verdad, con {@code RouterTestingHarness} y las rutas reales: lo que hay que
 * comprobar es que cada pieza <b>tiene su propia dirección</b> y que solo se ve una. Montando el
 * componente a mano no se vería ninguna, porque su contenido lo pone el router.
 */
describe('Catálogo de equipos', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  async function abrir(url: string): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades: ['equipment.read', 'equipment.write'] }),
        provideRouter(rutasDeNavegacion),
      ],
    });
    harness = await RouterTestingHarness.create();
    http = TestBed.inject(HttpTestingController);
    await harness.navigateByUrl(url);
    harness.detectChanges();
    await asentar(harness.fixture);
    // Cada pieza pide lo suyo; aqui lo que se prueba es la estructura, no los datos.
    http.match(() => true).forEach((peticion) => peticion.flush([]));
    await asentar(harness.fixture);

    return harness.fixture.nativeElement as HTMLElement;
  }

  const texto = (raiz: HTMLElement) => raiz.textContent ?? '';

  it('entrar en el catálogo lleva a la primera pieza, no a una página vacía', async () => {
    const raiz = await abrir('/catalogo');

    // La primera es «Tipos de equipo», no «Marcas»: ver la nota del orden en navegacion.ts.
    expect(TestBed.inject((await import('@angular/router')).Router).url).toBe('/catalogo/tipos');
    expect(texto(raiz)).toContain('Tipos de equipo');
  });

  it('cada pieza tiene su propia dirección', async () => {
    const raiz = await abrir('/catalogo/fabricantes');

    expect(texto(raiz)).toContain('Fabricantes');
  });

  it('solo se ve una pieza a la vez: es para lo que se hizo el cambio', async () => {
    // Antes las cinco compartian pagina y con treinta marcas llegar a los fabricantes eran cuatro
    // pantallas de desplazamiento.
    const raiz = await abrir('/catalogo/fabricantes');

    expect(raiz.querySelectorAll('section')).toHaveLength(1);
    expect(texto(raiz)).not.toContain('Añadir una marca');
    expect(texto(raiz)).not.toContain('Crear modelo');
  });

  it('la subnavegación ofrece las cinco, y marca la actual con aria-current', async () => {
    const raiz = await abrir('/catalogo/modelos');
    const enlaces = [...raiz.querySelectorAll('nav[aria-label="Piezas del catálogo"] a')];

    expect(enlaces.map((a) => a.textContent?.trim())).toEqual([
      'Tipos de equipo',
      'Marcas',
      'Fabricantes',
      'Equipos del catálogo',
      'Modelos',
    ]);
    // El subrayado en acento no le dice nada a quien no ve la pantalla.
    expect(raiz.querySelector('nav a[aria-current="page"]')?.textContent?.trim()).toBe('Modelos');
  });

  it('desde una pieza se salta a otra sin volver al menú', async () => {
    const raiz = await abrir('/catalogo/marcas');
    const enlaces = [...raiz.querySelectorAll('nav[aria-label="Piezas del catálogo"] a')];

    expect(enlaces.map((a) => a.getAttribute('href'))).toContain('/catalogo/fabricantes');
  });

  afterEach(() => http.verify());
});
