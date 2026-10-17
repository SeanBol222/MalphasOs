import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  injectQueryClient,
} from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MiFirma } from '../../core/api/tipos';

/**
 * La firma de quien tiene la sesión (V38, 2026-10-17). Solo la propia: la ruta no lleva identificador y
 * el servidor saca la persona del token.
 */
@Injectable({ providedIn: 'root' })
export class FirmaApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly url = `${environment.api}/v1/api/persons/me/signature`;

  static readonly CLAVE = ['mi-firma'] as const;

  /** La firma, o nulo si todavía no la ha guardado: el 404 aquí es un estado, no un fallo. */
  consultar() {
    return injectQuery(() => ({
      queryKey: FirmaApi.CLAVE,
      queryFn: () =>
        firstValueFrom(this.http.get<MiFirma>(this.url)).catch((fallo: unknown) => {
          if (fallo instanceof HttpErrorResponse && fallo.status === 404) {
            return null;
          }
          throw fallo;
        }),
    }));
  }

  guardar() {
    return injectMutation(() => ({
      mutationFn: (imagen: string) =>
        firstValueFrom(this.http.patch<MiFirma>(this.url, { imagen })),
      onSuccess: () => void this.queryClient.invalidateQueries({ queryKey: FirmaApi.CLAVE }),
    }));
  }
}
