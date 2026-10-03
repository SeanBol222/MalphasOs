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
}

/** Las filas de una verificación, con el índice que ocupan en el formulario entero. */
interface GrupoDeFilas {
  readonly idVerificacion: string;
  readonly magnitud: string;
  readonly unidad: string;
  readonly modalidad: string;
  readonly cantidadDatos: number | null;
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
      aMano: !mantieneAlgoConstante(verificacion.modalidad),
      filas: filas
        .map((fila, indice) => ({ fila, indice }))
        .filter((par) => par.fila.idVerificacion === verificacion.id),
    }));
  });

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

            return (verificacion.puntos ?? []).flatMap((punto) =>
              Array.from({ length: cuantas }, (_, indice) => ({
                ...comun,
                idPuntoVerificacion: punto.id!,
                // El valor solo, sin unidad: la unidad esta en la cabecera del bloque.
                punto: String(punto.valor ?? ''),
                secuencia: indice + 1,
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

  private rehacer(filas: readonly Fila[], medidas: readonly LecturaDeVerificacion[]): void {
    this.filas.set(filas);
    this.casillas.clear({ emitEvent: false });

    for (const fila of filas) {
      // La verificacion entra en la comparacion, y sin ella esto seria falso: la lectura 1 sin punto
      // de temperatura y la 1 sin punto de humedad casarian con la misma fila.
      const medida = medidas.find(
        (lectura) =>
          lectura.idVerificacion === fila.idVerificacion &&
          (lectura.idPuntoVerificacion ?? null) === fila.idPuntoVerificacion &&
          lectura.secuencia === fila.secuencia,
      );

      this.casillas.push(
        this.fb.group({
          valorPatron: this.fb.nonNullable.control(medida?.valorPatron?.toString() ?? ''),
          valorEquipo: this.fb.nonNullable.control(medida?.valorEquipo?.toString() ?? ''),
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
    });
  }
}
