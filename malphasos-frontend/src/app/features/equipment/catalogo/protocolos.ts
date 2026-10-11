import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { CatalogoApi } from '../catalogo-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';
import { mismoNombreQue, normalizar, parecidosA } from './parecidos';

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
  imports: [ReactiveFormsModule],
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

  protected readonly filtro = this.formBuilder.nonNullable.control('');
  private readonly textoDelFiltro = toSignal(this.filtro.valueChanges, { initialValue: '' });

  /** Filtrados por nombre o descripcion, sin tildes ni mayusculas; los vigentes primero. */
  protected readonly visibles = computed(() => {
    const buscado = normalizar(this.textoDelFiltro());

    return [...(this.protocolos.data() ?? [])]
      .filter(
        (protocolo) =>
          !buscado ||
          normalizar(protocolo.nombre ?? '').includes(buscado) ||
          normalizar(protocolo.descripcion ?? '').includes(buscado),
      )
      .sort((a, b) => Number(b.estadoActivo) - Number(a.estadoActivo));
  });

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
      { onSuccess: () => this.nuevo.reset() },
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
