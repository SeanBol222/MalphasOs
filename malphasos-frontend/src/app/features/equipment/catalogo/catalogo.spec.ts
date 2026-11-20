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

  it('se describe en una línea, y en un teléfono el título y las pestañas no se parten (2026-11-20)', async () => {
    const raiz = await abrir('/catalogo/tipos');

    expect(texto(raiz)).toContain('Configuración común a todos los clientes.');
    // El tamaño y la fila que se desliza los decide el CSS con el ancho de la pantalla, que el entorno
    // de pruebas no tiene: lo que se fija es que las clases estén puestas.
    expect(raiz.querySelector('h1')!.className).toContain('max-md:whitespace-nowrap');
    const fila = raiz.querySelector('nav[aria-label="Piezas del catálogo"]')!;
    expect(fila.className).toContain('max-md:overflow-x-auto');
    expect(fila.querySelector('ul')!.className).toContain('flex-nowrap');
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

  it('la subnavegación ofrece las siete, y marca la actual con aria-current', async () => {
    const raiz = await abrir('/catalogo/modelos');
    const enlaces = [...raiz.querySelectorAll('nav[aria-label="Piezas del catálogo"] a')];

    // La etiqueta es el segundo renglón: el primero, desde el rediseño, es cuántos hay.
    expect(enlaces.map((a) => a.querySelector('span:last-child')?.textContent?.trim())).toEqual([
      'Tipos de equipo',
      'Marcas',
      'Fabricantes',
      'Equipos del catálogo',
      'Modelos',
      // Los protocolos, del 2026-10-10: la sexta pieza.
      'Protocolos',
      // Los equipos patrón, del 2026-10-14: la séptima.
      'Patrones',
    ]);
    // El subrayado en acento no le dice nada a quien no ve la pantalla.
    expect(
      raiz.querySelector('nav a[aria-current="page"] span:last-child')?.textContent?.trim(),
    ).toBe('Modelos');
  });

  it('cada pestaña dice cuántas vigentes hay de su pieza, sin las retiradas', async () => {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        // Entrar al catálogo exige equipment.write desde el 2026-10-26: es de quien lo administra.
        ...proveerSesionFalsa({ autoridades: ['equipment.read', 'equipment.write'] }),
        provideRouter(rutasDeNavegacion),
      ],
    });
    harness = await RouterTestingHarness.create();
    http = TestBed.inject(HttpTestingController);
    await harness.navigateByUrl('/catalogo/marcas');
    harness.detectChanges();
    await asentar(harness.fixture);
    http
      .match((r) => r.url.endsWith('/brands'))
      .forEach((p) =>
        p.flush([
          { id: 'm1', estadoActivo: true },
          { id: 'm2', estadoActivo: true },
          { id: 'm3', estadoActivo: false },
        ]),
      );
    http.match(() => true).forEach((peticion) => peticion.flush([]));
    await asentar(harness.fixture);
    const raiz = harness.fixture.nativeElement as HTMLElement;
    const cifras = [...raiz.querySelectorAll('nav[aria-label="Piezas del catálogo"] a')].map((a) =>
      a.querySelector('span')?.textContent?.trim(),
    );

    expect(cifras).toEqual(['0', '2', '0', '0', '0', '0', '0']);
  });

  it('desde una pieza se salta a otra sin volver al menú', async () => {
    const raiz = await abrir('/catalogo/marcas');
    const enlaces = [...raiz.querySelectorAll('nav[aria-label="Piezas del catálogo"] a')];

    expect(enlaces.map((a) => a.getAttribute('href'))).toContain('/catalogo/fabricantes');
  });

  afterEach(() => http.verify());
});
