import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { ClienteApi } from '../../client/cliente-api';
import { SedeApi } from '../../client/sede-api';
import { OrdenApi } from '../orden-api';
import { Buscador, Opcion } from '../../../shared/buscador/buscador';
import {
  ETIQUETA_DE_PERIODICIDAD,
  ETIQUETA_DE_TIPO_DE_SERVICIO,
  PERIODICIDADES,
  TIPOS_DE_SERVICIO,
} from '../../../core/api/tipos';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/**
 * Programa una orden de trabajo: para quien, donde, cuando y de que clase.
 *
 * <p><b>Los equipos no se eligen aqui.</b> Una orden se programa primero y se le suman los equipos
 * despues, que es lo que el API ofrece y lo que ocurre de verdad: se acuerda la visita y luego se decide
 * sobre que se trabaja. Al guardar se lleva a la ficha, que es donde se anaden.
 *
 * <p>El cliente se busca escribiendo y la sede se despliega: hay muchos clientes y pocas sedes por
 * cliente. Y <b>solo se ofrecen sedes abiertas</b>: el backend rechaza una cerrada, y ofrecerla seria
 * invitar a un error que solo se ve al guardar.
 */
@Component({
  selector: 'app-nueva-orden',
  imports: [ReactiveFormsModule, RouterLink, Buscador],
  templateUrl: './nueva-orden.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuevaOrden {
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly periodicidades = PERIODICIDADES;
  protected readonly etiquetasDePeriodicidad = ETIQUETA_DE_PERIODICIDAD;
  protected readonly tiposDeServicio = TIPOS_DE_SERVICIO;
  protected readonly etiquetasDeServicio = ETIQUETA_DE_TIPO_DE_SERVICIO;

  protected readonly alta = inject(OrdenApi).programar();

  protected readonly formulario = this.formBuilder.nonNullable.group({
    idCliente: ['', Validators.required],
    idSede: ['', Validators.required],
    fechaMantenimiento: ['', Validators.required],
    periodicidad: ['MENSUAL' as (typeof PERIODICIDADES)[number], Validators.required],
    tipoServicio: ['PREVENTIVO' as (typeof TIPOS_DE_SERVICIO)[number], Validators.required],
  });

  /** Los formularios reactivos no son senales; sin esto, la sede no se recargaria al cambiar de cliente. */
  private readonly valores = toSignal(
    this.formulario.valueChanges.pipe(map(() => this.formulario.getRawValue())),
    { initialValue: this.formulario.getRawValue() },
  );

  private readonly clientes = inject(ClienteApi).listar();
  protected readonly sedes = inject(SedeApi).listarDe(computed(() => this.valores().idCliente));

  /**
   * Los clientes, como opciones del buscador.
   *
   * <p>A mano y no con {@code opcionesDe}: un cliente se llama {@code razonSocial} y no {@code nombre}.
   */
  protected readonly opcionesDeCliente = computed<readonly Opcion[]>(() =>
    (this.clientes.data() ?? [])
      .filter((cliente) => !!cliente.id && !!cliente.razonSocial)
      .map((cliente) => ({ id: cliente.id!, etiqueta: cliente.razonSocial! })),
  );

  protected readonly sedesAbiertas = computed(() =>
    (this.sedes.data() ?? []).filter((sede) => sede.estadoActivo),
  );

  /** Que el cliente no tenga sedes abiertas es distinto de que no hayan llegado. */
  protected readonly sinSedes = computed(
    () => !!this.valores().idCliente && this.sedes.isSuccess() && this.sedesAbiertas().length === 0,
  );

  protected readonly enviando = computed(() => this.alta.isPending());
  protected readonly mensajeDeError = computed(() =>
    this.alta.isError() ? traducirError(this.alta.error()) : '',
  );
  protected readonly detalles = computed(() => detallesDe(this.alta.error()));

  /**
   * Al cambiar de cliente, la sede elegida deja de valer: seria la sede de otro.
   *
   * <p>Va en un efecto y no en un {@code (change)} del campo porque el cliente se elige con el buscador,
   * que no es un {@code select} y no emite ese evento. Se intentó así primero, y el manejador no se
   * llamaba nunca: el campo aceptaba un cliente nuevo dejando puesta la sede del anterior.
   */
  private readonly limpiarSedeAlCambiarDeCliente = effect(() => {
    const cliente = this.valores().idCliente;

    if (cliente !== this.clienteAnterior) {
      this.clienteAnterior = cliente;

      if (this.formulario.getRawValue().idSede) {
        this.formulario.patchValue({ idSede: '' });
      }
    }
  });

  protected malo(campo: string): boolean {
    const control = this.formulario.get(campo);

    return !!control && control.invalid && control.touched;
  }

  /**
   * El cliente que estaba elegido la ultima vez que se miró.
   *
   * <p>Hace falta porque limpiar la sede dispara {@code valueChanges} y con ello el propio efecto: sin
   * comparar contra el anterior, se limpiaria en bucle.
   */
  private clienteAnterior = '';

  protected enviar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    this.alta.mutate(this.formulario.getRawValue(), {
      // A la ficha y no al listado: lo siguiente es anadirle los equipos, y estan ahi.
      onSuccess: (orden) => void this.router.navigate(['/ordenes', orden.id]),
    });
  }
}
