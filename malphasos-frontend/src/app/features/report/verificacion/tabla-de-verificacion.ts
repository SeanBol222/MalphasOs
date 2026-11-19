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
import { tipoDelEquipo } from '../tipo-del-equipo';
import { ReporteApi } from '../reporte-api';
import {
  ETIQUETA_DE_MODALIDAD,
  LecturaDeVerificacion,
  mantieneAlgoConstante,
  NuevaLectura,
  PatronUsado,
  patronVencido,
} from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';
import { fechaCorta, fechaIso, HOY } from '../../../core/fechas';

/** Lo que una fila dice de sí misma al compararla: el error, y si cabe en la tolerancia. */
export interface Comparacion {
  /** «+0,3», «−0,4» o «—» si falta alguno de los dos valores. */
  readonly error: string;
  /** Nulo si no hay tolerancia o falta un valor: no se sabe, y no se inventa. */
  readonly conforme: boolean | null;
}

/** Diezmilésimas: los decimales que guarda el servidor, y la escala a la que se compara aquí. */
const ESCALA = 10_000;

const FORMATO_DE_ERROR = new Intl.NumberFormat('es-CO', {
  maximumFractionDigits: 4,
  signDisplay: 'exceptZero',
});

/**
 * El error y la conformidad de una lectura, con la misma regla que el servidor:
 * {@code |equipo − patrón| ≤ tolerancia}, con el borde dentro.
 *
 * <p><b>En enteros de diezmilésimas, no en coma flotante.</b> 50,7 − 50,2 da 0,5000000000000071 en
 * JavaScript, y con una tolerancia de 0,5 la pantalla diría «no conforme» a una lectura que el servidor
 * —que cuenta en decimal— da por conforme.
 */
export function comparar(
  patron: number | null,
  equipo: number | null,
  tolerancia: number | null,
): Comparacion {
  if (patron === null || equipo === null || Number.isNaN(patron) || Number.isNaN(equipo)) {
    return { error: '—', conforme: null };
  }

  const error = Math.round(equipo * ESCALA) - Math.round(patron * ESCALA);

  return {
    error: FORMATO_DE_ERROR.format(error / ESCALA),
    conforme: tolerancia === null ? null : Math.abs(error) <= Math.round(tolerancia * ESCALA),
  };
}

/**
 * Una casilla de la tabla: qué se mide, dónde, y cuál de las N lecturas de ese punto es.
 *
 * <p><b>Lleva su verificación desde el 2026-10-03</b>, y es obligatoria: es lo único que dice qué se
 * midió cuando no hay punto, y un termohigrómetro puede medir temperatura y humedad las dos sin
 * puntos. La unidad viene de la verificación y ya no se teclea en ningún caso.
 */
interface Fila {
  readonly idVerificacion: string;
  readonly magnitud: string;
  readonly unidad: string;
  readonly idPuntoVerificacion: string | null;
  readonly punto: string;
  readonly secuencia: number;
  /**
   * Qué lado vale lo que dice el punto, en una modalidad constante: con patrón constante la pesa
   * patrón ES el punto —una de 5 kg en el punto 5—, y con equipo constante lo es el equipo. Su casilla
   * nace con ese valor (2026-10-27): antes nacía en blanco, y había que copiar a mano el punto que
   * estaba en la misma fila.
   */
  readonly fijo?: 'valorPatron' | 'valorEquipo';
}

