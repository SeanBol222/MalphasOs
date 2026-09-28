import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { CatalogoApi } from '../../equipment/catalogo-api';
import { EquipoApi } from '../../equipment/equipo-api';
import { ReporteApi } from '../reporte-api';
import {
  ETIQUETA_DE_MODALIDAD,
  LecturaDeVerificacion,
  mantieneAlgoConstante,
  NuevaLectura,
} from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';

/** Una casilla de la tabla: dónde se mide y cuál de las N lecturas de ese punto es. */
interface Fila {
  readonly idPuntoVerificacion: string | null;
  readonly punto: string;
  readonly secuencia: number;
  readonly unidad: string;
}

/**
 * La verificación metrológica de un reporte: lo que marcó el patrón y lo que marcó el equipo.
 *
 * <p><b>La tabla no se inventa: la dicta el tipo del equipo.</b> El tipo declara en qué puntos se
 * verifica —a 50, a 100 y a 150 mmHg— y cuántas lecturas se toman en cada uno, así que con tres puntos
 * y dos lecturas hay seis casillas y no más. Eso evita el error que el servidor rechazaría: una lectura
 * en un punto que ese tipo no declara, o la número cuatro donde se piden tres.
 *
 * <p><b>Averiguar cómo se verifica un equipo cuesta cuatro consultas</b>: unidad → modelo → equipo del
 * catálogo → tipo. Así está construida la cadena del catálogo —«equipo» significa categoría y
 * máquina— y ninguna tabla intermedia guarda un atajo. El backend camina la misma cadena en un solo
 * método por la misma razón: para que el coste esté a la vista. Las cuatro listas están en caché
 * porque otras pantallas ya las piden.
 *
 * <p><b>Con patrón y equipo variables no hay puntos</b> —el tipo no los declara y el servidor los
 * rechazaría— y cuántas lecturas tomar lo decide el ingeniero: la tabla crece a mano y cada fila lleva
 * su unidad, porque no hay punto de donde copiarla.
 *
 * <p>Se manda la tabla <b>entera</b> y sustituye la anterior: lo que no se envíe queda retirado. Es lo
 * que hace el API, y encaja con cómo se revisa una verificación — se corrige un valor y se entrega la
 * tabla completa.
 */
