import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClienteApi } from '../../client/cliente-api';
import { asignacionesDe, EncargadoApi } from '../../client/encargado-api';
import { SedeApi } from '../../client/sede-api';
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
 *
 * <p><b>Rehecha el 2026-10-11 con el rediseño</b> —la maqueta «A-Personas»—: un solo «Registrar
 * persona» que despliega las cuatro altas por la escalera de permisos, filtros con cuántos hay de cada
 * función, buscador, y una columna «Cliente» que dice de quién es cada persona del cliente. Para los
 * representantes sale de los clientes; para los encargados, de sus asignaciones y del resumen de sedes,
 * que dice de qué cliente es cada sede y cada área.
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
  private readonly leeEncargados = computed(() => this.sesion.puede('engineer.read'));
  private readonly clientes = inject(ClienteApi).listar(this.leeClientes);
  private readonly sedes = inject(SedeApi).resumen(undefined, this.leeClientes);
  private readonly encargados = inject(EncargadoApi).listar(this.leeEncargados);
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
  /**
   * Quién tiene alguna sede o área a cargo. Desde V46 eso es lo que hace a alguien encargado además de
   * su tipo: un representante a cargo de un área se ve y se filtra también como encargado.
   */
  private readonly aCargo = computed(
    () =>
      new Set(
        (this.encargados.data() ?? [])
          .filter((e) => e.estadoActivo && !!e.idPersona && asignacionesDe(e).length > 0)
          .map((e) => e.idPersona!),
      ),
  );

  protected readonly cuantas = computed(() => {
    const personas = this.personas.data() ?? [];
    const aCargo = this.aCargo();

    return Object.fromEntries(
      FILTROS.map((f) => [f.clave, personas.filter((p) => cumple(p, f.clave, aCargo)).length]),
    ) as Record<Filtro, number>;
  });

  /** Persona → de qué cliente es y de qué responde, si es del cliente. */
  private readonly delCliente = computed(() => {
    const nombreDeCliente = new Map(
      (this.clientes.data() ?? []).map((c) => [c.id, c.razonSocial ?? '']),
    );
    const sedes = new Map((this.sedes.data() ?? []).map((s) => [s.id, s]));
    const areas = new Map(
      (this.sedes.data() ?? []).flatMap((s) =>
        (s.areas ?? []).map((a) => [a.id, { area: a, sede: s }] as const),
      ),
    );
    const resultado = new Map<string, { clientes: Set<string>; aCargoDe: string[] }>();
    const anotar = (idPersona: string, idCliente: string | undefined, aCargoDe?: string) => {
      const actual = resultado.get(idPersona) ?? { clientes: new Set<string>(), aCargoDe: [] };
      const nombre = idCliente ? nombreDeCliente.get(idCliente) : undefined;
      if (nombre) actual.clientes.add(nombre);
      if (aCargoDe) actual.aCargoDe.push(aCargoDe);
      resultado.set(idPersona, actual);
    };

    for (const cliente of this.clientes.data() ?? []) {
      for (const idPersona of cliente.representantes ?? []) {
        anotar(idPersona, cliente.id);
      }
    }
    for (const encargado of this.encargados.data() ?? []) {
      if (!encargado.estadoActivo || !encargado.idPersona) continue;
      // Desde el 2026-10-27 esta a cargo de varias cosas: se anotan todas.
      for (const { tipo, idAsignacion } of asignacionesDe(encargado)) {
        if (tipo === 'SERVICE_AREA') {
          const donde = areas.get(idAsignacion);
          anotar(encargado.idPersona, donde?.sede.idCliente, donde?.area.nombre);
        } else {
          const sede = sedes.get(idAsignacion);
          anotar(encargado.idPersona, sede?.idCliente, sede?.nombre);
        }
      }
    }

    return resultado;
  });

  protected readonly filas = computed(() => {
    const buscado = normalizar(this.texto());
    const delCliente = this.delCliente();
    const aCargo = this.aCargo();

    return (this.personas.data() ?? [])
      .filter((persona) => cumple(persona, this.filtro(), aCargo))
      .filter(
        (persona) =>
          !buscado ||
          normalizar(nombreCompleto(persona)).includes(buscado) ||
          (persona.cedula ?? '').includes(buscado),
      )
      .map((persona) => {
        const deLaCasa = esDeLaCasa(persona);
        const cliente = delCliente.get(persona.identificador!);
        const aCargoDe = cliente?.aCargoDe.filter(Boolean) ?? [];

        return {
          id: persona.identificador!,
          nombre: nombreCompleto(persona),
          iniciales: iniciales(persona),
          deLaCasa,
          correo: (persona.emailPersonList ?? []).find((c) => c.estadoActivo)?.correoPersona ?? '',
          cedula: persona.cedula ?? '—',
          // Un representante a cargo de una sede o un área es además encargado, y las dos funciones se
          // muestran. Desde V46 lo segundo no lo dice un segundo tipo sino que tenga algo a cargo.
          oficios:
            oficiosDe(persona, aCargo)
              .map((t) => ETIQUETA_DE_TIPO_DE_PERSONA[t])
              .join(' · ') + (aCargoDe.length ? ` · ${aCargoDe.join(', ')}` : ''),
          cliente: deLaCasa ? 'De la casa' : [...(cliente?.clientes ?? [])].join(' · ') || '—',
          // Que tenga cuenta no lo dice el API: se deduce de su oficio. Las tres altas con cuenta son
          // administrador, ingeniero y representante; un encargado puede existir sin ella.
          entraAlSistema: deLaCasa || persona.tipoPersona === 'CEO_CLIENT',
          estadoActivo: !!persona.estadoActivo,
        };
      });
  });

  protected readonly hayError = computed(() => this.personas.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.personas.error()));
}

