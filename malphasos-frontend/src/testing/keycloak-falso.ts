import { Provider, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { KEYCLOAK_EVENT_SIGNAL, KeycloakEvent, KeycloakEventType } from 'keycloak-angular';
import { CLIENT_API } from '../app/core/auth/autoridades';

export interface SesionFalsa {
  readonly autenticado?: boolean;
  readonly autoridades?: readonly string[];
  readonly nombre?: string;
}

/** Lo que las pruebas pueden mirar del Keycloak de mentira. */
export interface KeycloakFalso extends Keycloak {
  readonly entradasPedidas: { redirectUri?: string }[];
  readonly salidasPedidas: number;
}

/**
 * Un Keycloak de mentira, para no hablar con un servidor en las pruebas.
 *
 * <p>Construye el token con la misma forma que Keycloak emite de verdad
 * -{@code resource_access[client].roles}-, de modo que lo que se prueba es el
 * codigo que lo interpreta y no un atajo.
 */
export function proveerSesionFalsa(sesion: SesionFalsa = {}): Provider[] {
  const autenticado = sesion.autenticado ?? true;
  const entradasPedidas: { redirectUri?: string }[] = [];
  let salidasPedidas = 0;

  const falso = {
    authenticated: autenticado,
    tokenParsed: autenticado
      ? {
          name: sesion.nombre ?? 'Sean Bolívar',
          preferred_username: 'sbolivar',
          resource_access: { [CLIENT_API]: { roles: [...(sesion.autoridades ?? [])] } },
        }
      : undefined,
    login: (opciones?: { redirectUri?: string }) => {
      entradasPedidas.push(opciones ?? {});

      return Promise.resolve();
    },
    logout: () => {
      salidasPedidas += 1;

      return Promise.resolve();
    },
    get entradasPedidas() {
      return entradasPedidas;
    },
    get salidasPedidas() {
      return salidasPedidas;
    },
  } as unknown as KeycloakFalso;

  return [
    { provide: Keycloak, useValue: falso },
    {
      provide: KEYCLOAK_EVENT_SIGNAL,
      useValue: signal<KeycloakEvent>({ type: KeycloakEventType.Ready, args: autenticado }),
    },
  ];
}
