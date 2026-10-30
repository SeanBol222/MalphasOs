import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClienteApi } from '../../client/cliente-api';
import { asignacionesDe, EncargadoApi } from '../../client/encargado-api';
import { SedeApi } from '../../client/sede-api';
import { EquipoApi } from '../../equipment/equipo-api';
import { OrdenApi } from '../orden-api';
import { ReporteApi } from '../../report/reporte-api';
import { nombreCompleto, PersonaApi } from '../../person/persona-api';
import { Sesion } from '../../../core/auth/sesion';
import {
  ETIQUETA_DE_ESTADO,
  ETIQUETA_DE_PERIODICIDAD,
  ETIQUETA_DE_RESULTADO,
  ETIQUETA_DE_TIPO_DE_SERVICIO,
  EstadoDeEjecucion,
  ESTADOS_DE_EJECUCION,
  FilaDeResumenDeEquipos,
} from '../../../core/api/tipos';
import { fechaCorta } from '../../../core/fechas';
import { Buscador, Opcion } from '../../../shared/buscador/buscador';
import { traducirError } from '../../../core/errores/traducir';

/**
 * Una orden de trabajo: sus datos, su alcance y su ciclo de vida.
 *
 * <p><b>El estado no se edita, se avanza.</b> Hay dos botones —iniciar y ejecutar— y cada uno aparece
 * solo cuando el estado lo permite, porque el backend comprueba lo mismo y responde «el estado no lo
 * permite» a lo demás. Un desplegable de estados invitaría a saltarse el orden.
 *
 * <p><b>Asignar el ingeniero exige otra autoridad</b> que el resto: {@code work-order.assign}, no
 * {@code work-order.write}. Es del backend y la separación es buena — repartir trabajo no es lo mismo que
 * alterarlo—, así que el selector solo se ve con ella.
 *
 * <p><b>Rehecha el 2026-10-11 con el rediseño</b> —la maqueta «A-Orden»—: cabecera con la línea de
 * estado, los equipos agrupados por área con su avance, y a un lado los datos, el ingeniero y los
 * encargados del sitio. Cada equipo del alcance llega como identificador, y lo que se sabe de él —tipo,
 * modelo, hoja de vida, serie y área— sale del listado de equipos pedido por esos identificadores, en una
 * petición. Hasta ese día se cruzaba la lista entera de equipos con una petición por área.
 *
 * <p><b>Una orden ejecutada se lee de su documento</b> (2026-10-28, {@code V49}): cliente, sede, ingeniero
 * y cada equipo con su área, como eran el día que se ejecutó. Lo único que sigue en vivo es el estado de
 * cada reporte, porque un reporte cerrado se puede sustituir después, y la ficha lo avisa.
 */
