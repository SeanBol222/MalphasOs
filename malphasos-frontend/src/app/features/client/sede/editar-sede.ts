import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { SedeApi } from '../sede-api';
import { UbicacionApi } from '../../location/ubicacion-api';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/**
 * Edicion de una sede: su nombre, su ciudad y su direccion.
 *
 * <p>Aqui la ciudad <b>no se recorta por pais</b>, al contrario que en el alta, y es a proposito: el
 * cliente no se consulta en esta pantalla y recortar con un dato que no se tiene esconderia la ciudad
 * correcta. Corregir una ciudad mal elegida es justo lo que se viene a hacer aqui.
 */
@Component({
  selector: 'app-editar-sede',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './editar-sede.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditarSede {
  readonly id = input.required<string>();

  private readonly api = inject(SedeApi);
  private readonly router = inject(Router);

  protected readonly sede = this.api.detalle(this.id);
  protected readonly ciudades = inject(UbicacionApi).listarCiudades();
  protected readonly cambio = this.api.editar();

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    idCiudad: ['', Validators.required],
    calle: ['', Validators.required],
    carrera: ['', Validators.required],
    numero: ['', Validators.required],
  });

  constructor() {
    effect(() => {
      const datos = this.sede.data();

      if (datos && this.formulario.pristine) {
        this.formulario.setValue({
          nombre: datos.nombre ?? '',
          idCiudad: datos.idCiudad ?? '',
          calle: datos.calle ?? '',
          carrera: datos.carrera ?? '',
          numero: datos.numero ?? '',
        });
      }
    });
  }

  protected readonly enviando = computed(() => this.cambio.isPending());
  protected readonly mensajeDeError = computed(() =>
    this.cambio.isError() || this.sede.isError()
      ? traducirError(this.cambio.error() ?? this.sede.error())
      : '',
  );
  protected readonly detalles = computed(() => detallesDe(this.cambio.error()));

  protected malo(campo: string): boolean {
    const control = this.formulario.get(campo);

    return !!control && control.invalid && control.touched;
  }

  protected enviar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    this.cambio.mutate(
      { id: this.id(), cambio: this.formulario.getRawValue() },
      { onSuccess: () => void this.router.navigate(['/sedes', this.id()]) },
    );
  }
}
