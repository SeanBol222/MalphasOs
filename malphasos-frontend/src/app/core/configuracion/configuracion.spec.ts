import { Entorno } from '../../../environments/environment';
import { cargarConfiguracion } from './configuracion';

/** La configuración se lee al arrancar, de /config.json (2026-10-17). */
describe('La configuración al arrancar', () => {
  const vacio = (): Entorno => ({ api: '', keycloak: { url: '', realm: '', clientId: '' } });
  const responde = (cuerpo: unknown, status = 200): typeof fetch =>
    (async () => new Response(JSON.stringify(cuerpo), { status })) as typeof fetch;

  const PRODUCCION = {
    api: 'https://malphasos.example/',
    keycloak: {
      url: 'https://malphasos.example/auth/',
      realm: 'malphasos-realm',
      clientId: 'malphasos-frontend',
    },
  };

  it('pone lo que dice el archivo, sin barras finales', async () => {
    const destino = vacio();

    await cargarConfiguracion(responde(PRODUCCION), destino);

    expect(destino).toEqual({
      api: 'https://malphasos.example',
      keycloak: {
        url: 'https://malphasos.example/auth',
        realm: 'malphasos-realm',
        clientId: 'malphasos-frontend',
      },
    });
  });

  it('la pide sin caché: un despliegue nuevo con otra dirección se ve al recargar', async () => {
    let pedida: [RequestInfo | URL, RequestInit | undefined] | undefined;
    const pedir = (async (entrada: RequestInfo | URL, opciones?: RequestInit) => {
      pedida = [entrada, opciones];
      return new Response(JSON.stringify(PRODUCCION));
    }) as typeof fetch;

    await cargarConfiguracion(pedir, vacio());

    expect(pedida).toEqual(['/config.json', { cache: 'no-store' }]);
  });

  it('sin el archivo falla en vez de seguir con los valores de desarrollo', async () => {
    const destino = vacio();

    await expect(cargarConfiguracion(responde({}, 404), destino)).rejects.toThrow('respondió 404');
    expect(destino.api).toBe('');
  });

  it('con un campo que falta o en blanco, falla diciendo cuál', async () => {
    await expect(
      cargarConfiguracion(responde({ ...PRODUCCION, api: '  ' }), vacio()),
    ).rejects.toThrow('/config.json no dice api');
    await expect(
      cargarConfiguracion(
        responde({ ...PRODUCCION, keycloak: { ...PRODUCCION.keycloak, realm: undefined } }),
        vacio(),
      ),
    ).rejects.toThrow('/config.json no dice keycloak.realm');
    await expect(cargarConfiguracion(responde(null), vacio())).rejects.toThrow(
      '/config.json no dice api',
    );
  });
});
