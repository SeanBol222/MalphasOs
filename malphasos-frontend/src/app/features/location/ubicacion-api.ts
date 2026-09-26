import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Ciudad, Pais } from '../../core/api/tipos';

/**
 * La unica puerta al servidor para paises y ciudades.
 *
 * <p><b>Solo lectura, y es una decision.</b> El modulo publica alta, edicion y baja de los dos, pero
 * quien registra un cliente no crea paises: los elige. Ofrecer «crear ciudad» dentro del alta de una
 * sede invitaria a duplicar Bogota tres veces con tres grafias. Cuando haga falta administrar el
 * catalogo sera su propia pantalla, con su propia autoridad.
 *
 * <p>Las dos listas se cachean mas tiempo que el resto: un catalogo de paises no cambia mientras
 * alguien rellena un formulario, y volver a pedirlo en cada pantalla que lo necesita es trafico sin
 * ninguna informacion nueva.
 */
@Injectable({ providedIn: 'root' })
export class UbicacionApi {
  private readonly http = inject(HttpClient);

  private readonly urlPaises = `${environment.api}/v1/api/countries`;
  private readonly urlCiudades = `${environment.api}/v1/api/cities`;

  static readonly CLAVE_PAISES = ['paises'] as const;
  static readonly CLAVE_CIUDADES = ['ciudades'] as const;

  /** Cinco minutos. Suficiente para una sesion de trabajo, corto para notar una correccion. */
  private static readonly VIGENCIA = 5 * 60 * 1000;

  listarPaises() {
    return injectQuery(() => ({
      queryKey: UbicacionApi.CLAVE_PAISES,
      queryFn: () => firstValueFrom(this.http.get<Pais[]>(this.urlPaises)),
      staleTime: UbicacionApi.VIGENCIA,
    }));
  }

  listarCiudades() {
    return injectQuery(() => ({
      queryKey: UbicacionApi.CLAVE_CIUDADES,
      queryFn: () => firstValueFrom(this.http.get<Ciudad[]>(this.urlCiudades)),
      staleTime: UbicacionApi.VIGENCIA,
    }));
  }
}
