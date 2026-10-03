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
  CambioDePersona,
  NuevaPersonaSinAcceso,
  NuevoUsuario,
  Persona,
} from '../../core/api/tipos';

/**
 * La unica puerta al modulo de personas.
 *
 * <p><b>Cuatro altas y no una, y eso no es ruido del API: son cuatro hechos distintos.</b> Una crea
 * solo la fila —alguien a quien se llama por telefono y no entra al sistema— y las otras tres crean
 * ademas su cuenta en Keycloak, por lo que exigen correo, nombre de usuario y contrasena inicial. El
 * tipo no viaja en el cuerpo de esas tres: lo dice la ruta, de modo que no se puede pedir «un
 * ingeniero» y mandar «ADMIN» en el campo.
 *
 * <p><b>Y quien puede usar cada una depende de QUE es esa persona</b>, que es la escalera de usuarios
 * del backend: la gente de la casa —ingenieros y administradores— exige {@code super.person.write}, y
 * la del cliente {@code person.write}. Las pantallas no autorizan, ocultan: el servidor comprueba
 * igual, pero ofrecer un boton que va a dar 403 es ofrecer algo que no existe.
 *
 * <p><b>La edicion es PUT y no PATCH</b>, al contrario que el resto del API. No es un despiste de esta
 * clase: {@code person} se migro antes de que la convencion se fijara y conserva tres PUT del sistema
 * original. Esta anotado como deuda; aqui se refleja lo que el contrato publica, no lo que deberia
 * publicar. La consecuencia practica importa: <b>un PUT manda la persona entera</b>, asi que un campo
 * que se deje fuera se borra.
 */
