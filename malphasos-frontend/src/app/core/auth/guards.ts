import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { createAuthGuard } from 'keycloak-angular';
import { autoridadesDe, ADMINISTRADOR } from './autoridades';

/**
 * Exige sesion iniciada. Si no la hay, manda a Keycloak y vuelve al destino.
 *
 * <p>No hay pagina de inicio de sesion propia: el cliente es publico y quien
 * pide credenciales es Keycloak. Una pantalla local que recogiera usuario y
 * contrasena para pasarselos seria justo lo que el flujo de codigo de
 * autorizacion existe para evitar.
 */
export const sesionIniciada: CanActivateFn = createAuthGuard(
  async (_ruta, estado, { authenticated, keycloak }) => {
    if (authenticated) {
      return true;
    }

    await keycloak.login({ redirectUri: new URL(estado.url, location.origin).href });

    return false;
  },
);

/**
 * Exige una autoridad concreta, con el mismo literal que el backend.
 *
 * <p>Las autoridades se leen del token con {@code autoridadesDe} y no de lo que
 * ofrece la libreria, para que el frontend tenga <b>un solo sitio</b> donde
 * interpreta el claim. Y la regla del administrador se aplica aqui igual que en
 * {@code Sesion.puede}: {@code admin.full} expande a todo.
 *
 * <p><b>Esto no protege la operacion</b>, solo evita enseñar una pantalla que
 * el servidor rechazaria. El permiso lo comprueba el backend en cada llamada.
 */
export function requiereAutoridad(autoridad: string): CanActivateFn {
  return createAuthGuard(async (_ruta, estado, { authenticated, keycloak }) => {
    if (!authenticated) {
      await keycloak.login({ redirectUri: new URL(estado.url, location.origin).href });

      return false;
    }

    const autoridades = autoridadesDe(keycloak.tokenParsed);

    if (autoridades.has(ADMINISTRADOR) || autoridades.has(autoridad)) {
      return true;
    }

    return inject(Router).parseUrl('/sin-permiso');
  });
}
