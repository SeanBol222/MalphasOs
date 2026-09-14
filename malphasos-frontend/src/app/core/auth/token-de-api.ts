import { createInterceptorCondition, IncludeBearerTokenCondition } from 'keycloak-angular';
import { environment } from '../../../environments/environment';

/**
 * Cuando se adjunta el token a una peticion.
 *
 * <p><b>Solo hacia el API de MalphasOS.</b> La condicion es explicita y no un
 * comodin a proposito: un interceptor que adjunte el token a cualquier destino
 * lo entrega al primer servicio de terceros al que la aplicacion llame.
 */
export const tokenSoloHaciaElApi = createInterceptorCondition<IncludeBearerTokenCondition>({
  urlPattern: new RegExp(`^${escaparParaRegExp(environment.api)}/v1/api(/|$)`, 'i'),
});

function escaparParaRegExp(texto: string): string {
  return texto.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