/** Las filas de una verificación, con el índice que ocupan en el formulario entero. */
interface GrupoDeFilas {
  readonly idVerificacion: string;
  readonly magnitud: string;
  readonly unidad: string;
  readonly modalidad: string;
  readonly cantidadDatos: number | null;
  /** La tolerancia que el tipo declara hoy, que es la que se copiará al guardar. */
  readonly tolerancia: number | null;
  /** Si esta verificación se hace con patrón y equipo variables: entonces las filas las pone quien mide. */
  readonly aMano: boolean;
  readonly filas: readonly { readonly fila: Fila; readonly indice: number }[];
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
 * <p><b>Un equipo se verifica en varias magnitudes, y la tabla se agrupa por ellas</b> (2026-10-03).
 * Un termohigrómetro tiene una tabla de temperatura y otra de humedad relativa, cada una con su
 * unidad, sus puntos y su modalidad. Antes había una sola tabla y una sola modalidad para todo el
 * aparato, de modo que un termohigrómetro había que registrarlo como dos tipos de equipo.
 *
 * <p><b>Con patrón y equipo variables no hay puntos</b> —el tipo no los declara y el servidor los
 * rechazaría— y cuántas lecturas tomar lo decide el ingeniero: esa tabla crece a mano. <b>Pero la
 * unidad ya no se teclea nunca</b>: la declara la verificación, también en ese caso. Antes había que
 * escribirla, y eso permitía imprimir un reporte con una unidad que nadie midió.
 *
 * <p>Se manda la tabla <b>entera</b> y sustituye la anterior: lo que no se envíe queda retirado. Es lo
 * que hace el API, y encaja con cómo se revisa una verificación — se corrige un valor y se entrega la
 * tabla completa.
 *
 * <p><b>Se guarda sola desde el 2026-10-27</b>, pedido por el usuario para quitar pasos: al salir de
 * cada casilla —el evento {@code change}, no cada tecla—, si hay alguna fila con las dos lecturas.
 * Los patrones ya se guardaban al elegirlos.
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
  /** Los patrones ya puestos en cada verificación, como los copió el reporte (V30). */
  readonly patrones = input<readonly PatronUsado[]>([]);

  private readonly fb = inject(FormBuilder);
  private readonly catalogo = inject(CatalogoApi);

  private readonly reportes = inject(ReporteApi);
  protected readonly registro = this.reportes.registrarVerificacion();
  protected readonly eleccion = this.reportes.usarPatron();
  protected readonly retiro = this.reportes.quitarPatron();

  // --- Los patrones de cada verificacion (V27; por verificacion desde V30) ---------

  private readonly hoy = fechaIso(inject(HOY)());
  private readonly catalogoDePatrones = this.catalogo.listarPatrones();

