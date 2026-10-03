import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { nombreCompleto, PersonaApi } from '../persona-api';
import { Sesion } from '../../../core/auth/sesion';
import {
  ETIQUETA_DE_TIPO_DE_PERSONA,
  exigeEscalonDeArriba,
  TIPOS_DE_PERSONA,
  TipoDePersona,
} from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';

/**
 * La gente que el sistema conoce: la de la casa y la del cliente, con cuenta o sin ella.
 *
 * <p><b>Es la primera pantalla de este módulo</b>, que tenía catorce operaciones en el API y ninguna
 * vista: hasta hoy un usuario solo se podía dar de alta con `curl`. Los requisitos de usuarios contaban
 * como implementados porque el backend los satisface por completo, y esa cuenta era correcta y a la vez
 * dejaba el producto sin forma de usarlos.
 *
 * <p><b>Una persona no es un usuario.</b> Alguien puede existir sin cuenta —un encargado al que solo se
 * llama por teléfono—, así que la columna de acceso dice las dos cosas por separado: qué es en el
 * negocio y si entra al sistema.
 *
 * <p><b>El filtro por tipo es del lado del navegador</b>, y es honesto decir por qué: el API publica
 * `GET /persons` sin parámetros, de modo que no hay forma de pedirle «solo los ingenieros». Con la
 * cantidad de gente que maneja una empresa de mantenimiento esto sobra; el día que no sobre, el filtro
 * tiene que bajar al servidor y esta pantalla no cambia de forma.
 */
@Component({
  selector: 'app-lista-personas',
  imports: [RouterLink],
  templateUrl: './lista-personas.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaPersonas {
  private readonly sesion = inject(Sesion);

  protected readonly personas = inject(PersonaApi).listar();

  protected readonly TIPOS = TIPOS_DE_PERSONA;
  protected readonly etiquetaDeTipo = ETIQUETA_DE_TIPO_DE_PERSONA;

  /** El tipo por el que se filtra, o vacío para ver a todos. */
  protected readonly tipoElegido = signal<TipoDePersona | ''>('');

  /** Quién puede dar de alta a quién: la escalera del backend, reflejada. */
  protected readonly puedeAltaDeLaCasa = computed(() => this.sesion.puede('super.person.write'));
  protected readonly puedeAltaDelCliente = computed(() => this.sesion.puede('person.write'));

  protected readonly filas = computed(() => {
    const tipo = this.tipoElegido();

    return (this.personas.data() ?? [])
      .filter(
        (persona) =>
          !tipo || persona.tipoPersona === tipo || persona.segundoTipoPersona === tipo,
      )
      .map((persona) => ({
        id: persona.identificador!,
        nombre: nombreCompleto(persona),
        cedula: persona.cedula ?? '—',
        // Una persona puede tener dos funciones —el modelo lo permite— y las dos se muestran: alguien
        // que es ingeniero y encargado a la vez no es un caso raro, es el jefe de servicio del cliente.
        oficios: [persona.tipoPersona, persona.segundoTipoPersona]
          .filter((t): t is TipoDePersona => !!t)
          .map((t) => ETIQUETA_DE_TIPO_DE_PERSONA[t])
          .join(' · '),
        // Que tenga cuenta no lo dice el API: se deduce de su oficio. Las tres altas con cuenta son
        // administrador, ingeniero y representante; un encargado puede existir sin ella.
        entraAlSistema: exigeEscalonDeArriba(persona.tipoPersona) || persona.tipoPersona === 'CEO_CLIENT',
        estadoActivo: !!persona.estadoActivo,
      }));
  });

  protected readonly hayError = computed(() => this.personas.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.personas.error()));

  protected filtrarPor(valor: string): void {
    this.tipoElegido.set((valor as TipoDePersona) || '');
  }
}
