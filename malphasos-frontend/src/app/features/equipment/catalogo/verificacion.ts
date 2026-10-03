import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChildren,
} from '@angular/core';
import { CatalogoApi } from '../catalogo-api';
import { Magnitud, NuevaVerificacionDeTipo } from '../../../core/api/tipos';
import { PanelDeVerificacion, VerificacionEnCurso } from './panel-de-verificacion';

/** Qué se verifica en un tipo de equipo, tal como el formulario lo tiene ahora mismo. */
export interface ConfiguracionDeVerificacion {
  readonly verificaciones: readonly NuevaVerificacionDeTipo[];
  /** Si lo que hay puesto se puede guardar. El padre no debe enviar una configuración inválida. */
  readonly valida: boolean;
}

/**
 * Qué se verifica en un tipo de equipo: **cuántas cosas, y cada una en su panel**.
 *
 * <p><b>Lo que cambió el 2026-10-03.</b> Este bloque declaraba una modalidad, una cantidad de lecturas
 * y unos puntos con la unidad escrita en cada uno — es decir, daba por supuesto que un aparato mide una
 * sola cosa. Un termohigrómetro mide temperatura y humedad relativa, y con aquel formulario había que
 * registrarlo como dos tipos de equipo. Ahora se dice cuántas verificaciones tiene y cada una se
 * configura en su panel: magnitud, unidad, modalidad, lecturas por punto y en qué valores.
 *
 * <p><b>El contador no se guarda.</b> Ni este ni el de los puntos: son controles que despliegan
 * campos, y lo que se envía es la lista. Un número que tiene que coincidir con el número de elementos
 * se desincroniza el día que alguien añade uno por otro camino, y la cuenta siempre se puede derivar
 * contando.
 *
 * <p><b>Una magnitud no se ofrece dos veces.</b> Un tipo de equipo se verifica una sola vez en cada
 * magnitud —el servidor lo rechaza y el esquema lo impide con un índice único—, así que cada panel ve
 * el catálogo menos lo que sus hermanos ya eligieron. La pantalla refleja la regla del servidor en vez
 * de descubrirla a golpes.
 *
 * <p>Vive aparte porque se usa en dos sitios —el alta de un tipo y el cambio desde la lista— y tener la
 * regla en un solo lugar es lo que evita que las dos pantallas se desvíen.
 */
@Component({
  selector: 'app-verificacion',
  imports: [PanelDeVerificacion],
  templateUrl: './verificacion.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Verificacion {
  private readonly api = inject(CatalogoApi);

  /** Lo que ya está guardado, cuando se entra a cambiarlo. */
  readonly inicial = input<readonly VerificacionEnCurso[] | null>(null);

  readonly cambio = output<ConfiguracionDeVerificacion>();

  protected readonly magnitudes = this.api.listarMagnitudes();

  /** Un hueco por panel. Lo que cada panel tenga puesto llega por su salida. */
  protected readonly estados = signal<(VerificacionEnCurso | null)[]>([]);

  /** Lo que el padre pasó para rellenar cada panel. Se indexa por posición. */
  protected readonly iniciales = signal<readonly VerificacionEnCurso[]>([]);

  private readonly paneles = viewChildren(PanelDeVerificacion);

  constructor() {
    effect(() => {
      const inicial = this.inicial();

      if (inicial) {
        this.iniciales.set(inicial);
        this.estados.set(inicial.map(() => null));
      }
    });

    // El padre se queda con lo último, válido o no: así puede bloquear el envío sin preguntar nada.
    effect(() => this.cambio.emit(this.configuracion()));
  }

  /** Cuántas verificaciones hay ahora mismo. Es el valor del contador, derivado de los paneles. */
  protected readonly cuantas = computed(() => this.estados().length);

  /**
   * Las magnitudes que puede elegir el panel de esa posición: todas menos las que usan sus hermanos.
   *
   * <p>La propia incluida, claro: si no, cambiar cualquier otro campo del panel haría desaparecer la
   * magnitud ya elegida de su propia lista.
   */
  protected disponiblesPara(posicion: number): readonly Magnitud[] {
    const tomadas = new Set(
      this.estados()
        .map((estado, indice) => (indice === posicion ? null : estado?.magnitudId))
        .filter((id): id is string => !!id),
    );

    return (this.magnitudes.data() ?? []).filter((magnitud) => !tomadas.has(magnitud.id!));
  }

  protected inicialPara(posicion: number): VerificacionEnCurso | null {
    return this.iniciales()[posicion] ?? null;
  }

  private readonly configuracion = computed<ConfiguracionDeVerificacion>(() => {
    const estados = this.estados();

    return {
      // Solo las completas se traducen; si alguna no lo está, `valida` es falso y el padre no envía.
      verificaciones: estados
        .filter((estado): estado is VerificacionEnCurso => !!estado?.valida)
        .map((estado) => ({
          magnitudId: estado.magnitudId!,
          unidadId: estado.unidadId!,
          modalidad: estado.modalidad!,
          cantidadDatos: estado.cantidadDatos ?? undefined,
          puntos: estado.puntos.map((valor) => ({ valor })),
        })),
      // Ninguna verificación es válido: significa que al tipo no se le verifica nada.
      valida: estados.every((estado) => !!estado?.valida),
    };
  });

  /**
   * Cuántas verificaciones tiene el tipo. Subirlo añade paneles vacíos; bajarlo quita los últimos.
   *
   * <p>Cero es una respuesta legítima y significa que a este tipo no se le verifica nada — es lo que
   * antes se decía dejando la modalidad vacía.
   */
  protected escribirCuantas(valor: string): void {
    const cuantas = Math.max(0, Math.min(20, valor === '' ? 0 : Number(valor)));

    this.estados.update((estados) =>
      cuantas <= estados.length
        ? estados.slice(0, cuantas)
        : [...estados, ...Array(cuantas - estados.length).fill(null)],
    );
  }

  protected anotar(posicion: number, estado: VerificacionEnCurso): void {
    this.estados.update((estados) =>
      estados.map((anterior, indice) => (indice === posicion ? estado : anterior)),
    );
  }

  /** El padre la llama al intentar guardar, para que cada panel pinte sus errores. */
  marcarIntento(): void {
    this.paneles().forEach((panel) => panel.marcarIntento());
  }
}