  /**
   * Los que se pueden elegir: vigentes hoy y sin retirar, por nombre. Uno vencido no se ofrece aunque
   * el servidor lo deje elegir: no dejaría cerrar, y ofrecerlo sería ofrecer un callejón.
   */
  private readonly elegibles = computed(() =>
    (this.catalogoDePatrones.data() ?? [])
      .filter((patron) => patron.estadoActivo && (patron.vencimientoCertificado ?? '') >= this.hoy)
      .sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? '', 'es')),
  );

  /** Los patrones puestos en una verificación, en el orden en que se pusieron. */
  protected patronesDe(idVerificacion: string): readonly PatronUsado[] {
    return this.patrones().filter((usado) => usado.idVerificacion === idVerificacion);
  }

  /**
   * Los elegibles que el catálogo admite para esa verificación y que todavía no tiene: el mismo patrón
   * dos veces no suma nada. Desde V60 el catálogo dice con qué se mide cada verificación, y el servidor
   * rechaza los demás; ofrecerlos sería ofrecer un error.
   */
  protected elegiblesPara(idVerificacion: string) {
    const admitidos = this.admitidosEn(idVerificacion);
    const puestos = new Set(this.patronesDe(idVerificacion).map((usado) => usado.id));

    return this.elegibles().filter(
      (patron) => admitidos.has(patron.id ?? '') && !puestos.has(patron.id),
    );
  }

  /** Si el catálogo, ya leído, no declara ningún patrón para esa verificación. */
  protected sinPatronesEnElCatalogo(idVerificacion: string): boolean {
    return this.patronesDelTipo.isSuccess() && this.admitidosEn(idVerificacion).size === 0;
  }

  private admitidosEn(idVerificacion: string): ReadonlySet<string> {
    const declarados = (this.patronesDelTipo.data() ?? []).find(
      (verificacion) => verificacion.idVerificacion === idVerificacion,
    );

    return new Set(declarados?.patrones ?? []);
  }

  /** Si una verificación tiene lecturas guardadas: entonces le hace falta al menos un patrón. */
  protected tieneLecturas(idVerificacion: string): boolean {
    return this.lecturas().some((lectura) => lectura.idVerificacion === idVerificacion);
  }

  protected vencido(usado: PatronUsado): boolean {
    return patronVencido(usado, this.hoy);
  }

  protected readonly fechaCorta = fechaCorta;

  /** Se guarda al elegirlo, y el desplegable vuelve a su primera opción para poder elegir otro. */
  protected ponerPatron(idVerificacion: string, desplegable: HTMLSelectElement): void {
    const idEquipoPatron = desplegable.value;
    desplegable.value = '';

    if (!idEquipoPatron) {
      return;
    }

    this.eleccion.mutate({ id: this.idReporte(), idVerificacion, idEquipoPatron });
  }

  protected quitarPatron(idVerificacion: string, idEquipoPatron: string): void {
    this.retiro.mutate({ id: this.idReporte(), idVerificacion, idEquipoPatron });
  }

  // --- La cadena del catalogo, cuatro eslabones: ver tipoDelEquipo ------------

  private readonly cadena = tipoDelEquipo(() => this.idEquipoCliente());

  /** El tipo del equipo reportado, o {@code undefined} mientras la cadena no esté completa. */
  protected readonly tipo = this.cadena.tipo;

  protected readonly cargando = this.cadena.cargando;

  /** Con qué patrones se mide cada verificación del tipo, según el catálogo (V60). */
  private readonly patronesDelTipo = this.catalogo.patronesDeVerificaciones(
    computed(() => this.tipo()?.id ?? ''),
  );

  /** Un tipo sin verificaciones no se verifica: no hay nada que medirle, y el API lo rechazaría. */
  protected readonly seVerifica = computed(() => !!this.tipo()?.verificaciones?.length);

  /** Lo que se le mide, para el resumen de la cabecera: «Temperatura (°C) · Humedad relativa (%HR)». */
  protected readonly resumen = computed(() =>
    (this.tipo()?.verificaciones ?? [])
      .map((verificacion) => `${verificacion.magnitud} (${verificacion.unidad})`)
      .join(' · '),
  );

  // --- La tabla ---------------------------------------------------------------

  protected readonly filas = signal<readonly Fila[]>([]);

  protected readonly formulario = this.fb.group({ casillas: this.fb.array([] as FormGroup[]) });

  protected get casillas(): FormArray<FormGroup> {
    return this.formulario.get('casillas') as FormArray<FormGroup>;
  }

  /**
   * Las filas agrupadas por verificación, con el índice que cada una ocupa en el formulario.
   *
   * <p>El formulario es <b>uno solo y plano</b> —un {@code FormArray} con todas las casillas— y la
   * plantilla las reparte en bloques. Partirlo en un formulario por verificación habría obligado a
   * recomponerlos al guardar, y lo que se manda es la tabla entera.
   */
  protected readonly grupos = computed<readonly GrupoDeFilas[]>(() => {
    const verificaciones = this.tipo()?.verificaciones ?? [];
    const filas = this.filas();

    return verificaciones.map((verificacion) => ({
      idVerificacion: verificacion.id!,
      magnitud: verificacion.magnitud ?? '',
      unidad: verificacion.unidad ?? '',
      modalidad: verificacion.modalidad ? ETIQUETA_DE_MODALIDAD[verificacion.modalidad] : '',
      cantidadDatos: verificacion.cantidadDatos ?? null,
      tolerancia: verificacion.tolerancia ?? null,
      aMano: !mantieneAlgoConstante(verificacion.modalidad),
      filas: filas
        .map((fila, indice) => ({ fila, indice }))
        .filter((par) => par.fila.idVerificacion === verificacion.id),
    }));
  });

  /** Si se guardó lo último que se cambió: para decirlo, ya que no hay botón que lo diga. */
  protected readonly guardado = signal(false);

  /**
   * Si hay un guardado en vuelo, y si llegó otro cambio mientras tanto. Propios y no {@code isPending}:
   * cuando corre {@code onSettled}, la mutación todavía se da por pendiente.
   */
  private enVuelo = false;
  private otraVez = false;

  protected readonly hayError = computed(
    () => this.registro.isError() || this.eleccion.isError() || this.retiro.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(this.registro.error() ?? this.eleccion.error() ?? this.retiro.error()),
  );

  /** La tolerancia en la cabecera de cada tabla, con su unidad: «±0,5 °C». */
  protected textoDeTolerancia(grupo: GrupoDeFilas): string {
    return grupo.tolerancia === null
      ? ''
      : `±${grupo.tolerancia.toLocaleString('es-CO')} ${grupo.unidad}`;
  }

  /**
   * El error y la conformidad de una fila, mientras se escribe.
   *
   * <p>En un borrador se juzga con la tolerancia que el tipo declara hoy, que es la que se copiará al
   * guardar. En un reporte cerrado, con la que se guardó en la lectura: si el tipo cambió su
   * tolerancia después, el reporte firmado no puede cambiar de veredicto.
   */
  protected comparacion(grupo: GrupoDeFilas, indice: number): Comparacion {
    this.escrito();

    const casilla = this.casillas.at(indice);
    const fila = this.filas()[indice];
    const numero = (valor: unknown) => (valor === '' || valor == null ? null : Number(valor));
    const tolerancia = this.editable()
      ? grupo.tolerancia
      : (this.lecturas().find(
          (lectura) =>
            lectura.idVerificacion === fila?.idVerificacion &&
            (lectura.idPuntoVerificacion ?? null) === fila?.idPuntoVerificacion &&
            lectura.secuencia === fila?.secuencia,
        )?.tolerancia ?? null);

    return comparar(
      numero(casilla?.get('valorPatron')?.value),
      numero(casilla?.get('valorEquipo')?.value),
      tolerancia,
    );
  }

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

      // Una tabla por verificacion, y en el orden en que el tipo las declara: con dos magnitudes, el
      // termohigrometro tiene dos bloques y no una tabla revuelta.
      this.rehacer(
        (tipo.verificaciones ?? []).flatMap<Fila>((verificacion) => {
          const comun = {
            idVerificacion: verificacion.id!,
            magnitud: verificacion.magnitud ?? '',
            unidad: verificacion.unidad ?? '',
          };

          if (mantieneAlgoConstante(verificacion.modalidad)) {
            const cuantas = verificacion.cantidadDatos ?? 1;
            const fijo =
              verificacion.modalidad === 'PATRON_CONSTANTE'
                ? ('valorPatron' as const)
                : ('valorEquipo' as const);

            return (verificacion.puntos ?? []).flatMap((punto) =>
              Array.from({ length: cuantas }, (_, indice) => ({
                ...comun,
                idPuntoVerificacion: punto.id!,
                // El valor solo, sin unidad: la unidad esta en la cabecera del bloque.
                punto: String(punto.valor ?? ''),
                secuencia: indice + 1,
                fijo,
              })),
            );
          }

          // Modalidad variable: las filas son las que ya se midieron EN ESTA verificacion, o una en
          // blanco para empezar. Filtrar por verificacion es lo que impide que las lecturas de
          // temperatura aparezcan en la tabla de humedad.
          const tomadas = medidas.filter(
            (lectura) =>
              lectura.idVerificacion === verificacion.id && !lectura.idPuntoVerificacion,
          );

          return (tomadas.length ? tomadas : [{ secuencia: 1 }]).map((lectura) => ({
            ...comun,
            idPuntoVerificacion: null,
            punto: 'Sin punto fijo',
            secuencia: lectura.secuencia ?? 1,
          }));
        }),
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

  /**
   * Añade una lectura más a <b>una</b> verificación, solo con modalidad variable.
   *
   * <p>Por verificación y no para toda la tabla: el número de lectura se cuenta dentro de su
   * verificación, y una fila añadida a la tabla de temperatura no es una fila de la de humedad.
   *
   * <p>Se añade al final del formulario, no junto a sus hermanas, porque el orden del {@code FormArray}
   * no tiene que coincidir con el visual: la plantilla agrupa por índice y el índice no cambia.
   */
  protected anadirFila(grupo: GrupoDeFilas): void {
    const siguiente = Math.max(0, ...grupo.filas.map((par) => par.fila.secuencia)) + 1;

    this.filas.update((filas) => [
      ...filas,
      {
        idVerificacion: grupo.idVerificacion,
        magnitud: grupo.magnitud,
        unidad: grupo.unidad,
        idPuntoVerificacion: null,
        punto: 'Sin punto fijo',
        secuencia: siguiente,
      },
    ]);
    this.casillas.push(this.grupoVacio());
    this.formulario.markAsDirty();
  }

  /**
   * La guarda si hay algo nuevo y alguna fila completa. Si se está guardando otra cosa, la deja para
   * después: dos peticiones en vuelo podrían llegar al revés, y la tabla que ganaría sería la vieja.
   */
  protected guardar(): void {
    const lecturas = this.lecturasEscritas();

    if (!this.editable() || !this.formulario.dirty || !lecturas.length) {
      return;
    }
    if (this.enVuelo) {
      this.otraVez = true;

      return;
    }

    const enviado = JSON.stringify(this.formulario.getRawValue());
    this.guardado.set(false);
    this.enVuelo = true;

    this.registro.mutate(
      { id: this.idReporte(), lecturas },
      {
        onSuccess: () => {
          // Solo se da por guardado si nadie cambió nada mientras tanto: si no, eso sigue pendiente.
          if (JSON.stringify(this.formulario.getRawValue()) === enviado) {
            this.formulario.markAsPristine();
            this.guardado.set(true);
          }
        },
        onSettled: () => {
          this.enVuelo = false;
          if (this.otraVez) {
            this.otraVez = false;
            this.guardar();
          }
        },
      },
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

      if (patron === '' || patron == null || equipo === '' || equipo == null) {
        return [];
      }

      // Sin unidad: la pone el servidor desde la verificacion, de modo que no se puede contradecir.
      return [
        {
          idVerificacion: fila.idVerificacion,
          idPuntoVerificacion: fila.idPuntoVerificacion ?? undefined,
          secuencia: fila.secuencia,
          valorPatron: Number(patron),
          valorEquipo: Number(equipo),
        },
      ];
    });
  }

  /**
   * Pone en la tabla las filas que el tipo dicta, con lo ya medido.
   *
   * <p><b>Reutiliza las casillas que ya existen y solo cambia sus valores</b> (2026-10-27). Antes las
   * vaciaba y creaba otras, y las que se ven en pantalla seguían atadas a las viejas: tras guardar una
   * fila —la ficha recarga el reporte y la tabla se rehace—, lo que se escribía en la siguiente iba a
   * una casilla que ya no estaba en el formulario, y su error y su conformidad se quedaban en «—». Lo
   * reportó el usuario al guardarse sola la tabla, y pasaba igual con el botón. Solo se crean o se
   * quitan las que sobran o faltan.
   */
  private rehacer(filas: readonly Fila[], medidas: readonly LecturaDeVerificacion[]): void {
    this.filas.set(filas);

    while (this.casillas.length > filas.length) {
      this.casillas.removeAt(this.casillas.length - 1, { emitEvent: false });
    }

    filas.forEach((fila, indice) => {
      // La verificacion entra en la comparacion, y sin ella esto seria falso: la lectura 1 sin punto
      // de temperatura y la 1 sin punto de humedad casarian con la misma fila.
      const medida = medidas.find(
        (lectura) =>
          lectura.idVerificacion === fila.idVerificacion &&
          (lectura.idPuntoVerificacion ?? null) === fila.idPuntoVerificacion &&
          lectura.secuencia === fila.secuencia,
      );

      // Sin lectura guardada, el lado constante nace con el valor del punto, y se puede corregir. Con
      // lectura no se mira: el servidor exige los dos valores, de modo que el guardado manda siempre.
      const delPunto = (lado: 'valorPatron' | 'valorEquipo') =>
        fila.fijo === lado ? fila.punto : '';
      const valor = {
        valorPatron: medida?.valorPatron?.toString() ?? delPunto('valorPatron'),
        valorEquipo: medida?.valorEquipo?.toString() ?? delPunto('valorEquipo'),
      };

      if (indice < this.casillas.length) {
        this.casillas.at(indice).setValue(valor, { emitEvent: false });
      } else {
        this.casillas.push(
          this.fb.group({
            valorPatron: this.fb.nonNullable.control(valor.valorPatron),
            valorEquipo: this.fb.nonNullable.control(valor.valorEquipo),
          }),
          { emitEvent: false },
        );
      }
    });

    // Y se vuelve a aplicar el estado: las casillas nuevas nacen activas.
    this.aplicarEstado(this.editable());
  }

  private grupoVacio(): FormGroup {
    return this.fb.group({
      valorPatron: this.fb.nonNullable.control(''),
      valorEquipo: this.fb.nonNullable.control(''),
    });
  }
}
