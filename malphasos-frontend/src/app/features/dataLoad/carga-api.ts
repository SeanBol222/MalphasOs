import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { InformeDeCarga } from '../../core/api/tipos';

/**
 * La carga de datos de otro sistema (2026-11-08), solo para el SuperUsuario: validar sin escribir y
 * aplicar, todo o nada.
 *
 * <p><b>Las dos son mutaciones y no consultas</b>: subir unos archivos es una acción, y su resultado no
 * se guarda en caché ni se vuelve a pedir solo.
 *
 * <p><b>Un 422 al aplicar no es un fallo, es un informe</b>: el servidor responde así a una carga con
 * errores o que rechazó al escribirla, con la misma forma que la validación. La pantalla lo lee igual.
 * Cualquier otro error sí lo es.
 */
@Injectable({ providedIn: 'root' })
export class CargaApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly api = `${environment.api}/v1/api/data-loads`;

  validar() {
    return injectMutation(() => ({
      mutationFn: (archivos: readonly File[]) =>
        firstValueFrom(
          this.http.post<InformeDeCarga>(`${this.api}/validation`, formulario(archivos)),
        ),
    }));
  }

  /**
   * Una carga aplicada crea catálogo, personas, clientes, órdenes y reportes de golpe: toda la caché queda
   * vieja, y se invalida entera en vez de nombrar cada clave.
   */
  aplicar() {
    return injectMutation(() => ({
      mutationFn: (archivos: readonly File[]) =>
        firstValueFrom(this.http.post<InformeDeCarga>(this.api, formulario(archivos))).catch(
          (fallo: unknown) => {
            if (fallo instanceof HttpErrorResponse && fallo.status === 422 && fallo.error) {
              return fallo.error as InformeDeCarga;
            }
            throw fallo;
          },
        ),
      onSuccess: (informe: InformeDeCarga) => {
        if (informe.valida) {
          void this.queryClient.invalidateQueries();
        }
      },
    }));
  }
}

function formulario(archivos: readonly File[]): FormData {
  const datos = new FormData();
  for (const archivo of archivos) {
    datos.append('archivos', archivo, archivo.name);
  }
  return datos;
}
