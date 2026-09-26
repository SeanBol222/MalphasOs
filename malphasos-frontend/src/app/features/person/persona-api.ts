import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Persona } from '../../core/api/tipos';

/**
 * La puerta al modulo de personas, hoy <b>solo de lectura y con un unico proposito</b>: poner nombre
 * a los identificadores que otros modulos devuelven.
 *
 * <p>Un encargado, tal como lo publica el API, es un {@code idPersona} y su asignacion; el nombre no
 * viaja en esa respuesta. Sin esta consulta, la lista de encargados de una sede seria una columna de
 * UUID, que no sirve para nada.
 *
 * <p><b>Se pide la lista entera y se indexa, en vez de una consulta por encargado.</b> El API no
 * ofrece filtrar ni consultar varias a la vez, de modo que la alternativa serian N peticiones para
 * pintar N filas. Queda anotado como deuda: es el mismo hueco que el filtrado por dueno.
 *
 * <p>Exige la autoridad {@code person.read}, que el grupo {@code clients} no tiene. Quien no la tenga
 * recibe un 403 y la pantalla lo dice en vez de inventarse un nombre.
 */
@Injectable({ providedIn: 'root' })
export class PersonaApi {
  private readonly http = inject(HttpClient);

  private readonly url = `${environment.api}/v1/api/persons`;

  static readonly CLAVE = ['personas'] as const;

  listar() {
    return injectQuery(() => ({
      queryKey: PersonaApi.CLAVE,
      queryFn: () => firstValueFrom(this.http.get<Persona[]>(this.url)),
    }));
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
