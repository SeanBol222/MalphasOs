import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  CdkDrag,
  CdkDragDrop,
  CdkDragHandle,
  CdkDropList,
  moveItemInArray,
} from '@angular/cdk/drag-drop';
import { CatalogoApi } from '../catalogo-api';
import { AsignacionDeProtocolos, Protocolo, ProtocoloAsignado } from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';
import { Buscador, Opcion } from '../../../shared/buscador/buscador';
import { mismoNombreQue, parecidosA } from './parecidos';

/** Un protocolo ya elegido para el tipo, con lo que hace falta para pintarlo. */
interface Elegido {
  readonly id: string;
  readonly nombre: string;
  readonly descripcion: string;
  readonly indicacion: string;
}

/**
 * Los protocolos del preventivo de un tipo de equipo, en el orden de su prioridad.
 *
 * <p>Decidido por el usuario el 2026-10-10: <b>la prioridad la decide quien configura, nunca el
 * programa</b>, y se decide ordenando la lista —arrastrando, o con los botones de subir y bajar, porque
 * arrastrar no se puede con el teclado—. Lo que viaja al servidor es la lista en ese orden; la prioridad
 * la deriva el servidor de la posicion, asi que va siempre 1, 2, 3 sin huecos.
 *
 * <p><b>Se eligen entre los que ya existen</b>, con el buscador. Si el que hace falta no existe se crea
 * en un panel aparte, que <b>avisa de los parecidos mientras se escribe el nombre</b> y ofrece usar uno
 * de ellos: pedido expresamente, para que no sea facil repetir protocolos. El servidor rechaza ademas
 * dos con el mismo nombre sin contar mayusculas ni tildes.
 *
 * <p>Como el bloque de verificacion, entrega lo que tiene por {@link cambio} y no guarda nada: el tipo
 * se guarda con su propio boton, y estos protocolos con el.
 */
