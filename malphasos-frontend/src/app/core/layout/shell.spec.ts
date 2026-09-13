import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { NAVEGACION } from '../navegacion';
import { routes } from '../../app.routes';
import { Shell } from './shell';

describe('Armazón', () => {
  async function pintar(destino = '/inicio'): Promise<HTMLElement> {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(destino);
    await harness.fixture.whenStable();

    return harness.routeDebugElement!.parent!.nativeElement as HTMLElement;
  }

  it('pinta un enlace por cada entrada del menú', async () => {
    const raiz = await pintar();

    expect(raiz.querySelectorAll('nav a')).toHaveLength(NAVEGACION.length);
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
