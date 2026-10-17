import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  injectQueryClient,
} from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DatosDeEmpresa, Empresa } from '../../core/api/tipos';

/**
 * La empresa que presta el servicio (2026-10-17): sus datos y su logo.
 *
 * <p>Una escritura deja vieja la hoja de vida en caché, que imprime el membrete: se invalida también.
 */
@Injectable({ providedIn: 'root' })
export class EmpresaApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly api = `${environment.api}/v1/api/company`;

  static readonly CLAVE = ['empresa'] as const;

  /**
   * La empresa, o nulo si todavía no se ha configurado. El 404 no es un fallo aquí: es el estado de una
   * instalación nueva sin archivo de arranque, y la pantalla lo dice en vez de mostrar un error.
   */
  consultar() {
    return injectQuery(() => ({
      queryKey: EmpresaApi.CLAVE,
      queryFn: () =>
        firstValueFrom(this.http.get<Empresa>(this.api)).catch((fallo: unknown) => {
          if (fallo instanceof HttpErrorResponse && fallo.status === 404) {
            return null;
          }
          throw fallo;
        }),
    }));
  }

  guardar() {
    return injectMutation(() => ({
      mutationFn: (datos: DatosDeEmpresa) =>
        firstValueFrom(this.http.patch<Empresa>(this.api, datos)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /** Sube el logo como archivo: el servidor deduce el tipo de sus bytes. */
  subirLogo() {
    return injectMutation(() => ({
      mutationFn: (archivo: File) => {
        const formulario = new FormData();
        formulario.append('archivo', archivo);

        return firstValueFrom(this.http.patch<Empresa>(`${this.api}/logo`, formulario));
      },
      onSuccess: () => this.invalidar(),
    }));
  }

  quitarLogo() {
    return injectMutation(() => ({
      mutationFn: () => firstValueFrom(this.http.delete<void>(`${this.api}/logo`)),
      onSuccess: () => this.invalidar(),
    }));
  }

  private invalidar(): void {
    void this.queryClient.invalidateQueries({ queryKey: EmpresaApi.CLAVE });
    void this.queryClient.invalidateQueries({ queryKey: ['hoja-de-vida'] });
  }
}
