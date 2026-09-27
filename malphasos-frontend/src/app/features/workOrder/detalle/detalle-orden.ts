import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AreaApi } from '../../client/area-api';
import { ClienteApi } from '../../client/cliente-api';
import { SedeApi } from '../../client/sede-api';
import { EquipoApi } from '../../equipment/equipo-api';
import { OrdenApi } from '../orden-api';
import { ReporteApi } from '../../report/reporte-api';
import { nombreCompleto, PersonaApi } from '../../person/persona-api';
import { Sesion } from '../../../core/auth/sesion';
import {
  ETIQUETA_DE_ESTADO,
  ETIQUETA_DE_ESTADO_DE_REPORTE,
  ETIQUETA_DE_PERIODICIDAD,
  ETIQUETA_DE_TIPO_DE_SERVICIO,
} from '../../../core/api/tipos';
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
 * <p>Cada equipo del alcance llega como identificador; la serie y el área se resuelven cruzando la lista
 * de equipos y las áreas de la sede, que es la tercera vez que este proyecto cruza listas por lo mismo.
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

  protected readonly inicio = this.api.iniciar();
  protected readonly ejecucion = this.api.ejecutar();
  protected readonly anulacion = this.api.anular();
  protected readonly bajaDeEquipo = this.api.quitarEquipo();
  protected readonly asignacion = this.api.asignarIngeniero();

  private readonly clientes = inject(ClienteApi).listar();
  private readonly sedes = inject(SedeApi).variasPorId(
    computed(() => {
      const sede = this.orden.data()?.idSede;

      return sede ? [sede] : [];
    }),
  );
  private readonly areas = inject(AreaApi).variasPorId(
    computed(() => [
      ...new Set(
        (this.orden.data()?.equipos ?? [])
          .map((equipo) => equipo.idAreaServicio!)
          .filter(Boolean),
      ),
    ]),
  );
  private readonly equipos = inject(EquipoApi).listarTodos();
  private readonly personas = inject(PersonaApi).listar();

  private readonly reporteApi = inject(ReporteApi);
  private readonly reportes = this.reporteApi.porOrden(this.id);
  protected readonly apertura = this.reporteApi.abrir();

  protected readonly puedeEscribir = computed(() => this.sesion.puede('work-order.write'));
  protected readonly puedeAsignar = computed(() => this.sesion.puede('work-order.assign'));
  protected readonly puedeReportar = computed(() => this.sesion.puede('report.write'));

  /**
   * Un reporte cuenta lo que se hizo, y en una orden sin empezar no se ha hecho nada: es la regla que
   * el servidor impone al abrirlo. Aqui se refleja para no ofrecer un boton que responderia 409.
   */
  protected readonly sePuedeReportar = computed(
    () => this.orden.data()?.estadoEjecucion !== 'CREADA' && !!this.orden.data()?.estadoActivo,
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
    const id = this.orden.data()?.idCliente;

    return (
      (this.clientes.data() ?? []).find((cliente) => cliente.id === id)?.razonSocial ??
      'No disponible'
    );
  });

  protected readonly sede = computed(
    () => (this.sedes.data() ?? [])[0]?.nombre ?? 'No disponible',
  );

  protected readonly estado = computed(() => {
    const estado = this.orden.data()?.estadoEjecucion;

    return estado ? ETIQUETA_DE_ESTADO[estado] : '';
  });

  protected readonly servicio = computed(() => {
    const datos = this.orden.data();
    const tipo = datos?.tipoServicio ? ETIQUETA_DE_TIPO_DE_SERVICIO[datos.tipoServicio] : '';
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

  /** El ingeniero asignado, con nombre. Sin él, la orden está programada y no repartida. */
  protected readonly ingeniero = computed(() => {
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

  /**
   * El alcance: qué equipos toca esta orden, con su serie, su área y su reporte.
   *
   * <p>El reporte que cuenta es <b>el vivo</b>: la consulta trae también los retirados, porque el API
   * devuelve el historial completo de la orden, y un equipo cuyo reporte se retiró vuelve a estar sin
   * reporte. Quedarse con el primero de la lista mostraría el retirado y el botón de abrir
   * desaparecería para siempre.
   */
  protected readonly alcance = computed(() => {
    const equipos = new Map((this.equipos.data() ?? []).map((equipo) => [equipo.id, equipo.serie]));
    const areas = new Map((this.areas.data() ?? []).map((area) => [area.id, area.nombre]));
    const reportes = new Map(
      (this.reportes.data() ?? [])
        .filter((reporte) => reporte.estadoActivo)
        .map((reporte) => [reporte.idEquipoCliente, reporte]),
    );

    return (this.orden.data()?.equipos ?? []).map((equipo) => {
      const reporte = reportes.get(equipo.idEquipoCliente!);

      return {
        id: equipo.idEquipoCliente!,
        serie: equipos.get(equipo.idEquipoCliente!) ?? 'Equipo no disponible',
        area: areas.get(equipo.idAreaServicio!) ?? 'Área no disponible',
        idReporte: reporte?.id,
        estadoDelReporte: reporte?.estado ? ETIQUETA_DE_ESTADO_DE_REPORTE[reporte.estado] : '',
      };
    });
  });

  protected readonly hayError = computed(
    () =>
      this.orden.isError() ||
      this.inicio.isError() ||
      this.ejecucion.isError() ||
      this.anulacion.isError() ||
      this.bajaDeEquipo.isError() ||
      this.asignacion.isError() ||
      this.apertura.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.orden.error() ??
        this.inicio.error() ??
        this.ejecucion.error() ??
        this.anulacion.error() ??
        this.bajaDeEquipo.error() ??
        this.asignacion.error() ??
        this.apertura.error(),
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

  /** Abre el reporte de un equipo y lleva a él: lo que sigue es llenarlo, no volver al listado. */
  protected abrirReporte(idEquipoCliente: string): void {
    this.apertura.mutate(
      { idOrdenTrabajo: this.id(), idEquipoCliente },
      {
        onSuccess: (reporte) => void this.router.navigate(['/reportes', reporte.id]),
      },
    );
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
