import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { CatalogoApi } from '../catalogo-api';
import { inject } from '@angular/core';
import {
  ETIQUETA_DE_MODALIDAD,
  Magnitud,
  mantieneAlgoConstante,
  ModalidadDeVerificacion,
  MODALIDADES_DE_VERIFICACION,
  NuevaVerificacionDeTipo,
} from '../../../core/api/tipos';

/** Una verificación tal como el panel la tiene ahora mismo, con su propio veredicto de validez. */
export interface VerificacionEnCurso {
  readonly magnitudId: string | null;
  readonly unidadId: string | null;
  readonly modalidad: ModalidadDeVerificacion | null;
  readonly cantidadDatos: number | null;
  readonly puntos: readonly number[];
  readonly valida: boolean;
}

/**
 * Un panel: **una** de las cosas que se verifican en un tipo de equipo.
 *
 * <p><b>Por qué existe este componente.</b> Hasta el 2026-10-03 la pantalla daba por supuesto que un
 * aparato mide una sola cosa: había una modalidad, una cantidad de lecturas y unos puntos con la unidad
 * escrita a mano en cada uno. Un termohigrómetro lo desmiente —mide temperatura y humedad relativa— y
 * con aquel formulario había que registrarlo como dos tipos de equipo, que es mentira: es un aparato,
 * con una hoja de vida y un reporte.
 *
 * <p><b>La unidad se elige una vez, no por punto.</b> Era la queja concreta: tres puntos de presión
 * obligaban a teclear «mmHg» tres veces. Ahora la declara el panel y los puntos solo llevan su valor,
 * de modo que dos puntos hermanos ya no pueden contradecirse.
 *
 * <p><b>Las unidades que se ofrecen son las de la magnitud elegida</b>, y eso no es comodidad: el
 * esquema del servidor ata el par (magnitud, unidad) con una foránea compuesta, así que «%HR» para una
 * verificación de temperatura es imposible de guardar. Ofrecerlo sería ofrecer un error.
 *
 * <p><b>Cuántos puntos es un contador, no una lista con botones.</b> Se escribe «3» y aparecen tres
 * casillas de valor. Bajar el número quita las últimas, y se avisa en pantalla: nada se ha guardado
 * todavía, así que el coste de equivocarse es volver a teclear.
 */
