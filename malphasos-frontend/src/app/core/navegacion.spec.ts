import { Route } from '@angular/router';
import { CAMINOS_DE_NAVEGACION, NAVEGACION, rutasDeNavegacion } from './navegacion';

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

  it('toda entrada del menú lleva a una página que existe de verdad, incluidas las hijas', async () => {
    // Sin esto, un destino mal escrito solo se descubre pulsandolo. La prueba invoca el cargador de
    // cada destino, que es lo unico que lo demuestra. Recorre tambien las hijas del catalogo: son
    // cinco rutas que el menu ofrece y que nadie mas comprobaria.
    for (const ruta of todasLasRutas()) {
      if (!ruta.loadComponent) {
        continue;
      }

      const cargar = ruta.loadComponent as () => Promise<unknown>;

      await expect(cargar()).resolves.toBeDefined();
    }
  });

  it('cuenta tantos destinos cargables como caminos declara la navegación', () => {
    // Si esto diera menos, la prueba de arriba recorreria una lista incompleta y pasaria sin mirar
    // justo el destino roto. Es la misma precaucion que la de «la lista no esta vacia».
    const cargables = todasLasRutas().filter((ruta) => !!ruta.loadComponent);

    expect(cargables).toHaveLength(CAMINOS_DE_NAVEGACION.length);
  });

  it('una entrada con hijas no deja una página vacía: redirige a la primera', () => {
    for (const entrada of NAVEGACION.filter((e) => e.hijos)) {
      const ruta = rutasDeNavegacion.find((r) => r.path === entrada.ruta)!;
      const redireccion = ruta.children!.find((hija) => hija.path === '');

      expect(redireccion?.redirectTo).toBe(entrada.hijos![0].ruta);
      expect(redireccion?.pathMatch).toBe('full');
    }
  });

  it('cada hija declara su camino, para que se pueda enlazar y recargar', () => {
    // Es la razon de haber usado rutas en vez de pestanas con estado interno.
    for (const entrada of NAVEGACION.filter((e) => e.hijos)) {
      const ruta = rutasDeNavegacion.find((r) => r.path === entrada.ruta)!;

      expect(ruta.children!.map((hija) => hija.path)).toEqual([
        ...entrada.hijos!.map((hija) => hija.ruta),
        '',
      ]);
    }
  });

  it('ninguna entrada tiene etiqueta vacía, ni las hijas', () => {
    for (const entrada of NAVEGACION) {
      expect(entrada.etiqueta.trim()).not.toBe('');

      for (const hija of entrada.hijos ?? []) {
        expect(hija.etiqueta.trim()).not.toBe('');
      }
    }
  });
});

/** Las rutas de la navegación, aplanadas: las de primer nivel y las hijas. */
function todasLasRutas(): Route[] {
  return rutasDeNavegacion.flatMap((ruta) => [ruta, ...(ruta.children ?? [])]);
}