@Component({
  selector: 'app-detalle-orden',
  imports: [ReactiveFormsModule, RouterLink, Buscador],
  templateUrl: './detalle-orden.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleOrden {
  readonly id = input.required<string>();

  private readonly api = inject(OrdenApi);
  private readonly sesion = inject(Sesion);
  private readonly router = inject(Router);

  protected readonly orden = this.api.detalle(this.id);

  /** Lo que la orden era al ejecutarse. Sin él, todo se lee en vivo. */
  protected readonly documento = computed(() => this.orden.data()?.documento ?? undefined);

  /** «Congelada al ejecutarse el 28 oct 2026», o que se congeló después, con los datos de entonces. */
  protected readonly congelada = computed(() => {
    const documento = this.documento();

    if (!documento) {
      return '';
    }

    return this.orden.data()?.documentoTardio || !documento.fechaEjecucion
      ? 'Congelada después de ejecutarse, con los datos del día en que se actualizó el sistema.'
      : `Congelada al ejecutarse el ${fechaCorta(documento.fechaEjecucion)}: dice lo que era ese día.`;
  });

  protected readonly inicio = this.api.iniciar();
  protected readonly ejecucion = this.api.ejecutar();
  protected readonly anulacion = this.api.anular();
  protected readonly bajaDeEquipo = this.api.quitarEquipo();
  protected readonly asignacion = this.api.asignarIngeniero();

  private readonly clientes = inject(ClienteApi).listar();
  private readonly idsDelAlcance = computed(() =>
    (this.orden.data()?.equipos ?? []).map((equipo) => equipo.idEquipoCliente!).filter(Boolean),
  );
  private readonly resumen = inject(EquipoApi).resumenDe(this.idsDelAlcance);
  private readonly sedes = inject(SedeApi).variasPorId(
    computed(() => {
      const sede = this.orden.data()?.idSede;

      return sede ? [sede] : [];
    }),
  );
  private readonly personas = inject(PersonaApi).listar();

  /** Los encargados del sitio se ven con {@code engineer.read}, la autoridad que los protege. */
  protected readonly puedeVerEncargados = computed(() => this.sesion.puede('engineer.read'));
  private readonly encargados = inject(EncargadoApi).listar(this.puedeVerEncargados);

  private readonly reporteApi = inject(ReporteApi);
  private readonly reportes = this.reporteApi.porOrden(this.id);

  protected readonly puedeEscribir = computed(() => this.sesion.puede('work-order.write'));
  protected readonly puedeAsignar = computed(() => this.sesion.puede('work-order.assign'));

  /**
   * Un reporte cuenta lo que se hizo, y en una orden sin empezar no se ha hecho nada: desde V50 el
   * borrador de cada equipo nace al añadirlo, pero el servidor no deja escribir en él hasta que la orden
   * empieza. Aquí se avisa, para que nadie lo descubra con un 409.
   */
  protected readonly sinEmpezar = computed(
    () => this.orden.data()?.estadoEjecucion === 'CREADA' && !!this.orden.data()?.estadoActivo,
  );

  /** Mientras no se ha ejecutado, sus equipos se añaden y se quitan. */
  protected readonly cambiaDeEquipos = computed(
    () => this.orden.data()?.estadoEjecucion !== 'EJECUTADA' && !!this.orden.data()?.estadoActivo,
  );

  protected readonly confirmandoAnulacion = signal(false);
  protected readonly asignando = signal(false);

  /**
   * El ingeniero elegido en el buscador.
   *
   * <p>Un {@code FormControl} y no una senal porque el buscador es un {@code ControlValueAccessor}: se
   * comunica por el control, no por una salida. Se intento con {@code (cambio)} y no existia — el
   * compilador de plantillas lo dijo, no {@code tsc}.
   */
  protected readonly ingenieroElegido = new FormControl('', { nonNullable: true });

  protected readonly cliente = computed(() => {
    const congelado = this.documento()?.cliente?.nombre;

    if (congelado) {
      return congelado;
    }

    const id = this.orden.data()?.idCliente;

    return (
      (this.clientes.data() ?? []).find((cliente) => cliente.id === id)?.razonSocial ??
      'No disponible'
    );
  });

  protected readonly sede = computed(
    () => this.documento()?.sede?.nombre ?? (this.sedes.data() ?? [])[0]?.nombre ?? 'No disponible',
  );

  protected readonly estado = computed(() => {
    const estado = this.orden.data()?.estadoEjecucion;

    return estado ? ETIQUETA_DE_ESTADO[estado] : '';
  });

  protected readonly servicio = computed(() => {
    const datos = this.orden.data();
    const etiqueta = datos?.tipoServicio ? ETIQUETA_DE_TIPO_DE_SERVICIO[datos.tipoServicio] : '';
    // La garantía es una marca del correctivo (2026-10-16): «Correctivo en garantía».
    const tipo = datos?.garantia ? `${etiqueta} en garantía` : etiqueta;
    const cada = datos?.periodicidad ? ETIQUETA_DE_PERIODICIDAD[datos.periodicidad] : '';

    return cada ? `${tipo} · ${cada}` : tipo;
  });

  /** Solo se puede iniciar una orden creada, y ejecutar una en ejecución. Lo mismo comprueba el backend. */
  protected readonly puedeIniciar = computed(
    () => this.orden.data()?.estadoEjecucion === 'CREADA' && !!this.orden.data()?.estadoActivo,
  );
  protected readonly puedeEjecutar = computed(
    () => this.orden.data()?.estadoEjecucion === 'EN_EJECUCION',
  );

  /**
   * Cuántos equipos no tienen aún su reporte cerrado. El servidor no deja ejecutar la orden mientras
   * queden (2026-10-14), así que el botón se desactiva y lo dice, en vez de ofrecer un 409.
   */
  protected readonly reportesPendientes = computed(() => this.totalDeEquipos() - this.cerrados());

  /** El ingeniero asignado, con nombre. Sin él, la orden está programada y no repartida. */
  protected readonly ingeniero = computed(() => {
    const congelado = this.documento()?.ingeniero;

    if (congelado) {
      return congelado;
    }

    const id = this.orden.data()?.idIngeniero;

    if (!id) {
      return '';
    }

    const persona = (this.personas.data() ?? []).find((p) => p.identificador === id);

    return persona ? nombreCompleto(persona) : 'No disponible';
  });

  /**
   * Los ingenieros a los que se puede repartir el trabajo.
   *
   * <p>Se filtran por tipo de persona: el API no publica «los ingenieros», publica las personas. Es la
   * misma ausencia de siempre, y aquí se nota porque una lista de todas las personas incluiría a los
   * encargados de sede, que no ejecutan mantenimientos.
   */
  protected readonly opcionesDeIngeniero = computed<readonly Opcion[]>(() =>
    (this.personas.data() ?? [])
      .filter((persona) => persona.tipoPersona === 'ENGINEER' && persona.estadoActivo)
      .map((persona) => ({ id: persona.identificador!, etiqueta: nombreCompleto(persona) })),
  );

  /** Los pasos del estado, en orden: la línea de la cabecera. */
  protected readonly pasos = computed(() => {
    const actual = this.orden.data()?.estadoEjecucion;
    const indice = ESTADOS_DE_EJECUCION.indexOf(actual as EstadoDeEjecucion);

    return ESTADOS_DE_EJECUCION.map((estado, i) => ({
      etiqueta: `${i + 1} · ${ETIQUETA_DE_ESTADO[estado]}`,
      hecho: i < indice,
      actual: i === indice,
    }));
  });

  protected readonly programada = computed(() => fechaCorta(this.orden.data()?.fechaMantenimiento));

  /**
   * El alcance, agrupado por área: qué equipos toca esta orden, qué es cada uno y cómo va su reporte.
   *
   * <p>El reporte que cuenta es <b>el vivo</b>: la consulta trae también los retirados, porque el API
   * devuelve el historial completo de la orden. Desde V50 cada equipo de una orden viva tiene el suyo
   * —es lo que lo pone en ella— y la orden lo dice; el estado se lee de la consulta de reportes.
   *
   * <p>El área de cada equipo es la de hoy, del listado de equipos: mientras la orden no se ejecuta, sus
   * equipos no se trasladan. La de una orden ejecutada está en su documento.
   */
  protected readonly grupos = computed(() => {
    const resumen = new Map((this.resumen.data()?.filas ?? []).map((fila) => [fila.id, fila]));
    const reportes = new Map(
      (this.reportes.data() ?? [])
        .filter((reporte) => reporte.estadoActivo)
        .map((reporte) => [reporte.idEquipoCliente, reporte]),
    );
    const grupos = new Map<string, { area: string; equipos: EquipoDelAlcance[] }>();
    const documento = this.documento();

    if (documento) {
      // Ya ordenados por el servidor: área, hoja de vida y serie.
      for (const equipo of documento.equipos ?? []) {
        const reporte = reportes.get(equipo.id);
        const area = equipo.area ?? 'Área no disponible';
        const grupo = grupos.get(area) ?? { area, equipos: [] };
        // Se puede sustituir un reporte cerrado después de ejecutar (decidido el 2026-10-28): el
        // documento conserva el número de entonces, y la ficha dice que ya no es el vigente.
        const sustituido =
          !!equipo.numeroReporte && !!reporte?.numero && reporte.numero !== equipo.numeroReporte;

        grupo.equipos.push({
          id: equipo.id!,
          nombre: [equipo.tipo, equipo.marca, equipo.modelo].filter(Boolean).join(' '),
          detalle: [
            equipo.numeroHojaVida,
            equipo.serie ? `Serie ${equipo.serie}` : '',
            sustituido ? 'Su reporte se sustituyó después de ejecutarla' : '',
          ]
            .filter(Boolean)
            .join(' · '),
          deBaja: false,
          idReporte: reporte?.id,
          ...estadoDelReporte(reporte?.estado, reporte?.resultado),
        });
        grupos.set(area, grupo);
      }

      return [...grupos.values()];
    }

    for (const equipo of this.orden.data()?.equipos ?? []) {
      const fila = resumen.get(equipo.idEquipoCliente!);
      const reporte = reportes.get(equipo.idEquipoCliente!);
      const clave = fila?.idAreaServicio ?? '';
      const grupo = grupos.get(clave) ?? {
        area: fila?.areaServicio ?? 'Área no disponible',
        equipos: [],
      };

      grupo.equipos.push({
        id: equipo.idEquipoCliente!,
        ...describir(fila),
        idReporte: equipo.idReporteServicio ?? reporte?.id,
        ...estadoDelReporte(reporte?.estado, reporte?.resultado),
      });
      grupos.set(clave, grupo);
    }

    return [...grupos.values()].sort((a, b) => a.area.localeCompare(b.area, 'es'));
  });

  protected readonly totalDeEquipos = computed(
    () => this.documento()?.equipos?.length ?? this.orden.data()?.equipos?.length ?? 0,
  );
  protected readonly cerrados = computed(
    () =>
      this.grupos()
        .flatMap((grupo) => grupo.equipos)
        .filter((equipo) => equipo.cerrado).length,
  );
  protected readonly avance = computed(() =>
    this.totalDeEquipos() ? Math.round((this.cerrados() / this.totalDeEquipos()) * 100) : 0,
  );

  /**
   * Los encargados del sitio: los de la sede y los de las áreas que la orden toca. El API no filtra
   * por sede, así que se recorta aquí, igual que en la ficha de la sede.
   */
  protected readonly encargadosDelSitio = computed(() => {
    const datos = this.orden.data();
    const nombresDeArea = new Map(
      (this.resumen.data()?.filas ?? []).map((fila) => [fila.idAreaServicio, fila.areaServicio]),
    );
    // Las áreas de sus equipos, del listado pedido por los equipos de la orden: la orden ya no las guarda.
    const idsDeArea = new Set(
      (this.resumen.data()?.filas ?? []).map((fila) => fila.idAreaServicio),
    );
    const porPersona = new Map((this.personas.data() ?? []).map((p) => [p.identificador, p]));

    // Una fila por cada cosa de la que responde en este sitio: desde el 2026-10-27 un encargado puede
    // estarlo de la sede y de varias areas a la vez.
    return (this.encargados.data() ?? []).flatMap((encargado) =>
      asignacionesDe(encargado)
        .filter(({ tipo, idAsignacion }) =>
          tipo === 'HEADQUARTER' ? idAsignacion === datos?.idSede : idsDeArea.has(idAsignacion),
        )
        .map(({ tipo, idAsignacion }) => ({
          id: encargado.idPersona!,
          nombre: porPersona.has(encargado.idPersona!)
            ? nombreCompleto(porPersona.get(encargado.idPersona!)!)
            : 'Nombre no disponible',
          aCargoDe:
            tipo === 'SERVICE_AREA'
              ? (nombresDeArea.get(idAsignacion) ?? 'Un área de la orden')
              : 'Toda la sede',
        })),
    );
  });

  protected readonly hayError = computed(
    () =>
      this.orden.isError() ||
      this.inicio.isError() ||
      this.ejecucion.isError() ||
      this.anulacion.isError() ||
      this.bajaDeEquipo.isError() ||
      this.asignacion.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.orden.error() ??
        this.inicio.error() ??
        this.ejecucion.error() ??
        this.anulacion.error() ??
        this.bajaDeEquipo.error() ??
        this.asignacion.error(),
    ),
  );

  protected iniciar(): void {
    this.inicio.mutate(this.id());
  }

  protected ejecutar(): void {
    this.ejecucion.mutate(this.id());
  }

  protected anular(): void {
    this.anulacion.mutate(this.id(), {
      // Se vuelve al listado: quedarse en una orden anulada invita a seguir tocándola.
      onSuccess: () => void this.router.navigate(['/ordenes']),
    });
  }

  protected quitarEquipo(idEquipoCliente: string): void {
    this.bajaDeEquipo.mutate({ id: this.id(), idEquipoCliente });
  }

  protected asignar(): void {
    const idIngeniero = this.ingenieroElegido.value;

    if (!idIngeniero) {
      return;
    }

    this.asignacion.mutate(
      { id: this.id(), idIngeniero },
      { onSuccess: () => this.asignando.set(false) },
    );
  }
}

