import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CatalogoApi } from '../catalogo-api';
import { detallesDe, traducirError } from '../../../core/errores/traducir';
import { Buscador } from '../../../shared/buscador/buscador';
import { tecnologiasAutorizadas } from './tecnologias';

/**
 * Edicion de un tipo de equipo.
 *
 * <p><b>Sin la modalidad de verificacion</b>, y no es un olvido: el backend la dejo fuera de
 * {@code EquipmentTypeUpdateRequest} y le dio ruta propia. Se cambia desde la lista del catalogo, que
 * es donde esta la operacion que le corresponde.
 */
@Component({
  selector: 'app-editar-tipo',
  imports: [ReactiveFormsModule, RouterLink, Buscador],
  templateUrl: './editar-tipo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditarTipo {
  readonly id = input.required<string>();

  private readonly api = inject(CatalogoApi);
  private readonly router = inject(Router);

  protected readonly tipo = this.api.detalleTipo(this.id);

  /** Las mismas autorizadas que en el alta, y por el mismo motivo. */
  protected readonly tecnologias = computed(() => tecnologiasAutorizadas(this.tipos.data()));

  private readonly tipos = this.api.listarTipos();
  protected readonly cambio = this.api.editarTipo();

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    tecnologiaPredominante: ['', [Validators.required, Validators.maxLength(50)]],
    definicionTecnica: ['', [Validators.required, Validators.maxLength(250)]],
    recomendacionesCuidado: ['', [Validators.required, Validators.maxLength(250)]],
    uso: ['', Validators.maxLength(250)],
    limpiezaCotidiana: ['', Validators.maxLength(250)],
    valorUnitarioMantenimiento: [null as number | null],
  });

  constructor() {
    effect(() => {
      const datos = this.tipo.data();

      if (datos && this.formulario.pristine) {
        this.formulario.setValue({
          nombre: datos.nombre ?? '',
          tecnologiaPredominante: datos.tecnologiaPredominante ?? '',
          definicionTecnica: datos.definicionTecnica ?? '',
          recomendacionesCuidado: datos.recomendacionesCuidado ?? '',
          uso: datos.uso ?? '',
          limpiezaCotidiana: datos.limpiezaCotidiana ?? '',
          valorUnitarioMantenimiento: datos.valorUnitarioMantenimiento ?? null,
        });
      }
    });
  }

  protected readonly enviando = computed(() => this.cambio.isPending());
  protected readonly mensajeDeError = computed(() =>
    this.cambio.isError() || this.tipo.isError()
      ? traducirError(this.cambio.error() ?? this.tipo.error())
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

    const datos = this.formulario.getRawValue();

    this.cambio.mutate(
      {
        id: this.id(),
        cambio: {
          nombre: datos.nombre,
          tecnologiaPredominante: datos.tecnologiaPredominante,
          definicionTecnica: datos.definicionTecnica,
          recomendacionesCuidado: datos.recomendacionesCuidado,
          // Se mandan siempre, vacios incluidos: para el backend un blanco es «vaciar», y es la unica
          // forma de quitar un uso que ya no aplica.
          uso: datos.uso,
          limpiezaCotidiana: datos.limpiezaCotidiana,
          ...(datos.valorUnitarioMantenimiento === null
            ? {}
            : { valorUnitarioMantenimiento: datos.valorUnitarioMantenimiento }),
        },
      },
      { onSuccess: () => void this.router.navigate(['/catalogo']) },
    );
  }
}
