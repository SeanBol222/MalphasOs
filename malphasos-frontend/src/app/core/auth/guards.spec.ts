import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import Keycloak from 'keycloak-js';
import { proveerSesionFalsa, KeycloakFalso } from '../../../testing/keycloak-falso';
import { Inicio } from '../../features/home/inicio';
import { requiereAutoridad, sesionIniciada } from './guards';

const RUTAS = [
  { path: 'abierta', component: Inicio, canActivate: [sesionIniciada] },
  {
    path: 'restringida',
    component: Inicio,
    canActivate: [requiereAutoridad('work-order.write')],
  },
  { path: 'sin-permiso', component: Inicio },
];

async function navegar(destino: string, sesion: Parameters<typeof proveerSesionFalsa>[0]) {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideRouter(RUTAS), ...proveerSesionFalsa(sesion)],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(destino).catch(() => undefined);

  return {
    url: TestBed.inject(Router).url,
    keycloak: TestBed.inject(Keycloak) as KeycloakFalso,
  };
}

describe('Guards', () => {
  describe('Exigir sesión', () => {
    it('deja pasar a quien ya entró', async () => {
      const { url } = await navegar('/abierta', { autenticado: true });

      expect(url).toBe('/abierta');
    });

    it('manda a Keycloak a quien no, y pide volver al destino', async () => {
      // Sin el redirectUri, entrar te devuelve a la portada y pierdes lo que
      // ibas a ver. Es el detalle que hace usable un inicio de sesion.
      const { keycloak } = await navegar('/abierta', { autenticado: false });

      expect(keycloak.entradasPedidas).toHaveLength(1);
      expect(keycloak.entradasPedidas[0].redirectUri).toContain('/abierta');
    });
  });

  describe('Exigir una autoridad', () => {
    it('deja pasar a quien la trae', async () => {
      const { url } = await navegar('/restringida', {
        autenticado: true,
        autoridades: ['work-order.write'],
      });

      expect(url).toBe('/restringida');
    });

    it('manda a sin-permiso a quien entró pero no la trae', async () => {
      const { url } = await navegar('/restringida', {
        autenticado: true,
        autoridades: ['work-order.read'],
      });

      expect(url).toBe('/sin-permiso');
    });

    it('aplica la expansión del administrador igual que el backend', async () => {
      const { url } = await navegar('/restringida', {
        autenticado: true,
        autoridades: ['admin.full'],
      });

      expect(url).toBe('/restringida');
    });

    it('a quien no ha entrado lo manda a Keycloak, no a sin-permiso', async () => {
      // Confundir "no has entrado" con "no puedes" es el error facil aqui:
      // enseñaria un rechazo a quien solo tenia que identificarse.
      const { keycloak, url } = await navegar('/restringida', { autenticado: false });

      expect(keycloak.entradasPedidas).toHaveLength(1);
      expect(url).not.toBe('/sin-permiso');
    });
  });
});