interface EquipoDelAlcance {
  readonly id: string;
  readonly nombre: string;
  readonly detalle: string;
  readonly idReporte?: string;
  readonly reporte: string;
  readonly peso: string;
  readonly cuadro: string;
  readonly cerrado: boolean;
  readonly deBaja: boolean;
}

/** «Balanza Kern PCB 3500-2» y «HV-CDN-0001 · Serie WB1240337». */
function describir(fila: FilaDeResumenDeEquipos | undefined) {
  if (!fila) {
    return { nombre: 'Equipo no disponible', detalle: '', deBaja: false };
  }

  return {
    nombre: [fila.tipoEquipo, fila.marca, fila.modelo].filter(Boolean).join(' '),
    detalle: [fila.numeroHojaVida, fila.serie ? `Serie ${fila.serie}` : '']
      .filter(Boolean)
      .join(' · '),
    deBaja: !fila.estadoActivo,
  };
}

/**
 * Cómo va el reporte de un equipo, con el peso y el cuadro del manual: lo pendiente pesa más que lo
 * cerrado, porque es lo que hay que mirar.
 */
function estadoDelReporte(
  estado: string | undefined,
  resultado: keyof typeof ETIQUETA_DE_RESULTADO | undefined,
) {
  if (estado === 'FINALIZADO') {
    return {
      reporte: resultado ? `Cerrado · ${ETIQUETA_DE_RESULTADO[resultado]}` : 'Cerrado',
      peso: 'font-normal',
      cuadro: 'bg-neutral-500',
      cerrado: true,
    };
  }
  if (estado === 'BORRADOR') {
    return { reporte: 'Borrador', peso: 'font-extrabold', cuadro: 'bg-ink', cerrado: false };
  }

  return { reporte: 'Sin reporte', peso: 'font-medium', cuadro: 'bg-transparent', cerrado: false };
}
