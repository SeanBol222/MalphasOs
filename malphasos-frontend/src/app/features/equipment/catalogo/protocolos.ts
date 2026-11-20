import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { CatalogoApi } from '../catalogo-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';
import { mismoNombreQue, normalizar, parecidosA } from './parecidos';
import { CabeceraDePieza } from './cabecera-de-pieza';
import { OrdenMovil } from './orden-movil';
import { ColumnaOrdenable, Sentido, compararEn, invertido } from './columna-ordenable';

/**
 * El catalogo de protocolos de mantenimiento preventivo (RF-14), del 2026-10-10.
 *
 * <p>Un protocolo es una actividad —«Limpieza y desinfeccion»— que se escribe una vez y se asigna a los
 * tipos de equipo que la necesitan; la asignacion y su orden se hacen en cada tipo, no aqui. Aqui se
 * crean, se corrigen y se retiran.
 *
 * <p><b>Crear uno avisa de los parecidos mientras se escribe</b>, igual que el panel dentro del tipo:
 * pedido para que no sea facil repetirlos. Y retirar uno que usan tipos vigentes no se puede; lo dice el
 * servidor, con cuantos, y la pantalla lo muestra tal cual.
 */
@Component({
  selector: 'app-protocolos',
  imports: [ReactiveFormsModule, CabeceraDePieza, ColumnaOrdenable, OrdenMovil],
  templateUrl: './protocolos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Protocolos {
  private readonly api = inject(CatalogoApi);
  private readonly formBuilder = inject(FormBuilder);
  private readonly sesion = inject(Sesion);

  protected readonly protocolos = this.api.listarProtocolos();
  protected readonly alta = this.api.crearProtocolo();
  protected readonly cambio = this.api.editarProtocolo();
  protected readonly baja = this.api.retirarProtocolo();

  protected readonly puedeEscribir = computed(() => this.sesion.puede('equipment.write'));

  /** Lo escrito en la lupa de la cabecera. */
  protected readonly busqueda = signal('');

  /** El sentido del orden por nombre: de la A a la Z por defecto, y la cabecera lo invierte. */
  protected readonly sentido = signal<Sentido>('asc');

  /** El alta se abre con «Nuevo protocolo» y se cierra al crear o al cancelar. */
  protected readonly abierto = signal(false);

  /**
   * Filtrados por nombre o descripcion, sin tildes ni mayusculas —cada palabra puede caer en cualquiera
   * de los dos—, por nombre en el orden elegido. Hasta el 2026-11-20 iban los vigentes primero; desde
   * entonces el catalogo entero ordena por su primera columna, y el retirado se distingue por peso.
   */
  protected readonly visibles = computed(() => {
    const palabras = normalizar(this.busqueda()).split(' ').filter(Boolean);

    return [...(this.protocolos.data() ?? [])]
      .filter((protocolo) => {
        const texto = normalizar(`${protocolo.nombre ?? ''} ${protocolo.descripcion ?? ''}`);

        return palabras.every((palabra) => texto.includes(palabra));
      })
      .sort((a, b) => compararEn(this.sentido(), a.nombre, b.nombre));
  });

  protected invertirOrden(): void {
    this.sentido.update(invertido);
  }

  protected abrir(): void {
    this.nuevo.reset();
    this.abierto.set(true);
  }

  protected cancelar(): void {
    this.nuevo.reset();
    this.abierto.set(false);
  }

  protected readonly nuevo = this.formBuilder.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    descripcion: ['', [Validators.required, Validators.maxLength(500)]],
  });
  private readonly nombreNuevo = toSignal(this.nuevo.controls.nombre.valueChanges, {
    initialValue: '',
  });

  protected readonly parecidos = computed(() =>
    parecidosA(this.nombreNuevo(), this.protocolos.data() ?? []),
  );
  protected readonly mismoNombre = computed(() =>
    mismoNombreQue(this.nombreNuevo(), this.protocolos.data() ?? []),
  );

  protected readonly editando = signal<string | null>(null);
  protected readonly edicion = this.formBuilder.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    descripcion: ['', [Validators.required, Validators.maxLength(500)]],
  });

  protected readonly confirmandoRetiro = signal<string | null>(null);

  protected readonly hayError = computed(
    () =>
      this.protocolos.isError() ||
      this.alta.isError() ||
      this.cambio.isError() ||
      this.baja.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.protocolos.error() ?? this.alta.error() ?? this.cambio.error() ?? this.baja.error(),
    ),
  );

  protected crear(): void {
    if (this.nuevo.invalid) {
      this.nuevo.markAllAsTouched();

      return;
    }

    const { nombre, descripcion } = this.nuevo.getRawValue();

    this.alta.mutate(
      { nombre: nombre.trim(), descripcion: descripcion.trim() },
      { onSuccess: () => this.cancelar() },
    );
  }

  protected empezarAEditar(
    id: string,
    nombre: string | undefined,
    descripcion: string | undefined,
  ): void {
    this.edicion.setValue({ nombre: nombre ?? '', descripcion: descripcion ?? '' });
    this.editando.set(id);
  }

  protected guardar(id: string): void {
    if (this.edicion.invalid) {
      this.edicion.markAllAsTouched();

      return;
    }

    const { nombre, descripcion } = this.edicion.getRawValue();

    this.cambio.mutate(
      { id, cambio: { nombre: nombre.trim(), descripcion: descripcion.trim() } },
      { onSuccess: () => this.editando.set(null) },
    );
  }

  protected retirar(id: string): void {
    this.baja.mutate(id, { onSettled: () => this.confirmandoRetiro.set(null) });
  }
}
