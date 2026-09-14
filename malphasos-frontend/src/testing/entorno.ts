import { EnvironmentProviders, Provider } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

/**
 * Lo que una pantalla necesita para probarse contra un API simulado.
 *
 * <p>Se usa {@code HttpTestingController} de Angular y no una libreria de intercepcion de red. La
 * declaracion de diseno hablaba de «un API simulado»; esta es la forma que el propio framework
 * ofrece, sin dependencia extra, y da control exacto sobre el cuerpo del error — que es lo que
 * estas pruebas necesitan, porque responden con los <b>codigos reales</b> del backend.
 *
 * <p>Sin reintentos y sin cache entre pruebas: un reintento deja la prueba esperando y una cache
 * compartida hace que el orden de ejecucion cambie el resultado.
 */
export function proveerApiSimulado(): (Provider | EnvironmentProviders)[] {
  return [
    provideHttpClient(),
    provideHttpClientTesting(),
    provideTanStackQuery(
      new QueryClient({
        defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
      }),
    ),
  ];
}
