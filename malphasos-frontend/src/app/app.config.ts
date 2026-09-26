import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { provideRouter } from '@angular/router';
import {
  AutoRefreshTokenService,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  includeBearerTokenInterceptor,
  provideKeycloak,
  UserActivityService,
  withAutoRefreshToken,
} from 'keycloak-angular';
import { routes } from './app.routes';
import { environment } from '../environments/environment';
import { tokenSoloHaciaElApi } from './core/auth/token-de-api';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideKeycloak({
      config: environment.keycloak,
      initOptions: {
        // Flujo de codigo de autorizacion con PKCE: lo correcto para un cliente
        // publico. El realm ya declara S256 para malphasos-frontend.
        pkceMethod: 'S256',
        // Comprueba si ya hay sesion sin sacar al usuario de la aplicacion.
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: `${location.origin}/silent-check-sso.html`,
      },
      features: [
        // El backend emite tokens de 300 s. Se renueva antes de que caduquen y
        // se cierra la sesion si la renovacion falla: un token muerto en mano
        // produce 401 en cada llamada y ninguna explicacion.
        withAutoRefreshToken({ onInactivityTimeout: 'logout', sessionTimeout: 300_000 }),
      ],
      providers: [
        { provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG, useValue: [tokenSoloHaciaElApi] },
        // withAutoRefreshToken los exige y ninguno es providedIn: 'root'. Sin ellos la
        // aplicacion no arranca: el inyector falla antes de pintar nada y la pagina queda en
        // blanco sin redirigir. Lo dice el README de la libreria; no leerlo costo este fallo.
        AutoRefreshTokenService,
        UserActivityService,
      ],
    }),
    provideHttpClient(withInterceptors([includeBearerTokenInterceptor])),
    // La cache que ataca los umbrales de dos segundos de la ERS: una lista ya vista no se vuelve a
    // pedir. Los reintentos van a uno: con el token caducado, reintentar tres veces solo retrasa
    // el momento en que el usuario se entera.
    provideTanStackQuery(
      new QueryClient({
        defaultOptions: { queries: { staleTime: 30_000, retry: 1 } },
      }),
    ),
  ],
};
