import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  injectQueryClient,
} from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CambioDeSede, NuevaSede, Sede } from '../../core/api/tipos';
import { ClienteApi } from './cliente-api';

/**
 * La unica puerta al servidor para el agregado sede.
 *
 * <p>Va aparte de {@link ClienteApi} porque son dos agregados, igual que en el backend, donde
 * {@code HeadquarterService} es una clase distinta de {@code ClientService}.
 *
 * <p><b>Las rutas no son simetricas y eso es del backend, no de aqui</b>: las sedes de un cliente se
 * consultan y se crean bajo {@code /clients/{id}/headquarters}, porque una sede no existe sin su
 * cliente; pero una vez creada se lee, se edita y se cierra en {@code /headquarters/{id}}, porque su
 * identificador ya la determina. Repetir el cliente en esas tres rutas seria informacion que el
 * servidor ya tiene y una ocasion de que las dos partes no coincidan.
 *
 * <p>Toda escritura invalida las dos ramas de la cache: la del cliente —de la que cuelga su lista de
 * sedes— y la de las sedes. Cerrar una sede cambia lo que se ve en las dos pantallas.
 */
@Injectable({ providedIn: 'root' })
export class SedeApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly urlClientes = `${environment.api}/v1/api/clients`;
  private readonly url = `${environment.api}/v1/api/headquarters`;

  static readonly CLAVE = ['sedes'] as const;

  static claveDeCliente(idCliente: string) {
    return [...ClienteApi.claveDetalle(idCliente), 'sedes'] as const;
  }

  static claveDetalle(id: string) {
    return [...SedeApi.CLAVE, id] as const;
  }

  listarDe(idCliente: Signal<string>) {
    return injectQuery(() => ({
      queryKey: SedeApi.claveDeCliente(idCliente()),
      queryFn: () =>
        firstValueFrom(this.http.get<Sede[]>(`${this.urlClientes}/${idCliente()}/headquarters`)),
      // Igual que en las areas: sin cliente elegido no hay nada que pedir.
      enabled: !!idCliente(),
    }));
  }

  detalle(id: Signal<string>) {
    return injectQuery(() => ({
      queryKey: SedeApi.claveDetalle(id()),
      queryFn: () => firstValueFrom(this.http.get<Sede>(`${this.url}/${id()}`)),
    }));
  }

  crear() {
    return injectMutation(() => ({
      mutationFn: ({ idCliente, sede }: { idCliente: string; sede: NuevaSede }) =>
        firstValueFrom(
          this.http.post<Sede>(`${this.urlClientes}/${idCliente}/headquarters`, sede),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  editar() {
    return injectMutation(() => ({
      mutationFn: ({ id, cambio }: { id: string; cambio: CambioDeSede }) =>
        firstValueFrom(this.http.patch<Sede>(`${this.url}/${id}`, cambio)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Cierra la sede.
   *
   * <p>Se llama cerrar y no borrar porque es lo que ocurre: la sede queda inactiva con su historia
   * intacta. El backend ademas no deja abrir areas en una sede cerrada, asi que cerrarla es una
   * decision con consecuencias, no una limpieza.
   */
  cerrar() {
    return injectMutation(() => ({
      mutationFn: (id: string) => firstValueFrom(this.http.delete<void>(`${this.url}/${id}`)),
      onSuccess: () => this.invalidar(),
    }));
  }

  private async invalidar(): Promise<void> {
    await Promise.all([
      this.queryClient.invalidateQueries({ queryKey: ClienteApi.CLAVE_LISTA }),
      this.queryClient.invalidateQueries({ queryKey: SedeApi.CLAVE }),
    ]);
  }
}
