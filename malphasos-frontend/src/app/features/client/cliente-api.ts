import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  injectQueryClient,
} from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CambioDeCliente, Cliente, NuevoCliente } from '../../core/api/tipos';

/**
 * La unica puerta al servidor para el agregado cliente.
 *
 * <p><b>Ningun componente llama al API directamente.</b> Aqui, y solo aqui, vive TanStack Query,
 * cuyo adaptador de Angular se distribuye con el sufijo {@code experimental} en el nombre del
 * paquete. Si su API cambia entre versiones, lo que se toca son estos servicios y no cada pantalla.
 *
 * <p>Es el espejo de un puerto de entrada del backend: lo que alli es
 * {@code ClientServicePort}, aqui es esto. <b>Un servicio por agregado y no uno por modulo</b>: el
 * modulo {@code client} tiene cuatro —cliente, sede, area de servicio y encargado— y el backend
 * tambien los separa asi, en {@code application/services/<agregado>/}. Un unico servicio para los
 * cuatro seria el archivo mas grande del frontend y no correspoderia con nada del otro lado.
 *
 * <p><b>Toda escritura invalida {@code ['clientes']}</b>, y eso alcanza tambien al detalle y a las
 * sedes, porque TanStack Query invalida por prefijo de clave. Es deliberado: una clave jerarquica
 * ahorra tener que acordarse de invalidar cada pantalla que mira el mismo dato, que es el defecto
 * clasico de dos vistas de las que solo una se entera.
 */
@Injectable({ providedIn: 'root' })
export class ClienteApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly url = `${environment.api}/v1/api/clients`;

  /** Clave de cache de la lista. Se declara una vez para que invalidar no dependa de recordarla. */
  static readonly CLAVE_LISTA = ['clientes'] as const;

  /** La clave del detalle cuelga de la de la lista, para que invalidar la lista lo alcance. */
  static claveDetalle(id: string) {
    return [...ClienteApi.CLAVE_LISTA, id] as const;
  }

  /**
   * La lista de clientes.
   *
   * <p>Admite una senal que la apaga, y no es un adorno: el alta de un equipo la usa para su primer
   * desplegable, pero cuando se entra ya sabiendo el area esa lista no se ensena y pedirla seria una
   * consulta entera para nada.
   */
  listar(habilitada?: Signal<boolean>) {
    return injectQuery(() => ({
      queryKey: ClienteApi.CLAVE_LISTA,
      queryFn: () => firstValueFrom(this.http.get<Cliente[]>(this.url)),
      enabled: habilitada ? habilitada() : true,
    }));
  }

  /**
   * Un cliente por su identificador.
   *
   * <p>Recibe una senal y no una cadena: el identificador viene de la ruta, y si la ruta cambia sin
   * destruir el componente —de un cliente a otro— una cadena fija dejaria la pantalla mostrando el
   * anterior.
   */
  detalle(id: Signal<string>) {
    return injectQuery(() => ({
      queryKey: ClienteApi.claveDetalle(id()),
      queryFn: () => firstValueFrom(this.http.get<Cliente>(`${this.url}/${id()}`)),
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
      onSuccess: () => this.invalidar(),
    }));
  }

  editar() {
    return injectMutation(() => ({
      mutationFn: ({ id, cambio }: { id: string; cambio: CambioDeCliente }) =>
        firstValueFrom(this.http.patch<Cliente>(`${this.url}/${id}`, cambio)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Corrige la sigla, la que encabeza el numero de sus hojas de vida. Tiene ruta propia en el backend
   * porque no es un dato mas del cliente: se genera sola, y corregirla responde 409 si la tiene otro.
   */
  corregirSigla() {
    return injectMutation(() => ({
      mutationFn: ({ id, sigla }: { id: string; sigla: string }) =>
        firstValueFrom(this.http.patch<Cliente>(`${this.url}/${id}/acronym`, { sigla })),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Retira al cliente.
   *
   * <p>Se llama retirar y no borrar porque el backend no borra: marca el estado en falso y el
   * cliente sigue en el listado, distinguido por peso tipografico. Llamarlo «eliminar» en pantalla
   * prometeria algo que no ocurre.
   */
  retirar() {
    return injectMutation(() => ({
      mutationFn: (id: string) => firstValueFrom(this.http.delete<void>(`${this.url}/${id}`)),
      onSuccess: () => this.invalidar(),
    }));
  }

  agregarCorreo() {
    return this.altaDeContacto('emails');
  }

  quitarCorreo() {
    return this.bajaDeContacto('emails');
  }

  agregarTelefono() {
    return this.altaDeContacto('phones');
  }

  quitarTelefono() {
    return this.bajaDeContacto('phones');
  }

  /**
   * Correos y telefonos se manejan igual porque el backend los expone igual: mismo cuerpo, mismas
   * dos operaciones, sub-recurso distinto. Escribir las cuatro a mano invitaria a que una se
   * desviara de las otras sin motivo.
   */
  private altaDeContacto(recurso: 'emails' | 'phones') {
    return injectMutation(() => ({
      mutationFn: ({ id, valor }: { id: string; valor: string }) =>
        firstValueFrom(this.http.post<Cliente>(`${this.url}/${id}/${recurso}`, { valor })),
      onSuccess: () => this.invalidar(),
    }));
  }

  private bajaDeContacto(recurso: 'emails' | 'phones') {
    return injectMutation(() => ({
      mutationFn: ({ id, idContacto }: { id: string; idContacto: string }) =>
        firstValueFrom(this.http.delete<void>(`${this.url}/${id}/${recurso}/${idContacto}`)),
      onSuccess: () => this.invalidar(),
    }));
  }

  private invalidar() {
    return this.queryClient.invalidateQueries({ queryKey: ClienteApi.CLAVE_LISTA });
  }
}
