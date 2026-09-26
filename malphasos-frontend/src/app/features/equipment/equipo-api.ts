import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  injectQueryClient,
} from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CambioDeEquipoDeCliente,
  EquipoDeCliente,
  NuevoEquipoDeCliente,
} from '../../core/api/tipos';
import { SedeApi } from '../client/sede-api';

/**
 * La unica puerta al servidor para los equipos instalados en un area.
 *
 * <p>Es el final de la cadena del catalogo: aqui un modelo deja de ser una entrada de catalogo y pasa
 * a ser <b>una maquina concreta</b>, con su serie, su numero de inventario y su fecha de compra. Es
 * tambien lo que una orden de trabajo toca.
 *
 * <p><b>El alta exige {@code equipment.assign} y no {@code equipment.write}</b>, y esa diferencia es
 * del backend: instalar un equipo en un area es repartir algo a alguien, no editar un catalogo. La
 * misma autoridad protege el traslado.
 *
 * <p>Las claves cuelgan de la de las sedes —{@code ['sedes', idSede, 'areas', idArea, 'equipos']}—,
 * de modo que lo que invalida una sede alcanza a sus equipos. Se pide el area, no la sede, porque es
 * del area de donde cuelgan.
 */
@Injectable({ providedIn: 'root' })
export class EquipoApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly api = `${environment.api}/v1/api`;

  static readonly CLAVE = ['equipos-de-cliente'] as const;

  static claveDeArea(idArea: string) {
    return [...EquipoApi.CLAVE, idArea] as const;
  }

  listarDe(idArea: Signal<string>) {
    return injectQuery(() => ({
      queryKey: EquipoApi.claveDeArea(idArea()),
      queryFn: () =>
        firstValueFrom(
          this.http.get<EquipoDeCliente[]>(`${this.api}/service-areas/${idArea()}/equipments`),
        ),
    }));
  }

  registrar() {
    return injectMutation(() => ({
      mutationFn: ({ idArea, equipo }: { idArea: string; equipo: NuevoEquipoDeCliente }) =>
        firstValueFrom(
          this.http.post<EquipoDeCliente>(
            `${this.api}/service-areas/${idArea}/equipments`,
            equipo,
          ),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Corrige los datos administrativos de un equipo: inventario, fecha y valor de compra.
   *
   * <p>La serie y el modelo no se editan, y no es un olvido del contrato: la serie identifica a la
   * maquina y el modelo dice que maquina es. Cambiar cualquiera de los dos seria decir que esta fila
   * describe otro equipo, no corregir este.
   */
  editar() {
    return injectMutation(() => ({
      mutationFn: ({ id, cambio }: { id: string; cambio: CambioDeEquipoDeCliente }) =>
        firstValueFrom(
          this.http.patch<EquipoDeCliente>(`${this.api}/client-equipments/${id}`, cambio),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  /** Da de baja el equipo. Como todo aqui, queda inactivo con su historia intacta. */
  darDeBaja() {
    return injectMutation(() => ({
      mutationFn: (id: string) =>
        firstValueFrom(this.http.delete<void>(`${this.api}/client-equipments/${id}`)),
      onSuccess: () => this.invalidar(),
    }));
  }

  private async invalidar(): Promise<void> {
    await Promise.all([
      this.queryClient.invalidateQueries({ queryKey: EquipoApi.CLAVE }),
      // Las areas viven bajo la sede, y cuantos equipos tiene un area es algo que esa rama muestra.
      this.queryClient.invalidateQueries({ queryKey: SedeApi.CLAVE }),
    ]);
  }
}
