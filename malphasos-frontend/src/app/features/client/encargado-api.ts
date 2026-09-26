import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  injectQueryClient,
} from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Encargado, NuevoEncargado } from '../../core/api/tipos';
import { PersonaApi } from '../person/persona-api';

/**
 * La unica puerta al servidor para el agregado encargado.
 *
 * <p>Un encargado es <b>la persona responsable de una sede o de un area</b>, y es el caso que explica
 * por que {@code PersonType.MANAGER} existe sin {@code RoleType}: puede figurar como contacto sin
 * acceder nunca a la aplicacion. Registrar uno crea la persona y la asigna en una sola operacion; el
 * backend lo fuerza a ser del tipo {@code MANAGER}.
 *
 * <p><b>No hay forma de pedir los encargados de una sede</b>: el API solo publica la lista completa,
 * sin filtros. Se trae entera y se recorta aqui. Queda anotado como deuda; con un catalogo de
 * desarrollo no se nota y con miles de sedes si.
 *
 * <p>Retirar a un encargado invalida tambien la cache de personas: el alta crea una persona nueva, y
 * la lista que le pone nombre a los identificadores se queda corta sin eso.
 */
@Injectable({ providedIn: 'root' })
export class EncargadoApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly url = `${environment.api}/v1/api/managers`;

  static readonly CLAVE = ['encargados'] as const;

  listar() {
    return injectQuery(() => ({
      queryKey: EncargadoApi.CLAVE,
      queryFn: () => firstValueFrom(this.http.get<Encargado[]>(this.url)),
    }));
  }

  registrar() {
    return injectMutation(() => ({
      mutationFn: (encargado: NuevoEncargado) =>
        firstValueFrom(this.http.post<Encargado>(this.url, encargado)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Retira al encargado.
   *
   * <p>La ruta lleva el identificador de la <b>persona</b> y no uno propio del encargado, porque el
   * encargado no tiene identidad aparte: es una persona con una asignacion.
   */
  retirar() {
    return injectMutation(() => ({
      mutationFn: (idPersona: string) =>
        firstValueFrom(this.http.delete<void>(`${this.url}/${idPersona}`)),
      onSuccess: () => this.invalidar(),
    }));
  }

  private async invalidar(): Promise<void> {
    await Promise.all([
      this.queryClient.invalidateQueries({ queryKey: EncargadoApi.CLAVE }),
      this.queryClient.invalidateQueries({ queryKey: PersonaApi.CLAVE }),
    ]);
  }
}