@Injectable({ providedIn: 'root' })
export class PersonaApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly url = `${environment.api}/v1/api/persons`;

  static readonly CLAVE = ['personas'] as const;

  static claveDetalle(id: string) {
    return [...PersonaApi.CLAVE, id] as const;
  }

  listar() {
    return injectQuery(() => ({
      queryKey: PersonaApi.CLAVE,
      queryFn: () => firstValueFrom(this.http.get<Persona[]>(this.url)),
    }));
  }

  detalle(id: Signal<string>) {
    return injectQuery(() => ({
      queryKey: PersonaApi.claveDetalle(id()),
      queryFn: () => firstValueFrom(this.http.get<Persona>(`${this.url}/${id()}`)),
      enabled: !!id(),
    }));
  }

  /** Alguien que no entra al sistema: se crea la fila y no hay cuenta que crear. */
  registrarSinAcceso() {
    return injectMutation(() => ({
      mutationFn: (persona: NuevaPersonaSinAcceso) =>
        firstValueFrom(this.http.post<Persona>(this.url, persona)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Las tres altas con cuenta. El tipo lo pone la ruta, no el cuerpo.
   *
   * <p>Tocan dos sistemas —la base y Keycloak— <b>sin transaccion que los envuelva</b>. El backend lo
   * resuelve en un orden concreto y, si el segundo paso falla, intenta deshacer el primero; cuando no
   * puede, lo deja escrito en su registro. Por eso los errores de Keycloak tienen mensaje propio en el
   * catalogo: no dicen que el formulario este mal.
   */
  registrarConCuenta(oficio: 'admins' | 'engineers' | 'ceo-clients') {
    return injectMutation(() => ({
      mutationFn: (usuario: NuevoUsuario) =>
        firstValueFrom(this.http.post<Persona>(`${this.url}/${oficio}`, usuario)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /** Manda la persona entera: es un PUT, y lo que no viaje se pierde. */
  editar() {
    return injectMutation(() => ({
      mutationFn: ({ id, cambio }: { id: string; cambio: CambioDePersona }) =>
        firstValueFrom(this.http.put<Persona>(`${this.url}/${id}`, cambio)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Retira a la persona.
   *
   * <p>Se llama retirar y no borrar porque es lo que hace: la fila queda con su historia. Y hace una
   * cosa mas que conviene saber —<b>deshabilita su cuenta de Keycloak</b>—, con un limite:
   * <b>los tokens ya emitidos siguen valiendo hasta que caducan</b>, 300 s en el realm de desarrollo.
   * Retirar a alguien no lo echa de la sesion que ya tiene abierta.
   */
  retirar() {
    return injectMutation(() => ({
      mutationFn: (id: string) => firstValueFrom(this.http.delete<void>(`${this.url}/${id}`)),
      onSuccess: () => this.invalidar(),
    }));
  }

  // --- Correos y telefonos: sub-recursos con sus propias rutas -----------------

  /**
   * Las seis operaciones de contacto, que son dos juegos de tres y se comportan igual.
   *
   * <p>Se generan en vez de escribirse seis veces porque lo unico que cambia es el segmento de la ruta
   * y el nombre del campo. Cada una invalida la persona entera: un correo no tiene consulta propia, vive
   * dentro de la ficha.
   *
   * <p><b>Un contacto no se borra, se retira</b> —{@code DELETE} apaga su estado y la fila se queda—,
   * como todo en este proyecto. La ficha solo pinta los vigentes.
   */
  anadirCorreo() {
    return this.anadirContacto<{ correoPersona: string }>('emails');
  }

  editarCorreo() {
    return this.editarContacto<{ correoPersona: string }>('emails');
  }

  retirarCorreo() {
    return this.retirarContacto('emails');
  }

  anadirTelefono() {
    return this.anadirContacto<{ telefonoPersona: string }>('phones');
  }

  editarTelefono() {
    return this.editarContacto<{ telefonoPersona: string }>('phones');
  }

  retirarTelefono() {
    return this.retirarContacto('phones');
  }

  private anadirContacto<C>(recurso: 'emails' | 'phones') {
    return injectMutation(() => ({
      mutationFn: ({ idPersona, contacto }: { idPersona: string; contacto: C }) =>
        firstValueFrom(this.http.post<unknown>(`${this.url}/${idPersona}/${recurso}`, contacto)),
      onSuccess: () => this.invalidar(),
    }));
  }

  /** Tambien PUT, como la propia persona, y por el mismo motivo historico. */
  private editarContacto<C>(recurso: 'emails' | 'phones') {
    return injectMutation(() => ({
      mutationFn: ({
        idPersona,
        idContacto,
        contacto,
      }: {
        idPersona: string;
        idContacto: string;
        contacto: C;
      }) =>
        firstValueFrom(
          this.http.put<unknown>(`${this.url}/${idPersona}/${recurso}/${idContacto}`, contacto),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  private retirarContacto(recurso: 'emails' | 'phones') {
    return injectMutation(() => ({
      mutationFn: ({ idPersona, idContacto }: { idPersona: string; idContacto: string }) =>
        firstValueFrom(
          this.http.delete<void>(`${this.url}/${idPersona}/${recurso}/${idContacto}`),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Recarga lo que cambio, sin esperarlo.
   *
   * <p>Invalida por PREFIJO, de modo que alcanza el listado y la ficha de una vez: las claves son
   * jerarquicas y {@code claveDetalle} cuelga de {@code CLAVE}. Sin eso, anadir un correo recargaria el
   * listado y dejaria la ficha —que es donde se esta mirando— con el dato viejo.
   *
   * <p>Y se lanza sin esperarla, como en los otros tres servicios con cadenas: TanStack aguarda la
   * promesa de {@code onSuccess} antes de resolver la mutacion, y eso frena cada paso de un alta con
   * varios contactos con una recarga que no necesita.
   */
  private invalidar(): void {
    void this.queryClient.invalidateQueries({ queryKey: PersonaApi.CLAVE });
  }
}

/** El nombre completo de una persona, tal como se escribe en un documento. */
export function nombreCompleto(persona: Persona): string {
  return [
    persona.primerNombre,
    persona.segundoNombre,
    persona.primerApellido,
    persona.segundoApellido,
  ]
    .filter(Boolean)
    .join(' ');
}
