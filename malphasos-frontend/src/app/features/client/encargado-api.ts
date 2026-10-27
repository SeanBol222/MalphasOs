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
  AsignacionDeEncargado,
  Encargado,
  NuevoEncargado,
  TipoDeAsignacion,
} from '../../core/api/tipos';
import { PersonaApi } from '../person/persona-api';

/** Una sede o un área de la que un encargado está a cargo. */
export interface AsignacionActiva {
  readonly tipo: TipoDeAsignacion;
  readonly idAsignacion: string;
}

/**
 * Lo que un encargado tiene a cargo, sedes primero. Desde el 2026-10-27 (`V41`) son varias cosas, y la
 * respuesta trae dos listas en lugar de un tipo con una sede o un área.
 */
export function asignacionesDe(encargado: Encargado): AsignacionActiva[] {
  return [
    ...(encargado.idsSede ?? []).map((id) => ({ tipo: 'HEADQUARTER' as const, idAsignacion: id })),
    ...(encargado.idsAreaServicio ?? []).map((id) => ({
      tipo: 'SERVICE_AREA' as const,
      idAsignacion: id,
    })),
  ];
}

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

  /**
   * Todos los encargados. Admite una senal que la apaga, como {@code ClienteApi.listar}: la ficha de una
   * orden la ensena solo a quien tiene {@code engineer.read}, y pedirla sin permiso seria un 403.
   */
  listar(habilitada?: Signal<boolean>) {
    return injectQuery(() => ({
      queryKey: EncargadoApi.CLAVE,
      queryFn: () => firstValueFrom(this.http.get<Encargado[]>(this.url)),
      enabled: habilitada ? habilitada() : true,
    }));
  }

  registrar() {
    return injectMutation(() => ({
      mutationFn: (encargado: NuevoEncargado) =>
        firstValueFrom(this.http.post<Encargado>(this.url, encargado)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /** Pone al frente de una sede o de un área a alguien que ya existe como persona. */
  asignar() {
    return injectMutation(() => ({
      mutationFn: (asignacion: AsignacionDeEncargado) =>
        firstValueFrom(this.http.post<Encargado>(`${this.url}/assignments`, asignacion)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Releva al encargado de una sede o de un área, y solo de esa: sigue a cargo de lo demás.
   *
   * <p>Sustituye, el 2026-10-27, al retiro de todo: con varias asignaciones, quitar a alguien de una
   * sede no puede quitarlo de las otras. La ruta lleva el identificador de la <b>persona</b>, porque el
   * encargado no tiene identidad aparte.
   */
  relevar() {
    return injectMutation(() => ({
      mutationFn: ({ idPersona, tipo, idAsignacion }: { idPersona: string } & AsignacionActiva) =>
        firstValueFrom(
          this.http.delete<void>(
            `${this.url}/${idPersona}/${tipo === 'HEADQUARTER' ? 'headquarters' : 'service-areas'}/${idAsignacion}`,
          ),
        ),
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
