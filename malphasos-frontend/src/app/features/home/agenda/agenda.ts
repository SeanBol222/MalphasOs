import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ClienteApi } from '../../client/cliente-api';
import { SedeApi } from '../../client/sede-api';
import { OrdenApi } from '../../workOrder/orden-api';
import { Sesion } from '../../../core/auth/sesion';
import { desdeIso, fechaIso, HOY, lunesDe, sumarDias } from '../../../core/fechas';
import {
  ETIQUETA_DE_ESTADO,
  ETIQUETA_DE_PERIODICIDAD,
  ETIQUETA_DE_TIPO_DE_SERVICIO,
  OrdenDeTrabajo,
} from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';

type Vista = 'semana' | 'mes';

/** Una orden tal como la pinta la agenda: con nombres y etiquetas, no con identificadores. */
interface BloqueDeOrden {
  readonly id: string;
  readonly fecha: string;
  /** «Martes 6 de octubre», para el panel: la ISO es para la maquina. */
  readonly fechaLarga: string;
  readonly cliente: string;
  readonly sede: string;
  readonly servicio: string;
  readonly periodicidad: string;
  /** El de ejecucion, o ANULADA si la orden se retiro: es lo que decide la forma del bloque. */
  readonly estado: string;
  readonly etiquetaEstado: string;
  readonly equipos: string;
  readonly asignada: boolean;
  readonly mia: boolean;
}

const MESES = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
];
const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

/**
 * La agenda de ordenes de trabajo del inicio, por semana o por mes.
 *
 * <p>Entro el <b>2026-10-10</b> con el rediseño: el usuario eligio entre tres propuestas la «sala de
 * control» y pidio para ella la agenda que traia otra. Pide al servidor solo el periodo que pinta
 * —{@code ?desde=&hasta=}, que se construyo para esto— en lugar de todas las ordenes.
 *
 * <p><b>El estado se distingue por forma y peso, nunca por un color nuevo</b>, como manda el manual: en
 * ejecucion es un bloque de tinta, creada un bloque blanco con borde, ejecutada un bloque gris con borde
 * punteado y una anulada va tachada. La leyenda lo dice con palabras, y cada bloque lo lleva en su
 * etiqueta accesible.
 *
 * <p><b>«Solo mis ordenes» no necesita preguntar quien soy</b>: el identificador de una persona es el
 * que le da Keycloak, asi que el {@code sub} de la sesion es el ingeniero que se filtra.
 */