@Component({
  selector: 'app-protocolos-del-tipo',
  imports: [ReactiveFormsModule, Buscador, CdkDropList, CdkDrag, CdkDragHandle],
  templateUrl: './protocolos-del-tipo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProtocolosDelTipo {
  private readonly api = inject(CatalogoApi);
  private readonly formBuilder = inject(FormBuilder);

  /** Los que el tipo ya tiene, para la edicion. Nulo en el alta. */
  readonly inicial = input<readonly ProtocoloAsignado[] | null>(null);

  /** La lista en orden, tal como se mandaria al servidor. Se emite en cada cambio. */
  readonly cambio = output<AsignacionDeProtocolos>();

  protected readonly catalogo = this.api.listarProtocolos();
  protected readonly alta = this.api.crearProtocolo();

  protected readonly elegidos = signal<readonly Elegido[]>([]);

  /** Lo que se anuncia a un lector de pantalla al mover un protocolo. */
  protected readonly anuncio = signal('');

  /** Si el usuario ya toco la lista: desde entonces lo que llegue del servidor no la pisa. */
  private tocada = false;

  /** El buscador: su valor es el id del protocolo elegido, y en cuanto hay uno se agrega y se vacia. */
  protected readonly eleccion = this.formBuilder.nonNullable.control('');

  protected readonly opciones = computed<readonly Opcion[]>(() => {
    const ya = new Set(this.elegidos().map((elegido) => elegido.id));

    return this.vigentes()
      .filter((protocolo) => !ya.has(protocolo.id!))
      .map((protocolo) => ({ id: protocolo.id!, etiqueta: protocolo.nombre! }));
  });

  /**
   * Lo que dice el buscador cuando no le queda nada que ofrecer, que son dos casos distintos: el
   * catalogo esta vacio, o todos sus protocolos ya estan en la lista. Hasta el 2026-10-10 decia lo
   * primero siempre, y con la lista llena era falso.
   */
  protected readonly avisoSinOpciones = computed(() =>
    this.vigentes().length
      ? 'Ya están en la lista todos los protocolos del catálogo. Si falta uno, créelo.'
      : 'Todavía no hay protocolos en el catálogo: cree el primero.',
  );

  // --- El panel que crea uno nuevo -----------------------------------------

  protected readonly creando = signal(false);

  protected readonly nuevo = this.formBuilder.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    descripcion: ['', [Validators.required, Validators.maxLength(500)]],
  });

  private readonly nombreEscrito = toSignal(this.nuevo.controls.nombre.valueChanges, {
    initialValue: '',
  });

  /** Los que ya existen y se parecen a lo que se escribe: crear uno repetido es una decision, no un descuido. */
  protected readonly parecidos = computed(() => parecidosA(this.nombreEscrito(), this.vigentes()));

  protected readonly mismoNombre = computed(() =>
    mismoNombreQue(this.nombreEscrito(), this.vigentes()),
  );

  protected readonly errorDelAlta = computed(() =>
    this.alta.isError() ? traducirError(this.alta.error()) : '',
  );

  private readonly vigentes = computed(() =>
    (this.catalogo.data() ?? []).filter((protocolo) => protocolo.estadoActivo),
  );

  constructor() {
    // Lo que el tipo ya tiene llega despues de construirse: se vuelca mientras nadie haya tocado nada.
    effect(() => {
      const inicial = this.inicial();

      if (inicial && !this.tocada) {
        untracked(() =>
          this.elegidos.set(
            inicial.map((asignado) => ({
              id: asignado.idProtocolo!,
              nombre: asignado.nombre ?? '',
              descripcion: asignado.descripcion ?? '',
              indicacion: asignado.indicacion ?? '',
            })),
          ),
        );
      }
    });

    effect(() => {
      const elegidos = this.elegidos();

      untracked(() =>
        this.cambio.emit({
          protocolos: elegidos.map((elegido) => ({
            idProtocolo: elegido.id,
            ...(elegido.indicacion.trim() ? { indicacion: elegido.indicacion.trim() } : {}),
          })),
        }),
      );
    });

    this.eleccion.valueChanges.subscribe((id) => {
      const protocolo = this.vigentes().find((candidato) => candidato.id === id);

      if (protocolo) {
        this.agregar(protocolo);
        this.eleccion.setValue('', { emitEvent: false });
      }
    });
  }

  protected agregar(protocolo: Protocolo): void {
    if (this.elegidos().some((elegido) => elegido.id === protocolo.id)) {
      return;
    }

    this.tocar([
      ...this.elegidos(),
      {
        id: protocolo.id!,
        nombre: protocolo.nombre ?? '',
        descripcion: protocolo.descripcion ?? '',
        indicacion: '',
      },
    ]);
    this.anuncio.set(`${protocolo.nombre} agregado con la prioridad ${this.elegidos().length}.`);
  }

  protected quitar(indice: number): void {
    const quitado = this.elegidos()[indice];
    this.tocar(this.elegidos().filter((_, i) => i !== indice));
    this.anuncio.set(`${quitado?.nombre} quitado.`);
  }

  protected mover(desde: number, hasta: number): void {
    if (hasta < 0 || hasta >= this.elegidos().length || desde === hasta) {
      return;
    }

    const lista = [...this.elegidos()];
    moveItemInArray(lista, desde, hasta);
    this.tocar(lista);
    this.anuncio.set(`${lista[hasta]?.nombre} pasa a la prioridad ${hasta + 1}.`);
  }

  protected soltar(evento: CdkDragDrop<readonly Elegido[]>): void {
    this.mover(evento.previousIndex, evento.currentIndex);
  }

  protected indicar(indice: number, indicacion: string): void {
    this.tocar(
      this.elegidos().map((elegido, i) => (i === indice ? { ...elegido, indicacion } : elegido)),
    );
  }

  protected abrirPanel(): void {
    this.nuevo.reset();
    this.alta.reset();
    this.creando.set(true);
  }

  protected cerrarPanel(): void {
    this.creando.set(false);
  }

  /** Usar uno que ya existe en lugar de crear el que se estaba escribiendo. */
  protected usar(protocolo: Protocolo): void {
    this.agregar(protocolo);
    this.cerrarPanel();
  }

  protected crear(): void {
    if (this.nuevo.invalid) {
      this.nuevo.markAllAsTouched();

      return;
    }

    const { nombre, descripcion } = this.nuevo.getRawValue();

    this.alta.mutate(
      { nombre: nombre.trim(), descripcion: descripcion.trim() },
      {
        onSuccess: (creado) => {
          this.agregar(creado);
          this.cerrarPanel();
        },
      },
    );
  }

  private tocar(lista: readonly Elegido[]): void {
    this.tocada = true;
    this.elegidos.set(lista);
  }
}
