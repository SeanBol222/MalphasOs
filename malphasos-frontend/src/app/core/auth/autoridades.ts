/**
 * Como el frontend lee las autoridades del token, y por que no las enumera.
 *
 * <p>El backend define 19 autoridades de recurso y las publica como roles del
 * client {@code malphasos-api}. Copiarlas aqui seria una segunda lista, y dos
 * listas escritas por separado se desincronizan: es un riesgo que este proyecto
 * ya tiene documentado.
 *
 * <p><b>No hace falta copiarlas.</b> Lo que el frontend necesita no es la lista
 * sino <b>la regla</b>: quien trae {@code admin.full} puede todo. Con esa regla,
 * preguntar "puede hacer X" no exige conocer todas las X posibles. Cada ruta
 * nombra literalmente la autoridad que exige, igual que cada operacion del
 * backend nombra la suya en su anotacion.
 *
 * <p>Que esos literales existan de verdad lo comprueba una prueba contra el
 * realm, del mismo modo que el backend comprueba su vocabulario contra el
 * mismo archivo.
 */

/** Client de Keycloak cuyos roles son las autoridades de esta aplicacion. */
export const CLIENT_API = 'malphasos-api';

/**
 * La autoridad que expande a todas las demas.
 *
 * <p>El backend la aplica una sola vez, al convertir el token en autoridades.
 * Aqui se aplica tambien una sola vez, en {@code Sesion.puede}.
 */
export const ADMINISTRADOR = 'admin.full';

/**
 * El escalón de arriba: el SuperUsuario, que se otorga a mano en Keycloak.
 *
 * <p>Concede lo mismo que {@link ADMINISTRADOR} y además las autoridades con el prefijo
 * {@link PREFIJO_SUPER}, como {@code ApiAuthority.expand} en el backend. Entró aquí el 2026-11-08: hasta
 * entonces el frontend solo conocía el primer escalón y le ofrecía a un administrador las pantallas del
 * SuperUsuario, que el servidor le respondía con un 403.
 */
export const SUPER_ADMINISTRADOR = 'super.admin.full';

/**
 * Lo que marca una autoridad que {@link ADMINISTRADOR} no concede. Es la convención del backend
 * —«el prefijo {@code super.} marca lo que {@code admin.full} no concede»—, y por eso basta el prefijo
 * para no copiar aquí la lista de esas autoridades.
 */
export const PREFIJO_SUPER = 'super.';

/** Forma del claim del que salen las autoridades. */
export interface TokenConRoles {
  readonly resource_access?: Readonly<Record<string, { readonly roles?: readonly string[] }>>;
}

/**
 * Las autoridades que un token concede, sin expandir.
 *
 * <p>El token es una entrada externa: cualquier desviacion de la forma esperada
 * se resuelve devolviendo un conjunto vacio, nunca lanzando. Es la misma
 * decision que toma {@code KeycloakRoleConverter} en el backend.
 */
export function autoridadesDe(token: TokenConRoles | undefined): ReadonlySet<string> {
  const roles = token?.resource_access?.[CLIENT_API]?.roles;

  return new Set(Array.isArray(roles) ? roles.filter((r) => typeof r === 'string') : []);
}
