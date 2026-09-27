import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  injectQueryClient,
} from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AreaDeServicio, CambioDeArea, NuevaArea } from '../../core/api/tipos';
import { SedeApi } from './sede-api';

/**
 * La unica puerta al servidor para el agregado area de servicio.
 *
 * <p>Un area es lo que da sentido a todo lo anterior: <b>los equipos no cuelgan del cliente ni de la
 * sede, sino del area</b>, y una orden de trabajo solo puede tocar equipos de areas de su propia
 * sede. Sin esta pantalla no hay donde registrar un equipo.
 *
 * <p>Mismo reparto de rutas que en las sedes, y por el mismo motivo: bajo la sede para listar y
 * crear, por su propio identificador para editar y cerrar.
 */
@Injectable({ providedIn: 'root' })
export class AreaApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly urlSedes = `${environment.api}/v1/api/headquarters`;
  private readonly url = `${environment.api}/v1/api/service-areas`;

  static claveDeSede(idSede: string) {
    return [...SedeApi.claveDetalle(idSede), 'areas'] as const;
  }

  listarDe(idSede: Signal<string>) {
    return injectQuery(() => ({
      queryKey: AreaApi.claveDeSede(idSede()),
      queryFn: () =>
        firstValueFrom(
          this.http.get<AreaDeServicio[]>(`${this.urlSedes}/${idSede()}/service-areas`),
        ),
      // Sin sede elegida no se consulta nada: la usan formularios encadenados, donde el primer
      // desplegable esta vacio hasta que alguien elige. Sin esto se pediria /headquarters//...
      enabled: !!idSede(),
    }));
  }

  /**
   * Un area por su identificador.
   *
   * <p>La necesita la pantalla de equipos de un area, que llega por la ruta del area y no desde su
   * sede: sin esto habria que traerse la lista de la sede para sacar un nombre.
   */
  detalle(id: Signal<string>) {
    return injectQuery(() => ({
      queryKey: [...SedeApi.CLAVE, 'area', id()],
      queryFn: () => firstValueFrom(this.http.get<AreaDeServicio>(`${this.url}/${id()}`)),
      // Vacio mientras el alta de un equipo no sepa todavia en que area se instala: sin esto se
      // pediria /service-areas/ a secas, que no es la ficha de nada.
      enabled: !!id(),
    }));
  }

  /**
   * Varias areas por sus identificadores, en una sola consulta de cache.
   *
   * <p><b>Emite una peticion por area</b>, y no es por gusto: el API no publica ni un listado global de
   * areas ni una consulta por lote, solo {@code /headquarters/{idSede}/service-areas} y el detalle por
   * identificador. El listado de equipos necesita el nombre del area de cada fila, y lo unico que trae
   * la respuesta del equipo es el identificador.
   *
   * <p>Se agrupan en una sola consulta con clave derivada de los identificadores <b>ordenados</b>: sin
   * ordenar, dos listas con los mismos elementos en otro orden se cachearian por separado. Queda
   * anotado como deuda; es la tercera vez que una respuesta devuelve identificadores sin nombres.
   */
  variasPorId(ids: Signal<readonly string[]>) {
    return injectQuery(() => ({
      queryKey: [...SedeApi.CLAVE, 'areas-por-id', [...ids()].sort().join(',')],
      queryFn: () =>
        Promise.all(
          ids().map((id) =>
            firstValueFrom(this.http.get<AreaDeServicio>(`${this.url}/${id}`)),
          ),
        ),
      enabled: ids().length > 0,
    }));
  }

  crear() {
    return injectMutation(() => ({
      mutationFn: ({ idSede, area }: { idSede: string; area: NuevaArea }) =>
        firstValueFrom(
          this.http.post<AreaDeServicio>(`${this.urlSedes}/${idSede}/service-areas`, area),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  editar() {
    return injectMutation(() => ({
      mutationFn: ({ id, cambio }: { id: string; cambio: CambioDeArea }) =>
        firstValueFrom(this.http.patch<AreaDeServicio>(`${this.url}/${id}`, cambio)),
      onSuccess: () => this.invalidar(),
    }));
  }

  cerrar() {
    return injectMutation(() => ({
      mutationFn: (id: string) => firstValueFrom(this.http.delete<void>(`${this.url}/${id}`)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Invalida la rama de las sedes, de la que cuelgan las areas.
   *
   * <p>No hace falta tocar la del cliente: la lista de sedes de un cliente no dice nada de sus areas.
   * Invalidar mas de lo necesario costaria consultas que no traen ninguna informacion nueva.
   */
  private invalidar() {
    return this.queryClient.invalidateQueries({ queryKey: SedeApi.CLAVE });
  }
}
