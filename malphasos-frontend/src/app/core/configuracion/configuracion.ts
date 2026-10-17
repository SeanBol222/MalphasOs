import { Entorno, environment } from '../../../environments/environment';

/**
 * Lee {@code /config.json} y lo pone en {@link environment}, antes de que arranque la aplicación
 * (2026-10-17). Es lo que hace que una misma imagen sirva en cualquier dominio: el contenedor escribe ese
 * archivo al arrancar desde sus variables de entorno, y en desarrollo lo sirve {@code public/}.
 *
 * <p><b>Falla en voz alta</b>: sin el archivo, o con un campo que falta, lanza en vez de seguir con los
 * valores de desarrollo. Arrancar en producción apuntando a {@code localhost} sería una página que no
 * funciona sin decir por qué.
 */
export async function cargarConfiguracion(
  pedir: typeof fetch = (entrada, opciones) => fetch(entrada, opciones),
  destino: Entorno = environment,
): Promise<void> {
  const respuesta = await pedir('/config.json', { cache: 'no-store' });
  if (!respuesta.ok) {
    throw new Error(`No se pudo leer /config.json: respondió ${respuesta.status}`);
  }

  const leida = (await respuesta.json()) as Partial<Entorno> | null;
  const api = texto(leida?.api, 'api');
  const keycloak = leida?.keycloak;

  destino.api = sinBarraFinal(api);
  destino.keycloak = {
    url: sinBarraFinal(texto(keycloak?.url, 'keycloak.url')),
    realm: texto(keycloak?.realm, 'keycloak.realm'),
    clientId: texto(keycloak?.clientId, 'keycloak.clientId'),
  };
}

function texto(valor: unknown, campo: string): string {
  if (typeof valor !== 'string' || !valor.trim()) {
    throw new Error(`/config.json no dice ${campo}`);
  }

  return valor.trim();
}

/** «https://ejemplo/» y «https://ejemplo» son lo mismo, y las rutas ya llevan su barra. */
function sinBarraFinal(url: string): string {
  return url.replace(/\/+$/, '');
}
