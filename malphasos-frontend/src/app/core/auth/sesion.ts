import { computed, effect, inject, Injectable, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { KEYCLOAK_EVENT_SIGNAL, typeEventArgs, ReadyArgs, KeycloakEventType } from 'keycloak-angular';
import { ADMINISTRADOR, autoridadesDe } from './autoridades';

/**
 * Quien esta dentro y que puede hacer.
 *
 * <p><b>Este servicio no autoriza: informa.</b> Ocultar un boton mejora la
 * experiencia; el permiso lo comprueba el servidor en cada llamada. Un frontend
 * que "protege" una operacion no protege nada.
 */
@Injectable({ providedIn: 'root' })
export class Sesion {
  private readonly keycloak = inject(Keycloak);
  private readonly evento = inject(KEYCLOAK_EVENT_SIGNAL);

  private readonly estado = signal(this.leerEstado());

  readonly autenticado = computed(() => this.estado().autenticado);
  readonly autoridades = computed(() => this.estado().autoridades);

  /** Nombre para mostrar. Cae al usuario si el token no trae nombre completo. */
  readonly nombre = computed(() => this.estado().nombre);

  /** El nombre de usuario con el que entra, que no siempre coincide con el que se muestra. */
  readonly usuario = computed(() => this.estado().usuario);

  /**
   * El identificador de quien esta dentro, que es tambien el de su fila en {@code persona}: el backend
   * da a cada persona el identificador que le asigna Keycloak. Es lo que permite pedir «mis ordenes»
   * sin una consulta previa. Vacio sin sesion.
   */
  readonly identificador = computed(() => this.estado().identificador);

  constructor() {
    // Cada evento de Keycloak -listo, token renovado, sesion terminada- puede
    // cambiar quien esta dentro. Releer es mas barato que mantener un espejo.
    effect(() => {
      typeEventArgs<ReadyArgs>(this.evento().args);
      this.estado.set(this.leerEstado());
    });
  }

  /**
   * Si la sesion actual alcanza para una autoridad.
   *
   * <p>Aqui vive la unica regla que el frontend conoce del modelo de permisos:
   * {@code admin.full} expande a todo. Por eso no hace falta enumerar las 21.
   */
  puede(autoridad: string): boolean {
    const autoridades = this.autoridades();

    return autoridades.has(ADMINISTRADOR) || autoridades.has(autoridad);
  }

  entrar(): Promise<void> {
    return this.keycloak.login();
  }

  /**
   * Lleva a Keycloak a cambiar la contraseña y vuelve aquí (2026-10-17). Es una acción de Keycloak
   * —`UPDATE_PASSWORD`—, con la página de su tema: la aplicación nunca ve la contraseña.
   */
  cambiarContrasena(): Promise<void> {
    return this.keycloak.login({ action: 'UPDATE_PASSWORD' });
  }

  salir(): Promise<void> {
    return this.keycloak.logout();
  }

  private leerEstado() {
    const token = this.keycloak.tokenParsed;

    return {
      autenticado: this.keycloak.authenticated ?? false,
      autoridades: autoridadesDe(token),
      nombre: token?.['name'] ?? token?.['preferred_username'] ?? '',
      usuario: (token?.['preferred_username'] as string | undefined) ?? '',
      identificador: (token?.['sub'] as string | undefined) ?? '',
    };
  }
}

/** Solo para que el tipo del evento no se pierda al no usarse. */
export type { KeycloakEventType };
