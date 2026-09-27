import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  injectQueryClient,
} from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { NuevaOrdenDeTrabajo, OrdenDeTrabajo } from '../../core/api/tipos';

/**
 * La unica puerta al servidor para las ordenes de trabajo.
 *
 * <p><b>Nueve operaciones y ninguna es «editar la orden».</b> Eso no es una carencia del API: una orden
 * no se edita, se le hacen cosas. Se programa, se le asignan equipos, se le asigna un ingeniero, se
 * inicia, se ejecuta y se anula. Cada una es una ruta propia porque el backend comprueba en cada una
 * que el estado la permita — una orden ejecutada no se puede volver a iniciar.
 *
 * <p><b>El estado no se manda, se pide la transicion.</b> Si esto fuera un campo editable, el frontend
 * podria poner «EJECUTADA» en una orden que nadie empezo, y el servidor tendria que adivinar la
 * intencion. Con {@code start} y {@code execute} la intencion es la operacion.
 *
 * <p>Asignar el ingeniero exige {@code work-order.assign} y no {@code work-order.write}, y esa
 * separacion es deliberada en el backend: <b>repartir trabajo no es lo mismo que alterarlo</b>. Hay una
 * prueba alli que impide que otra operacion se cuele en esa autoridad.
 */
@Injectable({ providedIn: 'root' })
export class OrdenApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly url = `${environment.api}/v1/api/work-orders`;

  static readonly CLAVE = ['ordenes'] as const;

  static claveDetalle(id: string) {
    return [...OrdenApi.CLAVE, id] as const;
  }

  listar() {
    return injectQuery(() => ({
      queryKey: OrdenApi.CLAVE,
      queryFn: () => firstValueFrom(this.http.get<OrdenDeTrabajo[]>(this.url)),
    }));
  }

  detalle(id: Signal<string>) {
    return injectQuery(() => ({
      queryKey: OrdenApi.claveDetalle(id()),
      queryFn: () => firstValueFrom(this.http.get<OrdenDeTrabajo>(`${this.url}/${id()}`)),
      enabled: !!id(),
    }));
  }

  programar() {
    return injectMutation(() => ({
      mutationFn: (orden: NuevaOrdenDeTrabajo) =>
        firstValueFrom(this.http.post<OrdenDeTrabajo>(this.url, orden)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Suma un equipo al alcance de la orden.
   *
   * <p>Uno por llamada, porque es lo que el API ofrece. La pantalla que permite elegir varios los manda
   * en serie y cuenta lo que entro: ver {@code AgregarEquipos}.
   */
  agregarEquipo() {
    return injectMutation(() => ({
      mutationFn: ({ id, idEquipoCliente }: { id: string; idEquipoCliente: string }) =>
        firstValueFrom(
          this.http.post<OrdenDeTrabajo>(`${this.url}/${id}/equipments`, { idEquipoCliente }),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  quitarEquipo() {
    return injectMutation(() => ({
      mutationFn: ({ id, idEquipoCliente }: { id: string; idEquipoCliente: string }) =>
        firstValueFrom(
          this.http.delete<void>(`${this.url}/${id}/equipments/${idEquipoCliente}`),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  asignarIngeniero() {
    return injectMutation(() => ({
      mutationFn: ({ id, idIngeniero }: { id: string; idIngeniero: string }) =>
        firstValueFrom(
          this.http.patch<OrdenDeTrabajo>(`${this.url}/${id}/engineer/${idIngeniero}`, {}),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  iniciar() {
    return this.transicion('start');
  }

  ejecutar() {
    return this.transicion('execute');
  }

  /**
   * Anula la orden.
   *
   * <p>Se llama anular y no borrar porque es lo que ocurre: queda inactiva con su historia. El listado
   * la sigue mostrando, distinguida por peso tipografico.
   */
  anular() {
    return injectMutation(() => ({
      mutationFn: (id: string) => firstValueFrom(this.http.delete<void>(`${this.url}/${id}`)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /** Las dos transiciones son la misma llamada con otro nombre: el estado lo decide el servidor. */
  private transicion(paso: 'start' | 'execute') {
    return injectMutation(() => ({
      mutationFn: (id: string) =>
        firstValueFrom(this.http.patch<OrdenDeTrabajo>(`${this.url}/${id}/${paso}`, {})),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Recarga lo que cambio, <b>sin esperarlo</b>.
   *
   * <p>TanStack aguarda la promesa que devuelve {@code onSuccess} antes de resolver la mutacion, de modo
   * que devolverla encadena cada alta con una recarga completa de la orden. Se nota justo donde importa:
   * anadir veinte equipos son veinte llamadas en serie, y cada una esperaria un viaje de ida y vuelta que
   * no necesita. Lo delato la prueba de esta pantalla, que se quedaba esperando la segunda alta.
   *
   * <p>Es la segunda vez que este proyecto lo paga —la primera fue {@code CatalogoApi} con el panel que
   * crea un modelo—, asi que conviene saberlo antes de escribir el tercer servicio con una cadena.
   */
  private invalidar(): void {
    void this.queryClient.invalidateQueries({ queryKey: OrdenApi.CLAVE });
  }
}