@Component({
  selector: 'app-agenda',
  imports: [RouterLink, NgClass],
  templateUrl: './agenda.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Agenda {
  private readonly sesion = inject(Sesion);
  private readonly hoy = fechaIso(inject(HOY)());

  protected readonly vista = signal<Vista>('semana');
  protected readonly soloMias = signal(false);

  /** Cualquier dia del periodo que se ve; las flechas lo mueven una semana o un mes. */
  private readonly referencia = signal(desdeIso(this.hoy));

  /** El id de la orden elegida, si se eligio alguna. */
  protected readonly elegida = signal<string | null>(null);

  /** Los dias que pinta la cuadricula: siempre semanas completas, de lunes a domingo. */
  private readonly dias = computed(() => {
    const referencia = this.referencia();

    if (this.vista() === 'semana') {
      const lunes = lunesDe(referencia);

      return Array.from({ length: 7 }, (_, i) => sumarDias(lunes, i));
    }

    const primero = new Date(referencia.getFullYear(), referencia.getMonth(), 1);
    const ultimo = new Date(referencia.getFullYear(), referencia.getMonth() + 1, 0);
    const desde = lunesDe(primero);
    const hasta = sumarDias(lunesDe(ultimo), 6);
    const total = Math.round((hasta.getTime() - desde.getTime()) / 86_400_000) + 1;

    return Array.from({ length: total }, (_, i) => sumarDias(desde, i));
  });

  private readonly desde = computed(() => fechaIso(this.dias()[0]!));
  private readonly hasta = computed(() => fechaIso(this.dias().at(-1)!));
  private readonly idIngeniero = computed(() =>
    this.soloMias() ? this.sesion.identificador() || null : null,
  );

  protected readonly ordenes = inject(OrdenApi).periodo(this.desde, this.hasta, this.idIngeniero);
  private readonly clientes = inject(ClienteApi).listar();
  private readonly idsDeSedes = computed(() => [
    ...new Set((this.ordenes.data() ?? []).map((orden) => orden.idSede!).filter(Boolean)),
  ]);
  private readonly sedes = inject(SedeApi).variasPorId(this.idsDeSedes);

  protected readonly titulo = computed(() => {
    const dias = this.dias();

    if (this.vista() === 'mes') {
      const r = this.referencia();

      return `${MESES[r.getMonth()]} de ${r.getFullYear()}`;
    }

    const [primero, ultimo] = [dias[0]!, dias[6]!];
    const mesPrimero =
      primero.getMonth() === ultimo.getMonth() ? '' : ` de ${MESES[primero.getMonth()]}`;

    return `${primero.getDate()}${mesPrimero} al ${ultimo.getDate()} de ${MESES[ultimo.getMonth()]} de ${ultimo.getFullYear()}`;
  });

  private readonly bloques = computed(() => {
    const clientes = new Map((this.clientes.data() ?? []).map((c) => [c.id, c.razonSocial]));
    const sedes = new Map((this.sedes.data() ?? []).map((s) => [s.id, s.nombre]));

    return (this.ordenes.data() ?? []).map((orden) => this.bloque(orden, clientes, sedes));
  });

  protected readonly celdas = computed(() => {
    const mes = this.referencia().getMonth();
    const porDia = new Map<string, BloqueDeOrden[]>();
    for (const bloque of this.bloques()) {
      porDia.set(bloque.fecha, [...(porDia.get(bloque.fecha) ?? []), bloque]);
    }

    return this.dias().map((dia, i) => {
      const iso = fechaIso(dia);

      return {
        iso,
        numero: dia.getDate(),
        nombre: `${DIAS[i % 7]} ${dia.getDate()} de ${MESES[dia.getMonth()]}`,
        esHoy: iso === this.hoy,
        fueraDelMes: this.vista() === 'mes' && dia.getMonth() !== mes,
        finDeSemana: i % 7 >= 5,
        ordenes: porDia.get(iso) ?? [],
      };
    });
  });

  protected readonly cabecera = DIAS.map((dia) => dia.slice(0, 3));

  protected readonly seleccion = computed(
    () => this.bloques().find((bloque) => bloque.id === this.elegida()) ?? null,
  );

  protected readonly hayError = computed(() => this.ordenes.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.ordenes.error()));

  protected verVista(vista: Vista): void {
    this.vista.set(vista);
  }

  protected mover(sentido: 1 | -1): void {
    const r = this.referencia();

    this.referencia.set(
      this.vista() === 'semana'
        ? sumarDias(r, 7 * sentido)
        : new Date(r.getFullYear(), r.getMonth() + sentido, 1),
    );
  }

  protected irAHoy(): void {
    this.referencia.set(desdeIso(this.hoy));
  }

  protected alternarMias(): void {
    this.soloMias.update((valor) => !valor);
  }

  protected elegir(id: string): void {
    this.elegida.set(id);
  }

  private bloque(
    orden: OrdenDeTrabajo,
    clientes: Map<string | undefined, string | undefined>,
    sedes: Map<string | undefined, string | undefined>,
  ): BloqueDeOrden {
    const estado = orden.estadoActivo === false ? 'ANULADA' : (orden.estadoEjecucion ?? 'CREADA');
    const etiquetaEstado = estado === 'ANULADA' ? 'Anulada' : ETIQUETA_DE_ESTADO[estado];
    const equipos = orden.equipos?.length ?? 0;

    return {
      id: orden.id!,
      fecha: orden.fechaMantenimiento!,
      fechaLarga: fechaLarga(orden.fechaMantenimiento!),
      cliente: clientes.get(orden.idCliente) ?? 'Cliente no disponible',
      sede: sedes.get(orden.idSede) ?? 'Sede no disponible',
      servicio: orden.tipoServicio ? ETIQUETA_DE_TIPO_DE_SERVICIO[orden.tipoServicio] : '',
      periodicidad: orden.periodicidad ? ETIQUETA_DE_PERIODICIDAD[orden.periodicidad] : '',
      estado,
      etiquetaEstado,
      equipos: equipos === 1 ? '1 equipo' : equipos === 0 ? 'Sin equipos' : `${equipos} equipos`,
      asignada: !!orden.idIngeniero,
      mia: !!orden.idIngeniero && orden.idIngeniero === this.sesion.identificador(),
    };
  }
}

/** «Martes 6 de octubre», sin pasar por Greenwich. */
function fechaLarga(iso: string): string {
  const dia = desdeIso(iso);

  return `${DIAS[(dia.getDay() + 6) % 7]} ${dia.getDate()} de ${MESES[dia.getMonth()]}`;
}
