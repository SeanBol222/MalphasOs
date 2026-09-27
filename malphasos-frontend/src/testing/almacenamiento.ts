/**
 * Un {@code localStorage} de mentira, para un entorno de pruebas que no lo tiene.
 *
 * <p><b>El corredor de pruebas de Angular no expone {@code localStorage}</b> —comprobado el
 * 2026-09-26: acceder a el da {@code undefined}—, aunque si expone {@code document}. El navegador si
 * lo tiene, de modo que lo que falta es el doble, no la funcionalidad.
 *
 * <p>Es tambien la razon de que el codigo de produccion envuelva cada acceso: si el propio corredor de
 * pruebas puede no tenerlo, una ventana privada o un navegador con el almacenamiento bloqueado tampoco,
 * y un formulario no puede caerse por no poder recordar la ultima eleccion.
 */
export function instalarAlmacenamiento(): () => void {
  const anterior = (globalThis as { localStorage?: Storage }).localStorage;
  const datos = new Map<string, string>();

  const falso: Storage = {
    get length() {
      return datos.size;
    },
    clear: () => datos.clear(),
    getItem: (clave) => datos.get(clave) ?? null,
    key: (indice) => [...datos.keys()][indice] ?? null,
    removeItem: (clave) => void datos.delete(clave),
    setItem: (clave, valor) => void datos.set(clave, String(valor)),
  };

  Object.defineProperty(globalThis, 'localStorage', {
    value: falso,
    configurable: true,
    writable: true,
  });

  return () => {
    Object.defineProperty(globalThis, 'localStorage', {
      value: anterior,
      configurable: true,
      writable: true,
    });
  };
}