@Component({
  selector: 'app-tabla-de-verificacion',
  imports: [ReactiveFormsModule],
  templateUrl: './tabla-de-verificacion.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablaDeVerificacion {
  readonly idReporte = input.required<string>();
  readonly idEquipoCliente = input.required<string>();
  readonly lecturas = input<readonly LecturaDeVerificacion[]>([]);
  readonly editable = input(false);

  private readonly fb = inject(FormBuilder);
  private readonly catalogo = inject(CatalogoApi);

  protected readonly registro = inject(ReporteApi).registrarVerificacion();

  // --- La cadena del catalogo, cuatro eslabones -------------------------------

  private readonly equipos = inject(EquipoApi).listarTodos();
  private readonly modelos = this.catalogo.listarModelos();
  private readonly equiposDeCatalogo = this.catalogo.listarEquipos();
  private readonly tipos = this.catalogo.listarTipos();

  /** El tipo del equipo reportado, o {@code undefined} mientras la cadena no esté completa. */
  protected readonly tipo = computed(() => {
    const unidad = (this.equipos.data() ?? []).find((equipo) => equipo.id === this.idEquipoCliente());
    const modelo = (this.modelos.data() ?? []).find((m) => m.id === unidad?.idModelo);
    const delCatalogo = (this.equiposDeCatalogo.data() ?? []).find((e) => e.id === modelo?.idEquipo);

    return (this.tipos.data() ?? []).find((t) => t.id === delCatalogo?.idTipoEquipo);
  });

  protected readonly cargando = computed(
    () =>
      this.equipos.isPending() ||
      this.modelos.isPending() ||
      this.equiposDeCatalogo.isPending() ||
      this.tipos.isPending(),
  );

  /** Un tipo sin modalidad no se verifica: no hay nada que medirle, y el API lo rechazaría. */
  protected readonly seVerifica = computed(() => !!this.tipo()?.modalidadVerificacion);

  protected readonly modalidad = computed(() => {
    const modalidad = this.tipo()?.modalidadVerificacion;

    return modalidad ? ETIQUETA_DE_MODALIDAD[modalidad] : '';
  });

  /** Con patrón y equipo variables, la tabla la construye el ingeniero fila a fila. */
  protected readonly aMano = computed(
    () => this.seVerifica() && !mantieneAlgoConstante(this.tipo()?.modalidadVerificacion),
  );

  // --- La tabla ---------------------------------------------------------------

  protected readonly filas = signal<readonly Fila[]>([]);

  protected readonly formulario = this.fb.group({ casillas: this.fb.array([] as FormGroup[]) });

  protected get casillas(): FormArray<FormGroup> {
    return this.formulario.get('casillas') as FormArray<FormGroup>;
  }

  protected readonly hayError = computed(() => this.registro.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.registro.error()));

  /**
   * Lo escrito en la tabla, como senal.
   *
   * <p><b>Un formulario reactivo no es una senal</b>: un {@code computed()} sobre {@code getRawValue()}
   * no se recalcula nunca, porque nada le dice que algo cambio. Es la convencion del proyecto, y aqui
   * hace falta para poder desactivar el boton mientras no haya ninguna casilla completa.
   */
  private readonly escrito = toSignal(this.formulario.valueChanges);

  /** Sin ninguna casilla con las dos lecturas no hay nada que mandar, y el API rechaza la lista vacía. */
  protected readonly hayAlgoQueMandar = computed(() => {
    this.escrito();

    return this.lecturasEscritas().length > 0;
  });

  constructor() {
    /**
     * Arma la tabla que el tipo dicta y la rellena con lo ya medido.
     *
     * <p>Se rehace solo mientras nadie haya escrito: sin la guarda, la recarga que sigue a cada
     * registro pisaría lo que la persona está tecleando. Es la misma guarda que la ficha del reporte,
     * y por el mismo motivo.
     */
    effect(() => {
      const tipo = this.tipo();

      if (!tipo || this.formulario.dirty) {
        return;
      }

      const medidas = this.lecturas();

      if (mantieneAlgoConstante(tipo.modalidadVerificacion)) {
        const puntos = tipo.puntosVerificacion ?? [];
        const cuantas = tipo.cantidadDatos ?? 1;

        this.rehacer(
          puntos.flatMap((punto) =>
            Array.from({ length: cuantas }, (_, indice) => ({
              idPuntoVerificacion: punto.id!,
              punto: `${punto.valor} ${punto.unidad}`,
              secuencia: indice + 1,
              unidad: punto.unidad ?? '',
            })),
          ),
          medidas,
        );

        return;
      }

      // Modalidad variable: las filas son las que ya se midieron, o una en blanco para empezar.
      const tomadas = medidas.filter((lectura) => !lectura.idPuntoVerificacion);

      this.rehacer(
        tomadas.length
          ? tomadas.map((lectura) => ({
              idPuntoVerificacion: null,
              punto: 'Sin punto fijo',
              secuencia: lectura.secuencia ?? 1,
              unidad: lectura.unidad ?? '',
            }))
          : [{ idPuntoVerificacion: null, punto: 'Sin punto fijo', secuencia: 1, unidad: '' }],
        medidas,
      );
    });

    effect(() => this.aplicarEstado(this.editable()));
  }

  /**
   * Deja el formulario abierto o cerrado, segun se pueda escribir.
   *
   * <p>Se llama tambien <b>al rehacer la tabla</b>, y no solo cuando cambia {@code editable}: las
   * casillas se crean cuando llega el tipo, que es despues, y <b>un control anadido a un formulario ya
   * desactivado nace activo</b>. Sin esto, la tabla de un reporte cerrado se podia teclear: el servidor
   * lo habria rechazado, pero la pantalla estaria ofreciendo algo que no existe. Lo encontro la prueba
   * del caso no editable.
   */
  private aplicarEstado(editable: boolean): void {
    if (editable) {
      this.formulario.enable({ emitEvent: false });
    } else {
      this.formulario.disable({ emitEvent: false });
    }
  }

  /** Añade una lectura más, solo con modalidad variable: ahí el número lo decide quien mide. */
  protected anadirFila(): void {
    const siguiente = Math.max(0, ...this.filas().map((fila) => fila.secuencia)) + 1;

    this.filas.update((filas) => [
      ...filas,
      { idPuntoVerificacion: null, punto: 'Sin punto fijo', secuencia: siguiente, unidad: '' },
    ]);
    this.casillas.push(this.grupoVacio());
    this.formulario.markAsDirty();
  }

  protected guardar(): void {
    const lecturas = this.lecturasEscritas();

    if (!lecturas.length) {
      return;
    }

    this.registro.mutate(
      { id: this.idReporte(), lecturas },
      { onSuccess: () => this.formulario.markAsPristine() },
    );
  }

  /**
   * Las casillas que tienen las dos lecturas escritas.
   *
   * <p>Una casilla a medias —patrón sin equipo— no se manda: el servidor exige las dos, y mandarla
   * incompleta cambiaría un aviso claro por un 400. Y las que se dejan en blanco se omiten a
   * propósito: una tabla a medio llenar se guarda y se termina después.
   */
  private lecturasEscritas(): NuevaLectura[] {
    return this.casillas.controls.flatMap((grupo, indice) => {
      const fila = this.filas()[indice];
      const patron = grupo.get('valorPatron')?.value;
      const equipo = grupo.get('valorEquipo')?.value;
      const unidad = grupo.get('unidad')?.value ?? fila.unidad;

      if (patron === '' || patron == null || equipo === '' || equipo == null) {
        return [];
      }

      return [
        {
          idPuntoVerificacion: fila.idPuntoVerificacion ?? undefined,
          secuencia: fila.secuencia,
          valorPatron: Number(patron),
          valorEquipo: Number(equipo),
          unidadSinPunto: fila.idPuntoVerificacion ? undefined : unidad,
        },
      ];
    });
  }

  private rehacer(filas: readonly Fila[], medidas: readonly LecturaDeVerificacion[]): void {
    this.filas.set(filas);
    this.casillas.clear({ emitEvent: false });

    for (const fila of filas) {
      const medida = medidas.find(
        (lectura) =>
          (lectura.idPuntoVerificacion ?? null) === fila.idPuntoVerificacion &&
          lectura.secuencia === fila.secuencia,
      );

      this.casillas.push(
        this.fb.group({
          valorPatron: this.fb.nonNullable.control(medida?.valorPatron?.toString() ?? ''),
          valorEquipo: this.fb.nonNullable.control(medida?.valorEquipo?.toString() ?? ''),
          unidad: this.fb.nonNullable.control(medida?.unidad ?? fila.unidad),
        }),
        { emitEvent: false },
      );
    }

    // Y se vuelve a aplicar el estado: las casillas acaban de nacer y lo hacen activas.
    this.aplicarEstado(this.editable());
  }

  private grupoVacio(): FormGroup {
    return this.fb.group({
      valorPatron: this.fb.nonNullable.control(''),
      valorEquipo: this.fb.nonNullable.control(''),
      unidad: this.fb.nonNullable.control(''),
    });
  }
}
