/**
 * Configuracion de desarrollo.
 *
 * <p>El realm, el client y las URL salen de {@code docker/keycloak/import/} y de
 * la configuracion del backend, no de una suposicion. El client
 * {@code malphasos-frontend} es publico y con PKCE S256, que es lo correcto
 * para una aplicacion de pagina unica.
 */
export const environment = {
  produccion: false,
  api: 'http://localhost:8081',
  keycloak: {
    url: 'http://localhost:8080',
    realm: 'malphasos-realm',
    clientId: 'malphasos-frontend',
  },
} as const;
