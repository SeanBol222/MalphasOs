import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { proveerSesionFalsa } from '../../../testing/keycloak-falso';
import { NAVEGACION } from '../navegacion';
import { routes } from '../../app.routes';
import { Shell } from './shell';

describe('Armazón', () => {
  /** El armazon de pruebas de la ruta actual, para poder detectar cambios tras pulsar algo. */
  let armazon: RouterTestingHarness;

  /**
   * Las autoridades de lectura de todos los destinos del menu: una sesion que lo ve entero.
   *
   * <p>Hacen falta desde el 2026-10-02: el menu oculta lo que la sesion no puede usar, de modo que sin
   * esto se pintaria solo «Inicio». Se nombran una a una en vez de usar {@code admin.full} a proposito
   * —asi la prueba falla si una entrada cambia de autoridad— y porque es el perfil real del grupo que
   * mas destinos ve sin ser administrador.
   */
  const LECTURA_COMPLETA = [
    'client.read',
    'equipment.read',
    'work-order.read',
    'person.read',
  ];

  async function pintar(
    destino = '/inicio',
    autoridades: readonly string[] = LECTURA_COMPLETA,
  ): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), ...proveerSesionFalsa({ autoridades })],
    });
    armazon = await RouterTestingHarness.create();
    await armazon.navigateByUrl(destino);
    await armazon.fixture.whenStable();

    return armazon.routeDebugElement!.parent!.nativeElement as HTMLElement;
  }

  it('pinta un destino por cada entrada del menú', async () => {
    // Un enlace por entrada simple y un boton por entrada con hijas: «catalogo» no es una pantalla,
    // es el sitio donde estan sus cinco piezas.
    const raiz = await pintar();
    const simples = NAVEGACION.filter((entrada) => !entrada.hijos);
    const desplegables = NAVEGACION.filter((entrada) => entrada.hijos);

    expect(raiz.querySelectorAll('nav > ul > li > a')).toHaveLength(simples.length);
    expect(raiz.querySelectorAll('nav > ul > li > button')).toHaveLength(desplegables.length);
  });

  it('no pinta el destino que la sesión no puede usar', async () => {
    // Es el caso real del grupo `clients` del realm: tiene client.read y equipment.read, y NO tiene
    // person.read. Antes del 2026-10-02 el menu le ofrecia «Personas», que responde 403.
    const raiz = await pintar('/inicio', ['client.read', 'equipment.read']);
    const destinos = [...raiz.querySelectorAll('nav > ul > li')].map((li) =>
      (li.textContent ?? '').trim(),
    );

    expect(destinos).toContain('Clientes');
    expect(destinos).toContain('Equipos');
    expect(destinos).not.toContain('Personas');
    expect(destinos).not.toContain('Órdenes');
  });

  it('quien trae admin.full lo ve todo, sin nombrar una sola autoridad de recurso', async () => {
    // La expansion la resuelve Sesion.puede en un solo sitio, igual que ApiAuthority en el backend.
    const raiz = await pintar('/inicio', ['admin.full']);

    expect(raiz.querySelectorAll('nav > ul > li')).toHaveLength(NAVEGACION.length);
  });

  describe('La entrada que se despliega', () => {
    const boton = (raiz: HTMLElement) =>
      [...raiz.querySelectorAll('nav button')].find((b) =>
        (b.textContent ?? '').includes('Catálogo'),
      ) as HTMLButtonElement;

    it('empieza cerrada y no ofrece sus piezas', async () => {
      const raiz = await pintar();

      expect(boton(raiz).getAttribute('aria-expanded')).toBe('false');
      expect(raiz.querySelector('#catalogo-submenu')).toBeNull();
    });

    it('al pulsarla ofrece las cinco piezas, cada una con su dirección', async () => {
      const raiz = await pintar();
      boton(raiz).click();
      // Sin detectar cambios el desplegable no se pinta: la senal cambio, no el DOM.
      armazon.detectChanges();

      const enlaces = [...raiz.querySelectorAll('#catalogo-submenu a')];

      expect(enlaces.map((a) => a.textContent?.trim())).toEqual([
        'Tipos de equipo',
        'Marcas',
        'Fabricantes',
        'Equipos del catálogo',
        'Modelos',
      ]);
      expect(enlaces.map((a) => a.getAttribute('href'))).toContain('/catalogo/fabricantes');
      expect(boton(raiz).getAttribute('aria-expanded')).toBe('true');
    });

    it('se cierra al volver a pulsarla', async () => {
      const raiz = await pintar();
      boton(raiz).click();
      armazon.detectChanges();
      boton(raiz).click();
      armazon.detectChanges();

      expect(raiz.querySelector('#catalogo-submenu')).toBeNull();
    });

    it('el botón declara qué controla, para un lector de pantalla', async () => {
      // aria-expanded sin aria-controls deja al lector sabiendo que algo se abrio y no que.
      const raiz = await pintar();

      expect(boton(raiz).getAttribute('aria-controls')).toBe('catalogo-submenu');
    });
  });

  describe('Lo que WCAG 2.1 AA exige y no se ve', () => {
    it('el enlace de salto apunta al contenido principal', async () => {
      // Sin el, cada pagina obliga a tabular la navegacion entera antes de
      // llegar a lo que se vino a leer. Criterio 2.4.1.
      const raiz = await pintar();
      const salto = raiz.querySelector('a[href="#contenido"]');

      expect(salto?.textContent?.trim()).toBe('Saltar al contenido');
      expect(raiz.querySelector('main')?.id).toBe('contenido');
    });

    it('estan los tres puntos de referencia por los que se mueve un lector', async () => {
      const raiz = await pintar();

      expect(raiz.querySelector('header')).toBeTruthy();
      expect(raiz.querySelector('nav[aria-label]')).toBeTruthy();
      expect(raiz.querySelector('main')).toBeTruthy();
    });

    it('el destino activo se marca con aria-current y no solo con color', async () => {
      // El subrayado en acento no le dice nada a quien no ve la pantalla.
      const raiz = await pintar('/inicio');
      const activo = raiz.querySelector('nav a[aria-current="page"]');

      expect(activo?.textContent?.trim()).toBe('Inicio');
    });

    it('el botón que despliega respeta el área táctil mínima', async () => {
      const raiz = await pintar();

      expect(raiz.querySelector('nav button')!.className).toContain('min-h-tactil');
    });

    it('los enlaces del menú respetan el área táctil mínima', async () => {
      // 44 px es lo que hace medible RNF-14. Se comprueba la clase y no el
      // alto calculado porque jsdom no aplica hojas de estilo.
      const raiz = await pintar();

      for (const enlace of raiz.querySelectorAll('nav a')) {
        expect(enlace.className).toContain('min-h-tactil');
      }
    });
  });

  it('el armazón no envuelve nada fuera de la sesión', () => {
    // El inicio de sesion cuelga de la raiz, no de aqui. Si algun dia una ruta
    // publica entrara como hija del armazon, ensenaria la navegacion a quien
    // todavia no ha entrado.
    const armazon = routes.find((r) => r.path === '');

    expect(armazon?.children?.map((c) => c.path)).not.toContain('login');
  });
});
