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
