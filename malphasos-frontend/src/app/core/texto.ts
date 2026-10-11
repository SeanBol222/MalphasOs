/**
 * Sin tildes, sin mayusculas y con los espacios colapsados: lo mismo que la clave de los protocolos en
 * el servidor, y lo que usan los buscadores de los listados. Nacio en {@code parecidos.ts} el 2026-10-10
 * y se movio aqui el 2026-10-11, cuando lo necesito una segunda pantalla.
 */
export function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim().replace(/\s+/g, ' ');
}
