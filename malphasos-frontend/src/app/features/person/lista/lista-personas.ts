import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClienteApi } from '../../client/cliente-api';
import { normalizar } from '../../../core/texto';
import { nombreCompleto, PersonaApi } from '../persona-api';
import { Sesion } from '../../../core/auth/sesion';
import {
  ETIQUETA_DE_TIPO_DE_PERSONA,
  exigeEscalonDeArriba,
  Persona,
  TipoDePersona,
} from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';

/**
 * La gente que el sistema conoce: la de la casa y la del cliente.
 *
 * <p><b>Es la primera pantalla de este módulo</b>, que tenía catorce operaciones en el API y ninguna
 * vista: hasta hoy un usuario solo se podía dar de alta con `curl`. Los requisitos de usuarios contaban
 * como implementados porque el backend los satisface por completo, y esa cuenta era correcta y a la vez
 * dejaba el producto sin forma de usarlos.
 *
 * <p><b>Desde V53 (2026-11-01) toda persona tiene cuenta</b>: la columna de acceso decía si entraba al
 * sistema, porque un encargado del cliente podía existir sin ella, y salió con los encargados.
 *
 * <p><b>El filtro por tipo es del lado del navegador</b>, y es honesto decir por qué: el API publica
 * `GET /persons` sin parámetros, de modo que no hay forma de pedirle «solo los ingenieros». Con la
 * cantidad de gente que maneja una empresa de mantenimiento esto sobra; el día que no sobre, el filtro
 * tiene que bajar al servidor y esta pantalla no cambia de forma.
 *
 * <p><b>Rehecha el 2026-10-11 con el rediseño</b> —la maqueta «A-Personas»—: un solo «Registrar
 * persona» que despliega las altas por la escalera de permisos, filtros con cuántos hay de cada
 * función, buscador, y una columna «Cliente» que dice de quién es cada supervisor: sale de los clientes
 * que supervisa, con el cargo con que se le nombró en cada uno.
 */
@Component({
  selector: 'app-lista-personas',
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './lista-personas.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaPersonas {
  private readonly sesion = inject(Sesion);

  protected readonly personas = inject(PersonaApi).listar();

  /** Lo que da el cliente de cada persona: cada consulta con su autoridad, y sin ella no se pide. */
  private readonly leeClientes = computed(() => this.sesion.puede('client.read'));
  private readonly clientes = inject(ClienteApi).listar(this.leeClientes);
  protected readonly veClientes = this.leeClientes;

  /** Quién puede dar de alta a quién: la escalera del backend, reflejada. */
  protected readonly puedeAltaDeLaCasa = computed(() => this.sesion.puede('super.person.write'));
  protected readonly puedeAltaDelCliente = computed(() => this.sesion.puede('person.write'));
  protected readonly menuAbierto = signal(false);

  protected readonly FILTROS = FILTROS;
  protected readonly filtro = signal<Filtro>('TODAS');
  protected readonly buscador = new FormControl('', { nonNullable: true });
  private readonly texto = toSignal(this.buscador.valueChanges, { initialValue: '' });

  /** Cuántas personas hay de cada función, para los botones del filtro. */
  protected readonly cuantas = computed(() => {
    const personas = this.personas.data() ?? [];

    return Object.fromEntries(
      FILTROS.map((f) => [f.clave, personas.filter((p) => cumple(p, f.clave)).length]),
    ) as Record<Filtro, number>;
  });

  /** Persona → los clientes que supervisa, cada uno con el cargo con que se le nombró. */
  private readonly delCliente = computed(() => {
    const resultado = new Map<string, string[]>();

    for (const cliente of this.clientes.data() ?? []) {
      for (const { idPersona, cargo } of cliente.supervisores ?? []) {
        if (!idPersona) continue;
        const nombre = cliente.razonSocial ?? '';
        resultado.set(idPersona, [
          ...(resultado.get(idPersona) ?? []),
          cargo ? `${nombre} (${cargo})` : nombre,
        ]);
      }
    }

    return resultado;
  });

  protected readonly filas = computed(() => {
    const buscado = normalizar(this.texto());
    const delCliente = this.delCliente();

    return (this.personas.data() ?? [])
      .filter((persona) => cumple(persona, this.filtro()))
      .filter(
        (persona) =>
          !buscado ||
          normalizar(nombreCompleto(persona)).includes(buscado) ||
          (persona.cedula ?? '').includes(buscado),
      )
      .map((persona) => {
        const deLaCasa = esDeLaCasa(persona);

        return {
          id: persona.identificador!,
          nombre: nombreCompleto(persona),
          iniciales: iniciales(persona),
          deLaCasa,
          correo: (persona.emailPersonList ?? []).find((c) => c.estadoActivo)?.correoPersona ?? '',
          cedula: persona.cedula ?? '—',
          oficios: persona.tipoPersona
            ? ETIQUETA_DE_TIPO_DE_PERSONA[persona.tipoPersona as TipoDePersona]
            : '',
          cliente: deLaCasa
            ? 'De la casa'
            : (delCliente.get(persona.identificador!) ?? []).join(' · ') || '—',
          estadoActivo: !!persona.estadoActivo,
        };
      });
  });

  protected readonly hayError = computed(() => this.personas.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.personas.error()));
}

/** Los botones del filtro: la escalera de la casa junta al super usuario con los administradores. */
type Filtro = 'TODAS' | 'INGENIEROS' | 'ADMINISTRADORES' | 'SUPERVISORES';

const FILTROS: readonly { clave: Filtro; etiqueta: string; tipos: readonly TipoDePersona[] }[] = [
  { clave: 'TODAS', etiqueta: 'Todas', tipos: [] },
  { clave: 'INGENIEROS', etiqueta: 'Ingenieros', tipos: ['ENGINEER'] },
  { clave: 'ADMINISTRADORES', etiqueta: 'Administradores', tipos: ['ADMIN', 'SUPER_ADMIN'] },
  { clave: 'SUPERVISORES', etiqueta: 'Supervisores', tipos: ['SUPERVISOR'] },
];

function cumple(persona: Persona, filtro: Filtro): boolean {
  const tipos = FILTROS.find((f) => f.clave === filtro)!.tipos;

  return !tipos.length || tipos.includes(persona.tipoPersona as TipoDePersona);
}

function esDeLaCasa(persona: Persona): boolean {
  return exigeEscalonDeArriba(persona.tipoPersona);
}

function iniciales(persona: Persona): string {
  return [persona.primerNombre, persona.primerApellido]
    .map((parte) => (parte ?? '').trim().charAt(0).toUpperCase())
    .join('');
}
