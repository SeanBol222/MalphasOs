import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CatalogoApi } from '../catalogo-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';

/**
 * Las marcas del catalogo: el primer eslabon de la cadena.
 *
 * <p>Una marca es un nombre y nada mas, de modo que se maneja en linea: un formulario de un solo campo
 * no merece una pagina propia, y verlas junto a lo que se construye con ellas es como se trabaja.
 */
@Component({
  selector: 'app-marcas',
  imports: [ReactiveFormsModule],
  templateUrl: './marcas.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Marcas {
  private readonly api = inject(CatalogoApi);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly marcas = this.api.listarMarcas();
  protected readonly alta = this.api.crearMarca();
  protected readonly cambio = this.api.renombrarMarca();
  protected readonly baja = this.api.retirarMarca();

  private readonly sesion = inject(Sesion);

  /** Señal y no booleano: las autoridades se releen en cada evento de Keycloak. */
  protected readonly puedeEscribir = computed(() => this.sesion.puede('equipment.write'));

  protected readonly nombreNuevo = this.formBuilder.nonNullable.control('', [
    Validators.required,
    Validators.maxLength(50),
  ]);
  protected readonly nombreEditado = this.formBuilder.nonNullable.control('', [
    Validators.required,
    Validators.maxLength(50),
  ]);

  protected readonly renombrando = signal<string | null>(null);

  protected readonly hayError = computed(
    () =>
      this.marcas.isError() || this.alta.isError() || this.cambio.isError() || this.baja.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.marcas.error() ?? this.alta.error() ?? this.cambio.error() ?? this.baja.error(),
    ),
  );

  protected agregar(): void {
    if (this.nombreNuevo.invalid) {
      this.nombreNuevo.markAsTouched();

      return;
    }

    this.alta.mutate({ nombre: this.nombreNuevo.value }, { onSuccess: () => this.nombreNuevo.reset() });
  }

  protected empezarARenombrar(id: string, nombre: string | undefined): void {
    this.nombreEditado.setValue(nombre ?? '');
    this.renombrando.set(id);
  }

  protected renombrar(id: string): void {
    if (this.nombreEditado.invalid) {
      this.nombreEditado.markAsTouched();

      return;
    }

    this.cambio.mutate(
      { id, cambio: { nombre: this.nombreEditado.value } },
      { onSuccess: () => this.renombrando.set(null) },
    );
  }

  protected retirar(id: string): void {
    this.baja.mutate(id);
  }
}
