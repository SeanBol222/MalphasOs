/**
 * Dónde están el API y Keycloak.
 *
 * <p><b>Desde el 2026-10-17 no se decide al compilar sino al arrancar</b>: antes de pintar nada, la
 * aplicación lee {@code /config.json} y pone aquí lo que diga —ver {@code cargarConfiguracion}—. Así una
 * misma imagen sirve en cualquier dominio. Hasta ese día el build de producción llevaba {@code localhost}
 * compilado, y desplegarlo exigía recompilar.
 *
 * <p>Los valores de aquí son los de desarrollo, y son los que usan las pruebas. Obviamente locales a
 * propósito: un valor inventado que pareciera de producción sería peor.
 *
 * <p>Había dos archivos, uno por configuración del build, que solo se diferenciaban en un indicador que
 * nadie leía. Con la configuración al arrancar, uno basta.
 */
export interface Entorno {
  /** El origen del API, sin barra final: las rutas empiezan por {@code /v1/api}. */
  api: string;
  keycloak: {
    url: string;
    realm: string;
    clientId: string;
  };
}

export const environment: Entorno = {
  api: 'http://localhost:8081',
  keycloak: {
    url: 'http://localhost:8080',
    realm: 'malphasos-realm',
    clientId: 'malphasos-frontend',
  },
};