@Component({
  selector: 'app-panel-de-verificacion',
  templateUrl: './panel-de-verificacion.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelDeVerificacion {
  private readonly api = inject(CatalogoApi);

  /** Qué número de panel es, para los textos y los identificadores de los controles. */
  readonly posicion = input.required<number>();

  /** Lo que ya está guardado en esta posición, cuando se entra a cambiarlo. */
  readonly inicial = input<VerificacionEnCurso | null>(null);

  /**
   * Las magnitudes que este panel puede elegir: las del catálogo menos las que ya usan sus hermanos.
   *
   * <p>Un tipo de equipo no declara dos veces la misma magnitud —el servidor lo rechaza y el esquema lo
   * impide con un índice único—, de modo que ofrecerla sería ofrecer un error.
   */
  readonly disponibles = input.required<readonly Magnitud[]>();

  readonly cambio = output<VerificacionEnCurso>();

  protected readonly modalidades = MODALIDADES_DE_VERIFICACION;
  protected readonly etiquetas = ETIQUETA_DE_MODALIDAD;

  protected readonly magnitudId = signal<string>('');
  protected readonly unidadId = signal<string>('');
  protected readonly modalidad = signal<ModalidadDeVerificacion | ''>('');
  protected readonly cantidad = signal<number | null>(null);
  protected readonly valores = signal<string[]>([]);

  /** Se marca cuando el padre intenta guardar: antes de eso no se pinta ningún error. */
  protected readonly intentado = signal(false);

  protected readonly constante = computed(() => mantieneAlgoConstante(this.modalidad()));

  /** Las unidades de la magnitud elegida. Sin magnitud no se consulta nada. */
  protected readonly unidades = this.api.listarUnidades(() => this.magnitudId() || undefined);

  constructor() {
    effect(() => {
      const inicial = this.inicial();

      if (inicial) {
        this.magnitudId.set(inicial.magnitudId ?? '');
        this.unidadId.set(inicial.unidadId ?? '');
        this.modalidad.set(inicial.modalidad ?? '');
        this.cantidad.set(inicial.cantidadDatos);
        this.valores.set(inicial.puntos.map((valor) => String(valor)));
      }
    });

    // El padre se queda con lo último, válido o no: así puede bloquear el envío sin preguntar nada.
    effect(() => this.cambio.emit(this.estado()));
  }

  /** Los valores escritos, ya convertidos a números; los vacíos quedan fuera. */
  private readonly valoresLimpios = computed(() =>
    this.valores()
      .filter((valor) => valor.trim() !== '')
      .map((valor) => Number(valor)),
  );

  protected readonly hayVacios = computed(
    () => this.valores().length > this.valoresLimpios().length,
  );

  /** Dos puntos con el mismo valor son el mismo punto dos veces. Ya no entra la unidad: es común. */
  protected readonly hayRepetidos = computed(() => {
    const vistos = new Set<number>();

    return this.valoresLimpios().some((valor) => {
      const repetido = vistos.has(valor);
      vistos.add(valor);

      return repetido;
    });
  });

  protected readonly faltaMagnitud = computed(() => this.magnitudId() === '');
  protected readonly faltaUnidad = computed(() => this.unidadId() === '');
  protected readonly faltaModalidad = computed(() => this.modalidad() === '');

  protected readonly cantidadFueraDeRango = computed(() => {
    const cantidad = this.cantidad();

    return this.constante() && (cantidad === null || cantidad < 1 || cantidad > 100);
  });

  protected readonly faltanPuntos = computed(
    () => this.constante() && this.valoresLimpios().length === 0,
  );

  private readonly estado = computed<VerificacionEnCurso>(() => {
    const constante = this.constante();

    return {
      magnitudId: this.magnitudId() || null,
      unidadId: this.unidadId() || null,
      modalidad: this.modalidad() === '' ? null : (this.modalidad() as ModalidadDeVerificacion),
      // Fuera de las modalidades constantes los dos campos viajan vacíos: el servidor los rechaza.
      cantidadDatos: constante ? this.cantidad() : null,
      puntos: constante ? this.valoresLimpios() : [],
      valida:
        !this.faltaMagnitud() &&
        !this.faltaUnidad() &&
        !this.faltaModalidad() &&
        (!constante ||
          (!this.cantidadFueraDeRango() &&
            !this.faltanPuntos() &&
            !this.hayRepetidos() &&
            !this.hayVacios())),
    };
  });

  /**
   * Al cambiar de magnitud se suelta la unidad.
   *
   * <p>No es limpieza por gusto: una unidad de la magnitud anterior no pertenece a la nueva, y
   * conservarla dejaría el panel en el único estado que el servidor devuelve con un 409.
   */
  protected elegirMagnitud(valor: string): void {
    this.magnitudId.set(valor);
    this.unidadId.set('');
  }

  protected elegirUnidad(valor: string): void {
    this.unidadId.set(valor);
  }

  protected elegirModalidad(valor: string): void {
    this.modalidad.set(valor as ModalidadDeVerificacion | '');

    if (!mantieneAlgoConstante(this.modalidad())) {
      // Al dejar de ser constante, lo que se había escrito ya no significa nada.
      this.cantidad.set(null);
      this.valores.set([]);
    } else if (this.valores().length === 0) {
      this.valores.set(['']);
    }
  }

  protected escribirCantidad(valor: string): void {
    this.cantidad.set(valor === '' ? null : Number(valor));
  }

  /** Cuántos puntos hay. Subirlo añade casillas vacías al final; bajarlo quita las últimas. */
  protected escribirCuantosPuntos(valor: string): void {
    const cuantos = Math.max(0, Math.min(100, valor === '' ? 0 : Number(valor)));

    this.valores.update((valores) =>
      cuantos <= valores.length
        ? valores.slice(0, cuantos)
        : [...valores, ...Array(cuantos - valores.length).fill('')],
    );
  }

  protected escribirValor(indice: number, valor: string): void {
    this.valores.update((valores) => valores.map((v, i) => (i === indice ? valor : v)));
  }

  /** El padre la llama al intentar guardar, para que se pinten los errores. */
  marcarIntento(): void {
    this.intentado.set(true);
  }

  /** Lo que el servidor espera de este panel. El padre solo lo pide si todos son válidos. */
  aPeticion(): NuevaVerificacionDeTipo {
    const estado = this.estado();

    return {
      magnitudId: estado.magnitudId!,
      unidadId: estado.unidadId!,
      modalidad: estado.modalidad!,
      cantidadDatos: estado.cantidadDatos ?? undefined,
      puntos: estado.puntos.map((valor) => ({ valor })),
    };
  }
}
