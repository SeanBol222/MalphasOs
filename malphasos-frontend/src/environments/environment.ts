/**
 * Configuracion de produccion.
 *
 * <p>Los valores reales los pone quien despliega. Los de aqui son los de
 * desarrollo a proposito: un valor inventado que pareciera de produccion seria
 * peor que uno obviamente local.
 */
export const environment = {
  produccion: true,
  api: 'http://localhost:8081',
  keycloak: {
    url: 'http://localhost:8080',
    realm: 'malphasos-realm',
    clientId: 'malphasos-frontend',
  },
} as const;
