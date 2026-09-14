import { NAVEGACION, rutasDeNavegacion } from './navegacion';

/**
 * El invariante de la navegacion: menu y rutas salen de la misma lista.
 *
 * <p>Es el mismo tipo de prueba que RestAuthorizationCoverageTest en el
 * backend: no ejerce un caso, fija una relacion estructural. Alli impedia que
 * una autoridad quedara sin endpoint; aqui impide que el menu ofrezca un
 * destino que no existe, o que exista uno que el menu no ofrece.
 */
describe('Navegación', () => {
  it('hay una ruta por cada entrada del menú, y ninguna de más', () => {
    expect(rutasDeNavegacion.map((r) => r.path)).toEqual(NAVEGACION.map((e) => e.ruta));
  });

  it('toda entrada del menú lleva a una página que existe de verdad', async () => {
    // Sin esto, un destino mal escrito solo se descubre pulsandolo. La prueba
    // invoca el cargador de cada entrada, que es lo unico que lo demuestra.
    for (const ruta of rutasDeNavegacion) {
      const cargar = ruta.loadComponent as () => Promise<unknown>;

      await expect(cargar()).resolves.toBeDefined();
    }
  });

  it('ninguna entrada tiene etiqueta vacía', () => {
    for (const entrada of NAVEGACION) {
      expect(entrada.etiqueta.trim()).not.toBe('');
    }
  });
});
