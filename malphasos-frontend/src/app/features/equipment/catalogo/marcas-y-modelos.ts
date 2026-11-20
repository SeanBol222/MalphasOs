import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { CatalogoApi } from '../catalogo-api';
import { normalizar } from '../../../core/texto';

/** Una pieza que ya existe, por su identificador, o una nueva, por el nombre que se le dara. */
export type PiezaElegida = { readonly id: string } | { readonly nombre: string };

/** Un modelo que nacera con el tipo: su marca, su fabricante, su nombre y, si se sabe, su INVIMA. */
export interface ModeloDelTipoNuevo {
  readonly marca: PiezaElegida;
  readonly fabricante: PiezaElegida;
  readonly nombre: string;
  readonly invima?: string;
}

export interface ConfiguracionDeModelos {
  readonly modelos: readonly ModeloDelTipoNuevo[];
  readonly valida: boolean;
}

/** El valor que en cada desplegable dice «voy a crear una pieza nueva», como en el alta de un equipo. */
export const NUEVO = 'nuevo';

/** Una marca y un fabricante elegidos o con nombre, y el nombre del modelo. */
function filaCompleta(fila: AbstractControl): ValidationErrors | null {
  const v = fila.getRawValue() as {
    idMarca: string;
    nombreDeMarca: string;
    idFabricante: string;
    nombreDeFabricante: string;
  };
  const marca = v.idMarca && (v.idMarca !== NUEVO || v.nombreDeMarca.trim());
  const fabricante = v.idFabricante && (v.idFabricante !== NUEVO || v.nombreDeFabricante.trim());

  return marca && fabricante ? null : { incompleta: true };
}

/**
 * Las marcas y los modelos que un tipo nuevo puede traer desde su alta (2026-11-19, pedido por el
 * usuario): sin esto, dar de alta un tipo era la primera de cuatro pantallas antes de poder instalar
 * un equipo de ese tipo.
 *
 * <p>Cada fila es un modelo: la marca y el fabricante se eligen de los que hay o se crean escribiendo
 * su nombre, y el modelo se nombra. Como los bloques de verificacion y de protocolos, <b>no guarda
 * nada</b>: entrega lo que tiene por {@link cambio}, y el alta del tipo lo crea despues del tipo,
 * porque el modelo necesita su identificador.
 *
 * <p>El alta lo pinta plegado, y <b>no lo crea hasta que se abre</b>: la mayoria de las altas no lo usan,
 * y no tienen por que pedir las marcas y los fabricantes cada vez. Una vez creado se conserva aunque se
 * vuelva a plegar, para no perder las filas.
 */
@Component({
  selector: 'app-marcas-y-modelos',
  imports: [ReactiveFormsModule],
  templateUrl: './marcas-y-modelos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarcasYModelos implements OnInit {
  private readonly api = inject(CatalogoApi);
  private readonly formBuilder = inject(FormBuilder);

  readonly cambio = output<ConfiguracionDeModelos>();

  protected readonly NUEVO = NUEVO;

  /** Hasta que se intenta guardar no se le reprocha nada a una fila a medias. */
  protected readonly intento = signal(false);

  protected readonly marcas = this.api.listarMarcas();
  protected readonly fabricantes = this.api.listarFabricantes();

  protected readonly marcasActivas = computed(() =>
    (this.marcas.data() ?? [])
      .filter((m) => m.estadoActivo)
      .sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? '', 'es')),
  );
  protected readonly fabricantesActivos = computed(() =>
    (this.fabricantes.data() ?? [])
      .filter((f) => f.estadoActivo)
      .sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? '', 'es')),
  );

  protected readonly filas = this.formBuilder.array<ReturnType<MarcasYModelos['nuevaFila']>>([]);

  constructor() {
    this.filas.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.avisar());
  }

  ngOnInit(): void {
    // Quien la abre es porque quiere agregar uno: la primera fila ya esta puesta. En el constructor el
    // alta todavia no escucha {@link cambio}, y se perderia.
    this.agregar();
  }

  private nuevaFila() {
    return this.formBuilder.nonNullable.group(
      {
        idMarca: ['', Validators.required],
        nombreDeMarca: ['', Validators.maxLength(50)],
        idFabricante: ['', Validators.required],
        nombreDeFabricante: ['', Validators.maxLength(50)],
        // Obligatorio, al contrario que el INVIMA: un modelo sin nombre no es nada, y uno sin registro
        // sanitario es un estado normal mientras se tramita.
        nombre: ['', [Validators.required, Validators.maxLength(50)]],
        invima: ['', Validators.maxLength(50)],
      },
      { validators: filaCompleta },
    );
  }

  protected agregar(): void {
    this.filas.push(this.nuevaFila());
  }

  protected quitar(indice: number): void {
    this.filas.removeAt(indice);
  }

  protected creando(indice: number, campo: 'idMarca' | 'idFabricante'): boolean {
    return this.filas.at(indice).controls[campo].value === NUEVO;
  }

  protected incompleta(indice: number): boolean {
    return this.intento() && this.filas.at(indice).invalid;
  }

  /** Lo llama el alta al intentar guardar: desde ese momento se senalan las filas a medias. */
  marcarIntento(): void {
    this.intento.set(true);
  }

  /**
   * Una pieza nueva escrita con el nombre de una que ya existe —sin tildes ni mayusculas— es esa: se
   * entrega por su identificador, para no crear «Welch Allyn» dos veces.
   */
  private pieza(
    elegida: string,
    escrita: string,
    existentes: readonly { id?: string; nombre?: string }[],
  ): PiezaElegida {
    if (elegida !== NUEVO) {
      return { id: elegida };
    }

    const nombre = escrita.trim();
    const igual = existentes.find((e) => normalizar(e.nombre ?? '') === normalizar(nombre));

    return igual?.id ? { id: igual.id } : { nombre };
  }

  private avisar(): void {
    this.cambio.emit({
      valida: this.filas.valid,
      modelos: this.filas.getRawValue().map((v) => ({
        marca: this.pieza(v.idMarca, v.nombreDeMarca, this.marcas.data() ?? []),
        fabricante: this.pieza(v.idFabricante, v.nombreDeFabricante, this.fabricantes.data() ?? []),
        nombre: v.nombre.trim(),
        ...(v.invima.trim() ? { invima: v.invima.trim() } : {}),
      })),
    });
  }
}
