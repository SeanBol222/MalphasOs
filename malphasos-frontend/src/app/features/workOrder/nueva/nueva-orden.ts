import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  untracked,
} from '@angular/core';
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

  /**
   * Cliente y sede ya elegidos, desde la ficha de una sede —«Nueva orden aquí», del rediseño del
   * 2026-10-11—. Llegan como parámetros de consulta y son solo el punto de partida: se pueden cambiar.
   */
  readonly cliente = input<string>();
  readonly sede = input<string>();

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
    garantia: [false],
  });

  /** Los formularios reactivos no son senales; sin esto, la sede no se recargaria al cambiar de cliente. */
  private readonly valores = toSignal(
    this.formulario.valueChanges.pipe(map(() => this.formulario.getRawValue())),
    { initialValue: this.formulario.getRawValue() },
  );

  /** La garantía es una marca del correctivo (2026-10-16): solo se ofrece para él. */
  protected readonly esCorrectivo = computed(() => this.valores().tipoServicio === 'CORRECTIVO');

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

  /**
   * Pone el cliente y la sede que traiga la ruta, una sola vez y solo si nadie eligió nada aún.
   *
   * <p>Marca el cliente como el anterior <b>antes</b> de ponerlo: si no, el efecto de arriba lo vería
   * como un cambio de cliente y borraría la sede recién puesta.
   */
  private readonly precargar = effect(() => {
    const cliente = this.cliente();
    const sede = this.sede();

    if (!cliente) {
      return;
    }

    untracked(() => {
      if (this.formulario.getRawValue().idCliente) {
        return;
      }
      this.clienteAnterior = cliente;
      this.formulario.patchValue({ idCliente: cliente, idSede: sede ?? '' });
    });
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

    const valores = this.formulario.getRawValue();
    // Marcada y luego cambiado el tipo, la casilla se esconde pero conserva su valor: el servidor
    // rechazaría la garantía de un preventivo, así que solo viaja con un correctivo.
    const garantia = valores.tipoServicio === 'CORRECTIVO' && valores.garantia;

    this.alta.mutate(
      { ...valores, garantia },
      {
        // A la ficha y no al listado: lo siguiente es anadirle los equipos, y estan ahi.
        onSuccess: (orden) => void this.router.navigate(['/ordenes', orden.id]),
      },
    );
  }
}
