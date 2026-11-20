import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { KeycloakFalso } from '../../../testing/keycloak-falso';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { proveerSesionFalsa } from '../../../testing/keycloak-falso';
import { proveerApiVacio } from '../../../testing/entorno';
import { NAVEGACION } from '../navegacion';
import { routes } from '../../app.routes';
import { Shell } from './shell';

describe('Armazón', () => {
  /** El armazon de pruebas de la ruta actual, para poder detectar cambios tras pulsar algo. */
  let armazon: RouterTestingHarness;

  /**
   * Las autoridades de todos los destinos del menu: una sesion que lo ve entero. Son de lectura salvo
   * {@code equipment.write}, que es la del catalogo desde el 2026-10-26, y {@code super.data-load.write},
   * la de importar datos desde el 2026-11-08.
   *
   * <p>Hacen falta desde el 2026-10-02: el menu oculta lo que la sesion no puede usar, de modo que sin
   * esto se pintaria solo «Inicio». Se nombran una a una en vez de usar {@code admin.full} a proposito
   * —asi la prueba falla si una entrada cambia de autoridad— y porque es el perfil real del grupo que
   * mas destinos ve sin ser administrador.
   */
  const LECTURA_COMPLETA = [
    'client.read',
    'equipment.read',
    'equipment.write',
    'work-order.read',
    'person.read',
    'company.read',
    'super.data-load.write',
  ];

  async function pintar(
    destino = '/inicio',
    autoridades: readonly string[] = LECTURA_COMPLETA,
  ): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        ...proveerSesionFalsa({ autoridades }),
        // El inicio pide la agenda desde el 2026-10-10: aqui no importa la respuesta, solo que llegue.
        ...proveerApiVacio(),
      ],
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

  it('cada destino del menú lleva su dibujo', async () => {
    // «Empresa» no lo tuvo hasta el 2026-11-20: un icono que falta no rompe nada y nadie lo ve en
    // las pruebas, solo en la barra.
    const raiz = await pintar('/inicio', ['super.admin.full']);
    const sinDibujo = [...raiz.querySelectorAll('nav > ul > li')]
      .filter((li) => !li.querySelector('svg path'))
      .map((li) => li.textContent?.trim());

    expect(sinDibujo).toEqual([]);
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
    expect(destinos).not.toContain('Órdenes de trabajo');
    // Lee equipos para ver los suyos, y el catálogo de la empresa no es asunto suyo (2026-10-26).
    expect(destinos).not.toContain('Catálogo');
  });

  describe('La ventana de la sesión', () => {
    // 2026-10-17: el nombre al pie abre una ventana con los datos, la firma digital y cerrar sesión.
    // Sustituye al enlace «Mi firma» y al botón de salir que vivían sueltos junto al nombre.
    const botonDeUsuario = (raiz: HTMLElement) =>
      raiz.querySelector('[data-boton-de-usuario]') as HTMLButtonElement;
    const ventana = (raiz: HTMLElement) => raiz.querySelector('[role="dialog"]');

    async function pulsar(boton: HTMLElement): Promise<void> {
      boton.click();
      await armazon.fixture.whenStable();
      armazon.fixture.detectChanges();
    }

    it('el nombre es un botón que abre la ventana y lo dice', async () => {
      const raiz = await pintar();

      expect(botonDeUsuario(raiz).getAttribute('aria-expanded')).toBe('false');
      expect(ventana(raiz)).toBeNull();

      await pulsar(botonDeUsuario(raiz));

      expect(botonDeUsuario(raiz).getAttribute('aria-expanded')).toBe('true');
      expect(ventana(raiz)?.getAttribute('aria-modal')).toBe('true');
    });

    it('cerrarla devuelve el foco al nombre, que es de donde salió', async () => {
      const raiz = await pintar();
      await pulsar(botonDeUsuario(raiz));

      const cerrar = ventana(raiz)!.querySelector(
        'button[aria-label="Cerrar"]',
      ) as HTMLButtonElement;
      await pulsar(cerrar);

      expect(ventana(raiz)).toBeNull();
      expect(document.activeElement).toBe(botonDeUsuario(raiz));
    });

    it('ya no hay enlace suelto a «Mi firma» en el pie', async () => {
      const raiz = await pintar('/inicio', ['report.write']);

      expect(raiz.querySelector('a[href="/mi-firma"]')).toBeNull();
    });

    it('la dirección vieja de «Mi firma» lleva al inicio en vez de a una página que ya no existe', async () => {
      await pintar('/mi-firma', ['report.write']);

      expect(TestBed.inject(Router).url).toBe('/inicio');
    });
  });

  describe('Los dos bloques del menú', () => {
    const rotulos = (raiz: HTMLElement) =>
      [...raiz.querySelectorAll('nav > p')].map((p) => (p.textContent ?? '').trim());

    it('reparte los destinos en operación y administración, en ese orden', async () => {
      const raiz = await pintar();
      const bloques = [...raiz.querySelectorAll('nav > ul')].map((ul) =>
        [...ul.querySelectorAll(':scope > li')].map((li) => (li.textContent ?? '').trim()),
      );

      expect(rotulos(raiz)).toEqual(['Operación', 'Administración']);
      expect(bloques).toEqual([
        ['Inicio', 'Órdenes de trabajo', 'Equipos', 'Clientes'],
        ['Personas', 'Catálogo', 'Empresa', 'Importar datos'],
      ]);
    });

    it('un bloque sin destinos no pinta su rótulo', async () => {
      // Quien solo tiene client.read no administra nada visible: el rotulo solo seria ruido. Y el
      // catalogo se lee con equipment.read, asi que sin el tambien desaparece «Catalogo».
      const raiz = await pintar('/inicio', ['client.read']);

      expect(rotulos(raiz)).toEqual(['Operación']);
    });

    it('toda entrada de primer nivel declara su bloque', () => {
      // Sin bloque, la entrada caeria en «Operacion» por omision y nadie lo notaria.
      for (const entrada of NAVEGACION) {
        expect(entrada.grupo, `la entrada "${entrada.ruta}"`).toBeDefined();
      }
    });
  });

  describe('La sesión, al pie de la barra', () => {
    it('muestra quién está dentro', async () => {
      const raiz = await pintar();

      expect(raiz.querySelector('header')?.textContent).toContain('Sean Bolívar');
      expect(raiz.querySelector('header')?.textContent).toContain('SB');
    });

    it('cerrar sesión le pide la salida a Keycloak', async () => {
      // No existia en ninguna pantalla hasta el 2026-10-10.
      // Desde el 2026-10-17 vive dentro de la ventana de la sesión.
      const raiz = await pintar();
      (raiz.querySelector('[data-boton-de-usuario]') as HTMLButtonElement).click();
      await armazon.fixture.whenStable();
      armazon.fixture.detectChanges();
      const boton = [...raiz.querySelectorAll('[role="dialog"] button')].find(
        (b) => b.textContent?.trim() === 'Cerrar sesión',
      ) as HTMLButtonElement;

      boton.click();

      expect((TestBed.inject(Keycloak) as KeycloakFalso).salidasPedidas).toBe(1);
    });
  });

  describe('En un teléfono', () => {
    const botonMenu = (raiz: HTMLElement) =>
      raiz.querySelector('button[aria-controls="menu-principal"]') as HTMLButtonElement;

    it('el menú empieza cerrado y el botón lo dice', async () => {
      // La clase hidden la anula md:flex en escritorio; jsdom no aplica estilos, asi que se comprueba
      // la clase y el estado que lee un lector de pantalla.
      const raiz = await pintar();

      expect(botonMenu(raiz).getAttribute('aria-expanded')).toBe('false');
      expect(botonMenu(raiz).getAttribute('aria-label')).toBe('Abrir el menú');
      expect(raiz.querySelector('#menu-principal')!.classList).toContain('hidden');
    });

    it('se abre con el botón y se cierra al elegir un destino', async () => {
      const raiz = await pintar();
      botonMenu(raiz).click();
      armazon.detectChanges();

      expect(botonMenu(raiz).getAttribute('aria-expanded')).toBe('true');
      expect(raiz.querySelector('#menu-principal')!.classList).not.toContain('hidden');

      (raiz.querySelector('#menu-principal a') as HTMLAnchorElement).click();
      armazon.detectChanges();

      expect(botonMenu(raiz).getAttribute('aria-expanded')).toBe('false');
    });
  });

  it('quien trae admin.full lo ve todo menos lo del SuperUsuario, sin nombrar una sola autoridad', async () => {
    // La expansion la resuelve Sesion.puede en un solo sitio, igual que ApiAuthority en el backend, y
    // desde el 2026-11-08 en sus dos escalones: «Importar datos» exige super.data-load.write.
    const raiz = await pintar('/inicio', ['admin.full']);
    const destinos = [...raiz.querySelectorAll('nav > ul > li')].map((li) =>
      li.textContent?.trim(),
    );

    expect(destinos).toHaveLength(NAVEGACION.length - 1);
    expect(destinos).not.toContain('Importar datos');
  });

  it('quien trae super.admin.full lo ve todo, «Importar datos» incluido', async () => {
    const raiz = await pintar('/inicio', ['super.admin.full']);
    const destinos = [...raiz.querySelectorAll('nav > ul > li')].map((li) =>
      li.textContent?.trim(),
    );

    expect(destinos).toHaveLength(NAVEGACION.length);
    expect(destinos).toContain('Importar datos');
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

    it('al pulsarla ofrece las siete piezas, cada una con su dirección', async () => {
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
        'Protocolos',
        // Los equipos patrón, del 2026-10-14.
        'Patrones',
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
