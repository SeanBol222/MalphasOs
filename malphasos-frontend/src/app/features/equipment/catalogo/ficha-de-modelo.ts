import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CatalogoApi } from '../catalogo-api';
import {
  CLASES_DE_RIESGO,
  ClaseDeRiesgo,
  ETIQUETA_DE_RIESGO,
  FichaTecnica,
} from '../../../core/api/tipos';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/**
 * La ficha tecnica de un modelo: lo que dice su placa y su registro sanitario.
 *
 * <p><b>Pagina propia y no una fila mas de la lista</b>: son siete datos, y quien los llena tiene la
 * placa del equipo delante y la lee entera. Por lo mismo se guarda entera —el backend la reemplaza—, y
 * un campo que se deja vacio queda vacio.
 *
 * <p><b>Voltaje y amperaje viven aqui desde el 2026-10-05</b>; antes estaban en el tipo de equipo,
 * heredados del original, y son de cada modelo: dos balanzas de marcas distintas no consumen lo mismo.
 * Todo es opcional: la hoja de vida imprime «—» en lo que falte.
 */
@Component({
  selector: 'app-ficha-de-modelo',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './ficha-de-modelo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FichaDeModelo {
  readonly id = input.required<string>();

  private readonly api = inject(CatalogoApi);
  private readonly router = inject(Router);

  protected readonly modelo = this.api.detalleModelo(this.id);
  protected readonly cambio = this.api.describirModelo();

  protected readonly clases = CLASES_DE_RIESGO.map((clase) => ({
    valor: clase,
    etiqueta: ETIQUETA_DE_RIESGO[clase],
  }));

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    riesgo: ['' as ClaseDeRiesgo | ''],
    caracteristicas: ['', Validators.maxLength(250)],
    alimentacion: ['', Validators.maxLength(50)],
    voltaje: [null as number | null, Validators.min(1)],
    potencia: [null as number | null, Validators.min(1)],
    amperaje: [
      null as number | null,
      [Validators.min(0.01), Validators.pattern(/^\d+(\.\d{1,2})?$/)],
    ],
    frecuencia: [null as number | null, Validators.min(1)],
  });

  constructor() {
    effect(() => {
      const ficha = this.modelo.data()?.fichaTecnica;

      // Solo mientras nadie haya tocado el formulario: una recarga en segundo plano no debe borrar lo
      // que se esta escribiendo.
      if (ficha && this.formulario.pristine) {
        this.formulario.reset({
          riesgo: ficha.riesgo ?? '',
          caracteristicas: ficha.caracteristicas ?? '',
          alimentacion: ficha.alimentacion ?? '',
          voltaje: ficha.voltaje ?? null,
          potencia: ficha.potencia ?? null,
          amperaje: ficha.amperaje ?? null,
          frecuencia: ficha.frecuencia ?? null,
        });
      }
    });
  }

  protected readonly enviando = computed(() => this.cambio.isPending());
  protected readonly mensajeDeError = computed(() =>
    this.cambio.isError() ? traducirError(this.cambio.error()) : '',
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
    const texto = (valor: string) => valor.trim() || undefined;
    const numero = (valor: number | null) => (valor === null ? undefined : valor);

    // Lo vacio no viaja: para el backend, un dato que no llega es un dato vacio, porque la ficha se
    // reemplaza entera. Un cero tampoco seria «no se sabe»: lo rechazan los validadores.
    const ficha: FichaTecnica = Object.fromEntries(
      Object.entries({
        riesgo: datos.riesgo || undefined,
        caracteristicas: texto(datos.caracteristicas),
        alimentacion: texto(datos.alimentacion),
        voltaje: numero(datos.voltaje),
        potencia: numero(datos.potencia),
        amperaje: numero(datos.amperaje),
        frecuencia: numero(datos.frecuencia),
      }).filter(([, valor]) => valor !== undefined),
    );

    this.cambio.mutate(
      { id: this.id(), ficha },
      { onSuccess: () => void this.router.navigate(['/catalogo', 'modelos']) },
    );
  }
}
