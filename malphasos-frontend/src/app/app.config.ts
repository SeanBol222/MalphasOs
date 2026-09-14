import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import {
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  includeBearerTokenInterceptor,
  provideKeycloak,
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
      ],
    }),
    provideHttpClient(withInterceptors([includeBearerTokenInterceptor])),
  ],
};
