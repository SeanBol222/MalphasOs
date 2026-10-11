import { Protocolo } from '../../../core/api/tipos';
import { normalizar } from '../../../core/texto';

export { normalizar };

/**
 * Los protocolos vigentes que se parecen a un nombre que se esta escribiendo: el mismo sin tildes ni
 * mayusculas, uno contenido en el otro, o una palabra de cuatro letras o mas en comun.
 *
 * <p>Existe porque el usuario pidio, el 2026-10-10, que no fuera facil repetir protocolos. Lo usan las
 * dos pantallas donde se crea uno —el catalogo y el panel dentro del tipo de equipo—, y por eso vive
 * aparte. Con menos de tres letras no avisa de nada: todo se pareceria a todo.
 */
export function parecidosA(nombre: string, protocolos: readonly Protocolo[]): readonly Protocolo[] {
  const escrito = normalizar(nombre);

  if (escrito.length < 3) {
    return [];
  }

  const palabras = escrito.split(' ').filter((palabra) => palabra.length >= 4);

  return protocolos.filter((protocolo) => {
    if (!protocolo.estadoActivo) {
      return false;
    }

    const suyo = normalizar(protocolo.nombre ?? '');

    return (
      suyo.includes(escrito) ||
      escrito.includes(suyo) ||
      palabras.some((palabra) => suyo.split(' ').includes(palabra))
    );
  });
}

/** El vigente que tiene exactamente ese nombre, sin contar mayusculas ni tildes: el que el servidor rechazaria. */
export function mismoNombreQue(
  nombre: string,
  protocolos: readonly Protocolo[],
): Protocolo | undefined {
  return protocolos.find(
    (protocolo) =>
      protocolo.estadoActivo && normalizar(protocolo.nombre ?? '') === normalizar(nombre),
  );
}
