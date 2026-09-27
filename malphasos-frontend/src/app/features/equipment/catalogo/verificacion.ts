import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import {
  ETIQUETA_DE_MODALIDAD,
  mantieneAlgoConstante,
  ModalidadDeVerificacion,
  MODALIDADES_DE_VERIFICACION,
  NuevoPuntoDeVerificacion,
} from '../../../core/api/tipos';

/** Cómo se verifica un tipo de equipo, tal como el formulario lo tiene ahora mismo. */
export interface ConfiguracionDeVerificacion {
  readonly modalidad: ModalidadDeVerificacion | null;
  readonly cantidadDatos: number | null;
  readonly puntos: readonly NuevoPuntoDeVerificacion[];
  /** Si lo que hay puesto se puede guardar. El padre no debe enviar una configuración inválida. */
  readonly valida: boolean;
}

/**
 * Los tres datos que dicen cómo se verifica un tipo: modalidad, cuántas lecturas y en qué valores.
 *
 * <p><b>Van juntos porque son una sola decisión</b>, y el backend los recibe juntos por la misma razón:
 * por separado existiría el instante en que un tipo dice verificarse contra un patrón constante sin
 * decir contra qué valor.
 *
 * <p>Las dos modalidades <b>constantes</b> piden cuántas lecturas se toman y al menos un punto; la
 * <b>variable</b> no admite ninguna de las dos, porque cuántas lecturas tomar lo decide el ingeniero en
 * campo y no hay nada constante que declarar. El formulario oculta lo que no aplica en lugar de dejarlo
 * ahí para que el servidor lo rechace.
 *
 * <p><b>La cantidad es por punto, no en total.</b> Se dice en pantalla: confundirlo daría un reporte con
 * un tercio de los datos que hacían falta.
 *
 * <p>Vive aparte porque se usa en dos sitios —el alta de un tipo y el cambio de modalidad desde la
 * lista— y tener la regla en un solo lugar es lo que evita que las dos pantallas se desvíen.
 */
@Component({
  selector: 'app-verificacion',
  templateUrl: './verificacion.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Verificacion {
  /** Lo que ya está guardado, cuando se entra a cambiarlo. */
  readonly inicial = input<ConfiguracionDeVerificacion | null>(null);

  readonly cambio = output<ConfiguracionDeVerificacion>();

  protected readonly modalidades = MODALIDADES_DE_VERIFICACION;
  protected readonly etiquetas = ETIQUETA_DE_MODALIDAD;

  protected readonly modalidad = signal<ModalidadDeVerificacion | ''>('');
  protected readonly cantidad = signal<number | null>(null);
  protected readonly puntos = signal<{ valor: string; unidad: string }[]>([]);

  /** Se marca al intentar guardar: antes de eso no se pinta ningún error. */
  protected readonly intentado = signal(false);

  protected readonly constante = computed(() => mantieneAlgoConstante(this.modalidad()));

  constructor() {
    effect(() => {
      const inicial = this.inicial();

      if (inicial) {
        this.modalidad.set(inicial.modalidad ?? '');
        this.cantidad.set(inicial.cantidadDatos);
        this.puntos.set(
          inicial.puntos.map((punto) => ({
            valor: String(punto.valor ?? ''),
            unidad: punto.unidad ?? '',
          })),
        );
      }
    });

    // El padre se queda con la última configuración válida o inválida: así puede bloquear el envío sin
    // preguntarle nada a este componente.
    effect(() => this.cambio.emit(this.configuracion()));
  }

  /** Los puntos escritos, ya convertidos a números; los incompletos quedan fuera. */
  private readonly puntosLimpios = computed(() =>
    this.puntos()
      .filter((punto) => punto.valor.trim() !== '' && punto.unidad.trim() !== '')
      .map((punto) => ({ valor: Number(punto.valor), unidad: punto.unidad.trim() })),
  );

  protected readonly hayIncompletos = computed(
    () => this.puntos().length > this.puntosLimpios().length,
  );

  /** Dos puntos con el mismo valor y la misma unidad son el mismo punto dos veces. */
  protected readonly hayRepetidos = computed(() => {
    const vistos = new Set<string>();

    return this.puntosLimpios().some((punto) => {
      const clave = `${punto.valor}|${punto.unidad.toLowerCase()}`;
      const repetido = vistos.has(clave);
      vistos.add(clave);

      return repetido;
    });
  });

  protected readonly cantidadFueraDeRango = computed(() => {
    const cantidad = this.cantidad();

    return this.constante() && (cantidad === null || cantidad < 1 || cantidad > 100);
  });

  protected readonly faltanPuntos = computed(
    () => this.constante() && this.puntosLimpios().length === 0,
  );

  private readonly configuracion = computed<ConfiguracionDeVerificacion>(() => {
    const constante = this.constante();

    return {
      modalidad: this.modalidad() === '' ? null : (this.modalidad() as ModalidadDeVerificacion),
      // Fuera de las modalidades constantes, los dos campos viajan vacíos: el backend los rechaza.
      cantidadDatos: constante ? this.cantidad() : null,
      puntos: constante ? this.puntosLimpios() : [],
      valida:
        !constante ||
        (!this.cantidadFueraDeRango() && !this.faltanPuntos() && !this.hayRepetidos()
          && !this.hayIncompletos()),
    };
  });

  protected elegirModalidad(valor: string): void {
    this.modalidad.set(valor as ModalidadDeVerificacion | '');

    if (!mantieneAlgoConstante(this.modalidad())) {
      // Al dejar de ser constante, lo que se había escrito ya no significa nada.
      this.cantidad.set(null);
      this.puntos.set([]);
    } else if (this.puntos().length === 0) {
      this.puntos.set([{ valor: '', unidad: '' }]);
    }
  }

  protected escribirCantidad(valor: string): void {
    this.cantidad.set(valor === '' ? null : Number(valor));
  }

  protected escribirPunto(indice: number, campo: 'valor' | 'unidad', valor: string): void {
    this.puntos.update((puntos) =>
      puntos.map((punto, i) => (i === indice ? { ...punto, [campo]: valor } : punto)),
    );
  }

  protected agregarPunto(): void {
    this.puntos.update((puntos) => [...puntos, { valor: '', unidad: '' }]);
  }

  protected quitarPunto(indice: number): void {
    this.puntos.update((puntos) => puntos.filter((_, i) => i !== indice));
  }

  /** El padre la llama al intentar guardar, para que se pinten los errores. */
  marcarIntento(): void {
    this.intentado.set(true);
  }
}
