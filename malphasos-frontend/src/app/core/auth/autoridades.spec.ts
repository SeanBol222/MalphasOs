import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { existsSync } from 'node:fs';
import { ADMINISTRADOR, CLIENT_API, autoridadesDe } from './autoridades';

describe('Autoridades', () => {
  describe('Leer el token', () => {
    it('toma los roles del client de la API y no los de otros clients', () => {
      const token = {
        resource_access: {
          [CLIENT_API]: { roles: ['work-order.read', 'client.read'] },
          account: { roles: ['manage-account'] },
        },
      };

      expect([...autoridadesDe(token)]).toEqual(['work-order.read', 'client.read']);
    });

    it.each([
      ['sin token', undefined],
      ['sin resource_access', {}],
      ['sin el client de la API', { resource_access: { account: { roles: ['x'] } } }],
      ['sin roles', { resource_access: { [CLIENT_API]: {} } }],
      ['con roles que no son lista', { resource_access: { [CLIENT_API]: { roles: 'x' } } }],
    ])('devuelve vacio %s, sin lanzar', (_caso, token) => {
      // El token es una entrada externa. La misma decision que toma
      // KeycloakRoleConverter en el backend: forma inesperada, cero
      // autoridades, nunca una excepcion.
      expect(autoridadesDe(token as never).size).toBe(0);
    });

    it('descarta lo que no sea texto dentro de la lista de roles', () => {
      const token = { resource_access: { [CLIENT_API]: { roles: ['client.read', 7, null] } } };

      expect([...autoridadesDe(token as never)]).toEqual(['client.read']);
    });
  });

  describe('Contrato con el realm', () => {
    // Es el equivalente de RealmAuthorityContractTest: el vocabulario no se
    // supone, se comprueba contra el archivo que Keycloak importa de verdad.
    const realm = leerRealm();

    it('el client del que se leen los roles existe en el realm', () => {
      const clients = realm.clients.map((c) => c.clientId);

      expect(clients).toContain(CLIENT_API);
    });

    it('admin.full es un rol real de ese client, no un literal inventado aqui', () => {
      // Es la unica autoridad que el frontend nombra por su cuenta: la regla de
      // expansion depende de ella. Si el realm la renombrara, esto lo dice.
      const roles = realm.roles.client[CLIENT_API].map((r) => r.name);

      expect(roles).toContain(ADMINISTRADOR);
    });

    it('toda autoridad que una ruta exige es un rol real del realm', () => {
      // El agujero que esta prueba tapa se abrio de verdad: al escribir las rutas de las sedes se
      // pusieron 'headquarter.read' y 'headquarter.write', que no existen —las sedes las protege
      // 'client.*'—. Nada fallaba: el guard mandaba a /sin-permiso y la pantalla quedaba
      // inalcanzable para todo el mundo, incluido el administrador. Es el mismo riesgo que el
      // backend persigue con RestAuthorizationCoverageTest, en el otro extremo de la linea.
      const roles = realm.roles.client[CLIENT_API].map((r) => r.name);

      for (const autoridad of autoridadesExigidasPorLasRutas()) {
        expect(roles, `la ruta exige "${autoridad}", que el realm no concede`).toContain(autoridad);
      }
    });

    it('las rutas exigen alguna autoridad: si esto da cero, la prueba de arriba no mira nada', () => {
      // Una prueba que recorre una lista vacia pasa siempre. Este proyecto ya tiene escrito lo que
      // cuesta una prueba que pasa en vacio, y no vale la pena repetirlo.
      expect(autoridadesExigidasPorLasRutas().length).toBeGreaterThan(0);
    });

    it('el client publico del frontend admite el puerto en el que se sirve', () => {
      // El realm apunta al 5173, que es donde el servidor de desarrollo escucha
      // porque angular.json se ajusto a el. Si alguien cambia el puerto sin
      // tocar el realm, el retorno de Keycloak deja de funcionar.
      const frontend = realm.clients.find((c) => c.clientId === 'malphasos-frontend')!;

      expect(frontend.publicClient).toBe(true);
      expect(frontend.attributes['pkce.code.challenge.method']).toBe('S256');
      expect(frontend.redirectUris.some((u) => u.includes(':5173'))).toBe(true);
    });
  });
});

// ---------------------------------------------------------------------------

/**
 * Las autoridades que las rutas nombran, leidas del propio archivo de rutas.
 *
 * <p>Se lee el fuente en vez de importar {@code routes} porque lo que hay que comprobar es el
 * <b>literal escrito</b>: importarlo obligaria a ejecutar cada {@code loadComponent} para llegar a
 * los guards, y un guard es una funcion cerrada sobre su cadena, de la que no se puede recuperar.
 */
function autoridadesExigidasPorLasRutas(): string[] {
  const fuente = readFileSync(resolve(process.cwd(), 'src/app/app.routes.ts'), 'utf8');

  return [...fuente.matchAll(/requiereAutoridad\('([^']+)'\)/g)].map(([, autoridad]) => autoridad);
}

interface Realm {
  clients: { clientId: string; publicClient: boolean; redirectUris: string[];
    attributes: Record<string, string> }[];
  roles: { client: Record<string, { name: string }[]> };
}

/**
 * El realm de desarrollo, buscado subiendo desde el directorio de trabajo.
 *
 * <p>Vive fuera de este proyecto, en {@code docker/}, que es territorio de solo
 * lectura. Se localiza subiendo porque el directorio de trabajo no es el mismo
 * al lanzar la bateria desde la raiz del repositorio que desde aqui — misma
 * razon y misma solucion que {@code RealmFixture} en el backend.
 */
function leerRealm(): Realm {
  const RUTA = 'docker/keycloak/import/malphasos-realm-realm.json';
  let directorio = resolve(process.cwd());

  while (true) {
    const candidato = join(directorio, RUTA);
    if (existsSync(candidato)) {
      return JSON.parse(readFileSync(candidato, 'utf8')) as Realm;
    }

    const padre = dirname(directorio);
    if (padre === directorio) {
      throw new Error(`No se encontro ${RUTA} subiendo desde ${process.cwd()}`);
    }
    directorio = padre;
  }
}
