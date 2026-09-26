import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AreaApi } from '../area-api';
import { EncargadoApi } from '../encargado-api';
import { SedeApi } from '../sede-api';
import { TipoDeAsignacion } from '../../../core/api/tipos';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/**
 * Registro del encargado de una sede o de una de sus areas.
 *
 * <p><b>Una sola operacion crea la persona y la asigna</b>, y eso es del backend: el encargado no
 * existe sin algo a cargo. Por eso este formulario pide datos de persona —cedula, nombres, contacto—
 * y, a la vez, que se elija la asignacion.
 *
 * <p>La asignacion se ofrece como una sola lista con la sede y sus areas dentro, en lugar de dos
 * campos —tipo e identificador— que el contrato declara por separado. Elegir «area de servicio» y
 * luego un identificador de sede es un error que el formulario no deberia permitir cometer: aqui cada
 * opcion lleva su tipo consigo.
 *
 * <p>El encargado <b>no recibe acceso a la aplicacion</b>: es el caso que explica que
 * {@code PersonType.MANAGER} exista sin rol. Se dice en pantalla para que nadie espere una cuenta.
 */
@Component({
  selector: 'app-nuevo-encargado',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './nuevo-encargado.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuevoEncargado {
  /** La sede desde la que se registra. Viene de la ruta. */
  readonly id = input.required<string>();

  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly sede = inject(SedeApi).detalle(this.id);
  protected readonly areas = inject(AreaApi).listarDe(this.id);
  protected readonly alta = inject(EncargadoApi).registrar();

  protected readonly formulario = this.formBuilder.nonNullable.group({
    cedula: ['', [Validators.required, Validators.maxLength(11)]],
    primerNombre: ['', Validators.required],
    segundoNombre: [''],
    primerApellido: ['', Validators.required],
    segundoApellido: [''],
    correo: ['', [Validators.required, Validators.email]],
    telefono: [''],
    // Guarda "HEADQUARTER:<id>" o "SERVICE_AREA:<id>": el tipo viaja pegado a la opcion elegida.
    asignacion: ['', Validators.required],
  });

  /** Las asignaciones posibles: la sede, y cada area abierta de la sede. */
  protected readonly asignaciones = computed(() => {
    const sede = this.sede.data();
    const opciones = sede ? [{ valor: `HEADQUARTER:${this.id()}`, etiqueta: `Toda la sede ${sede.nombre}` }] : [];

    for (const area of this.areas.data() ?? []) {
      // Un area cerrada no admite un encargado nuevo: el backend la rechaza como referencia
      // retirada, y ofrecerla seria invitar a un error que solo se ve al guardar.
      if (area.estadoActivo) {
        opciones.push({ valor: `SERVICE_AREA:${area.id}`, etiqueta: `Área: ${area.nombre}` });
      }
    }

    return opciones;
  });

  protected readonly enviando = computed(() => this.alta.isPending());
  protected readonly mensajeDeError = computed(() =>
    this.alta.isError() ? traducirError(this.alta.error()) : '',
  );
  protected readonly detalles = computed(() => detallesDe(this.alta.error()));

  protected malo(campo: string): boolean {
    const control = this.formulario.get(campo);

    return !!control && control.invalid && control.touched;
  }

  protected enviar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    const datos = this.formulario.getRawValue();
    const [tipo, idAsignacion] = datos.asignacion.split(':');

    this.alta.mutate(
      {
        cedula: datos.cedula,
        primerNombre: datos.primerNombre,
        segundoNombre: datos.segundoNombre || undefined,
        primerApellido: datos.primerApellido,
        segundoApellido: datos.segundoApellido || undefined,
        correos: [{ valor: datos.correo }],
        // El telefono es opcional, y una lista con un valor vacio no es "sin telefono".
        telefonos: datos.telefono ? [{ valor: datos.telefono }] : [],
        tipo: tipo as TipoDeAsignacion,
        idAsignacion,
      },
      { onSuccess: () => void this.router.navigate(['/sedes', this.id()]) },
    );
  }
}
