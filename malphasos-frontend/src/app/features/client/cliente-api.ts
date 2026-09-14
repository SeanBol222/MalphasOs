import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  injectQueryClient,
} from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Cliente, NuevoCliente } from '../../core/api/tipos';

/**
 * La unica puerta al servidor para el modulo de clientes.
 *
 * <p><b>Ningun componente llama al API directamente.</b> Aqui, y solo aqui, vive TanStack Query,
 * cuyo adaptador de Angular se distribuye con el sufijo {@code experimental} en el nombre del
 * paquete. Si su API cambia entre versiones, lo que se toca son estos servicios y no cada pantalla.
 *
 * <p>Es el espejo de un puerto de entrada del backend: lo que alli es
 * {@code ClientServicePort}, aqui es esto.
 */
@Injectable({ providedIn: 'root' })
export class ClienteApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly url = `${environment.api}/v1/api/clients`;

  /** Clave de cache de la lista. Se declara una vez para que invalidar no dependa de recordarla. */
  static readonly CLAVE_LISTA = ['clientes'] as const;

  listar() {
    return injectQuery(() => ({
      queryKey: ClienteApi.CLAVE_LISTA,
      queryFn: () => firstValueFrom(this.http.get<Cliente[]>(this.url)),
    }));
  }

  /**
   * Da de alta un cliente e invalida la lista.
   *
   * <p>Invalidar no es un detalle: sin eso, volver al listado tras crear muestra la cache vieja y
   * el cliente recien creado no aparece. Es el defecto clasico de dos pantallas que miran la misma
   * lista y solo una se entera.
   */
  crear() {
    return injectMutation(() => ({
      mutationFn: (cliente: NuevoCliente) =>
        firstValueFrom(this.http.post<Cliente>(this.url, cliente)),
      onSuccess: () => this.queryClient.invalidateQueries({ queryKey: ClienteApi.CLAVE_LISTA }),
    }));
  }
}
