import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReporteApi } from '../reporte-api';
import { QuienRecibe } from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';

/**
 * A quién se entregó el equipo (V31, 2026-10-16): nombre, cédula y cargo, en texto, porque quien
 * recibe muchas veces no está registrado en el sistema. Es el último bloque del reporte, como en el
 * informe impreso, donde va junto a su firma.
 *
 * <p>Los tres campos en blanco borran lo que hubiera. Cédula o cargo sin nombre no dicen quién recibió,
 * y el servidor los rechaza: aquí se avisa antes.
 */
@Component({
  selector: 'app-entrega-del-equipo',
  imports: [ReactiveFormsModule],
  templateUrl: './entrega-del-equipo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EntregaDelEquipo {
  readonly idReporte = input.required<string>();
  readonly recibe = input<QuienRecibe | null | undefined>(null);
  readonly editable = input(false);

  private readonly fb = inject(FormBuilder);
  protected readonly registro = inject(ReporteApi).registrarEntrega();

  protected readonly formulario = this.fb.nonNullable.group({ nombre: '', cedula: '', cargo: '' });

  private readonly escrito = toSignal(this.formulario.valueChanges);

  protected readonly faltaElNombre = computed(() => {
    this.escrito();
    const { nombre, cedula, cargo } = this.formulario.getRawValue();

    return !nombre.trim() && !!(cedula.trim() || cargo.trim());
  });

  protected readonly mensajeDeError = computed(() => traducirError(this.registro.error()));

  constructor() {
    effect(() => {
      const recibe = this.recibe();

      if (this.formulario.dirty) {
        return;
      }

      this.formulario.setValue(
        { nombre: recibe?.nombre ?? '', cedula: recibe?.cedula ?? '', cargo: recibe?.cargo ?? '' },
        { emitEvent: false },
      );
    });

    effect(() => {
      if (this.editable()) {
        this.formulario.enable({ emitEvent: false });
      } else {
        this.formulario.disable({ emitEvent: false });
      }
    });
  }

  protected guardar(): void {
    if (this.faltaElNombre()) {
      this.formulario.markAllAsTouched();

      return;
    }

    const { nombre, cedula, cargo } = this.formulario.getRawValue();
    const texto = (t: string) => t.trim() || undefined;

    this.registro.mutate(
      {
        id: this.idReporte(),
        recibe: { nombre: texto(nombre), cedula: texto(cedula), cargo: texto(cargo) },
      },
      { onSuccess: () => this.formulario.markAsPristine() },
    );
  }
}
