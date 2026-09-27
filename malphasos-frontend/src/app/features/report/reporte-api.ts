import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  injectQueryClient,
} from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LlenarReporte, NuevaLectura, ReporteDeServicio } from '../../core/api/tipos';

/**
 * La unica puerta al servidor para los reportes de servicio.
 *
 * <p><b>No hay «listar todos».</b> El API no lo ofrece, y no es una carencia: un reporte vive dentro de
 * su orden o dentro del historial de su equipo, asi que las dos consultas que existen llevan filtro
 * obligatorio. Pedir {@code /reports} a secas devuelve 400, y esta clase no da forma de hacerlo.
 *
 * <p><b>Llenar y cerrar son operaciones distintas</b>, como iniciar y ejecutar en una orden. Cerrar no
 * es poner un campo «estado»: es una ruta propia, y el servidor comprueba en ella que haya
 * procedimientos, resultado y la verificacion completa. Si fuera un campo, la pantalla podria escribir
 * «FINALIZADO» en un reporte vacio.
 *
 * <p><b>Un campo ausente al llenar deja el valor como estaba; uno en blanco lo borra.</b> Es del
 * backend y se aprovecha tal cual: el formulario manda solo lo que se toco.
 */
@Injectable({ providedIn: 'root' })
export class ReporteApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly url = `${environment.api}/v1/api/reports`;

  static readonly CLAVE = ['reportes'] as const;

  static claveDeOrden(idOrdenTrabajo: string) {
    return [...ReporteApi.CLAVE, 'orden', idOrdenTrabajo] as const;
  }

  static claveDeEquipo(idEquipoCliente: string) {
    return [...ReporteApi.CLAVE, 'equipo', idEquipoCliente] as const;
  }

  static claveDetalle(id: string) {
    return [...ReporteApi.CLAVE, id] as const;
  }

  /** Los reportes de una orden, que es como RF-09 pide poder llegar a ellos. */
  porOrden(idOrdenTrabajo: Signal<string>) {
    return injectQuery(() => ({
      queryKey: ReporteApi.claveDeOrden(idOrdenTrabajo()),
      queryFn: () =>
        firstValueFrom(
          this.http.get<ReporteDeServicio[]>(this.url, {
            params: { idOrdenTrabajo: idOrdenTrabajo() },
          }),
        ),
      // Sin identificador no se consulta: el API exige filtro y responderia 400.
      enabled: !!idOrdenTrabajo(),
    }));
  }

  /** El historial de un equipo: es la base de la hoja de vida (RF-26, RF-27). */
  porEquipo(idEquipoCliente: Signal<string>) {
    return injectQuery(() => ({
      queryKey: ReporteApi.claveDeEquipo(idEquipoCliente()),
      queryFn: () =>
        firstValueFrom(
          this.http.get<ReporteDeServicio[]>(this.url, {
            params: { idEquipoCliente: idEquipoCliente() },
          }),
        ),
      enabled: !!idEquipoCliente(),
    }));
  }

  detalle(id: Signal<string>) {
    return injectQuery(() => ({
      queryKey: ReporteApi.claveDetalle(id()),
      queryFn: () => firstValueFrom(this.http.get<ReporteDeServicio>(`${this.url}/${id()}`)),
      enabled: !!id(),
    }));
  }

  /** Abre el reporte de un equipo de una orden. Nace en borrador y vacio. */
  abrir() {
    return injectMutation(() => ({
      mutationFn: ({
        idOrdenTrabajo,
        idEquipoCliente,
      }: {
        idOrdenTrabajo: string;
        idEquipoCliente: string;
      }) =>
        firstValueFrom(
          this.http.post<ReporteDeServicio>(`${this.url}/work-orders/${idOrdenTrabajo}`, {
            idEquipoCliente,
          }),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  llenar() {
    return injectMutation(() => ({
      mutationFn: ({ id, datos }: { id: string; datos: LlenarReporte }) =>
        firstValueFrom(this.http.patch<ReporteDeServicio>(`${this.url}/${id}`, datos)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Registra la verificacion, sustituyendo la anterior entera.
   *
   * <p>Se manda la tabla completa y no celda a celda, porque es como se revisa: el ingeniero corrige un
   * valor y entrega la tabla. Las lecturas anteriores quedan retiradas, no borradas.
   */
  registrarVerificacion() {
    return injectMutation(() => ({
      mutationFn: ({ id, lecturas }: { id: string; lecturas: readonly NuevaLectura[] }) =>
        firstValueFrom(
          this.http.patch<ReporteDeServicio>(`${this.url}/${id}/verification`, { lecturas }),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  /** Cierra el reporte. Desde aqui ya no cambia: corregirlo es retirarlo y abrir otro. */
  cerrar() {
    return injectMutation(() => ({
      mutationFn: (id: string) =>
        firstValueFrom(this.http.patch<ReporteDeServicio>(`${this.url}/${id}/finish`, {})),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Retira el reporte.
   *
   * <p>Se llama retirar y no borrar porque es lo que ocurre: la fila queda con su historia y sus
   * lecturas. Es ademas la unica salida de un reporte cerrado con un error.
   */
  retirar() {
    return injectMutation(() => ({
      mutationFn: (id: string) => firstValueFrom(this.http.delete<void>(`${this.url}/${id}`)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Recarga lo que cambio, <b>sin esperarlo</b>.
   *
   * <p>TanStack aguarda la promesa que devuelve {@code onSuccess} antes de resolver la mutacion, asi que
   * devolverla frena cada paso de una cadena con una recarga que no necesita. Es la <b>tercera</b> vez
   * que este proyecto lo escribe —{@code CatalogoApi} y {@code OrdenApi} fueron las dos primeras, y las
   * dos lo pagaron con una prueba colgada—, de modo que aqui entra ya hecho: abrir los reportes de los
   * ocho equipos de una orden son ocho llamadas en serie.
   */
  private invalidar(): void {
    void this.queryClient.invalidateQueries({ queryKey: ReporteApi.CLAVE });
  }
}