/** Los botones del filtro: la escalera de la casa junta al super usuario con los administradores. */
type Filtro = 'TODAS' | 'INGENIEROS' | 'ADMINISTRADORES' | 'REPRESENTANTES' | 'ENCARGADOS';

const FILTROS: readonly { clave: Filtro; etiqueta: string; tipos: readonly TipoDePersona[] }[] = [
  { clave: 'TODAS', etiqueta: 'Todas', tipos: [] },
  { clave: 'INGENIEROS', etiqueta: 'Ingenieros', tipos: ['ENGINEER'] },
  { clave: 'ADMINISTRADORES', etiqueta: 'Administradores', tipos: ['ADMIN', 'SUPER_ADMIN'] },
  { clave: 'REPRESENTANTES', etiqueta: 'Representantes', tipos: ['CEO_CLIENT'] },
  { clave: 'ENCARGADOS', etiqueta: 'Encargados', tipos: ['MANAGER'] },
];

function cumple(persona: Persona, filtro: Filtro, aCargo: ReadonlySet<string>): boolean {
  const tipos = FILTROS.find((f) => f.clave === filtro)!.tipos;

  return !tipos.length || oficiosDe(persona, aCargo).some((t) => tipos.includes(t));
}

/** Su tipo, y encargado además si tiene algo a cargo y no lo es ya por tipo. */
function oficiosDe(persona: Persona, aCargo: ReadonlySet<string>): TipoDePersona[] {
  const tipo = persona.tipoPersona as TipoDePersona | undefined;
  if (!tipo) return [];

  return tipo !== 'MANAGER' && aCargo.has(persona.identificador ?? '') ? [tipo, 'MANAGER'] : [tipo];
}

function esDeLaCasa(persona: Persona): boolean {
  return exigeEscalonDeArriba(persona.tipoPersona);
}

function iniciales(persona: Persona): string {
  return [persona.primerNombre, persona.primerApellido]
    .map((parte) => (parte ?? '').trim().charAt(0).toUpperCase())
    .join('');
}
