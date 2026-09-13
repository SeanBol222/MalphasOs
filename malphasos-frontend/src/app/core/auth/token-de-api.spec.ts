import { tokenSoloHaciaElApi } from './token-de-api';
import { environment } from '../../../environments/environment';

/**
 * Donde viaja el token y donde no.
 *
 * <p>Es la prueba de seguridad de esta capa. Un interceptor con un patron
 * demasiado amplio entrega el token de acceso al primer servicio de terceros al
 * que la aplicacion llame, y eso no produce ningun error visible.
 */
describe('El token solo viaja hacia el API', () => {
  const casa = environment.api;

  it.each([
    `${casa}/v1/api/work-orders`,
    `${casa}/v1/api/clients/123`,
    `${casa}/v1/api`,
  ])('se adjunta a %s', (url) => {
    expect(tokenSoloHaciaElApi.urlPattern.test(url)).toBe(true);
  });

  it.each([
    ['otro host que empieza igual', 'http://localhost:8081.malicioso.example/v1/api/clients'],
    ['un tercero cualquiera', 'https://api.tercero.example/v1/api/clients'],
    ['el propio Keycloak', 'http://localhost:8080/realms/malphasos-realm/protocol/openid-connect/token'],
    ['una ruta del mismo host fuera del API', `${casa}/actuator/health`],
    ['un prefijo que solo se parece', `${casa}/v1/apix/clients`],
  ])('NO se adjunta a %s', (_caso, url) => {
    expect(tokenSoloHaciaElApi.urlPattern.test(url)).toBe(false);
  });
});
