import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CatalogoApi } from '../catalogo-api';
import { UbicacionApi } from '../../location/ubicacion-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';
import { Buscador, opcionesDe } from '../../../shared/buscador/buscador';

/**
 * Los fabricantes del catalogo, con su pais de origen.
 *
 * <p>El pais se elige de una lista: el contrato lo pide como identificador y un campo de texto
 * obligaria a teclear un UUID. Es opcional, igual que en el cliente, porque de algunos fabricantes
 * solo se conoce el nombre.
 *
 * <p><b>Fabricante y marca no son lo mismo</b>, y es la confusion mas probable de esta pantalla: la
 * marca es la linea comercial del equipo y el fabricante es quien lo produce, que puede ser otra
 * empresa y en otro pais. El esquema los separa y el modelo los junta.
 */
@Component({
  selector: 'app-fabricantes',
  imports: [ReactiveFormsModule, Buscador],
  templateUrl: './fabricantes.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Fabricantes {
  private readonly api = inject(CatalogoApi);
  private readonly sesion = inject(Sesion);

  protected readonly fabricantes = this.api.listarFabricantes();
  private readonly paises = inject(UbicacionApi).listarPaises();

  protected readonly opcionesDePais = computed(() => opcionesDe(this.paises.data()));
  protected readonly alta = this.api.crearFabricante();
  protected readonly baja = this.api.retirarFabricante();

  protected readonly puedeEscribir = computed(() => this.sesion.puede('equipment.write'));

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    idPais: [''],
  });

  protected readonly hayError = computed(
    () => this.fabricantes.isError() || this.alta.isError() || this.baja.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(this.fabricantes.error() ?? this.alta.error() ?? this.baja.error()),
  );

  /** El pais de cada fabricante, en palabras. Sin catalogo no se inventa: se dice que no esta. */
  protected pais(idPais: string | undefined): string {
    if (!idPais) {
      return 'Sin especificar';
    }

    return this.paises.data()?.find((p) => p.id === idPais)?.nombre ?? 'No disponible';
  }

  protected agregar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    const { nombre, idPais } = this.formulario.getRawValue();

    this.alta.mutate(idPais ? { nombre, idPais } : { nombre }, {
      onSuccess: () => this.formulario.reset(),
    });
  }

  protected retirar(id: string): void {
    this.baja.mutate(id);
  }
}
