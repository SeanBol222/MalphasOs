import { anotarEnHistorial, historialDe } from './historial';
import { instalarAlmacenamiento } from '../../../testing/almacenamiento';

describe('Historial de lo ya elegido', () => {
  let desinstalar: () => void;

  beforeEach(() => {
    desinstalar = instalarAlmacenamiento();
  });
  it('guarda y devuelve, del mas reciente al mas antiguo', () => {
    anotarEnHistorial('prueba', 'a');
    anotarEnHistorial('prueba', 'b');

    expect(historialDe('prueba')).toEqual(['b', 'a']);
  });

  it('no repite: volver a elegir algo lo sube al principio', () => {
    anotarEnHistorial('prueba2', 'a');
    anotarEnHistorial('prueba2', 'b');
    anotarEnHistorial('prueba2', 'a');

    expect(historialDe('prueba2')).toEqual(['a', 'b']);
  });

  it('no pasa de ocho, porque mas no caben en pantalla', () => {
    for (let i = 0; i < 12; i += 1) {
      anotarEnHistorial('prueba3', `id-${i}`);
    }

    expect(historialDe('prueba3')).toHaveLength(8);
    expect(historialDe('prueba3')[0]).toBe('id-11');
  });

  it('sobrevive a lo que el navegador guarde mal, sin lanzar', () => {
    // Puede pasar: una version anterior guardo otra forma, o alguien lo edito a mano.
    localStorage.setItem('malphasos.historial.roto', '{no es json');

    expect(historialDe('roto')).toEqual([]);
  });

  it('un campo sin historial devuelve vacio', () => {
    expect(historialDe('nunca-usado')).toEqual([]);
  });

  it('sin almacenamiento en el navegador, no lanza y no ofrece nada', () => {
    // Es el caso de una ventana privada, y tambien el del corredor de pruebas de Angular, que no
    // expone localStorage: si esto lanzara, el campo de busqueda se caeria al abrirse.
    desinstalar();

    expect(() => anotarEnHistorial('sin-soporte', 'a')).not.toThrow();
    expect(historialDe('sin-soporte')).toEqual([]);

    desinstalar = instalarAlmacenamiento();
  });

  afterEach(() => {
    localStorage.clear();
    desinstalar();
  });
});
