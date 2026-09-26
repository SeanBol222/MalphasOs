import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClienteApi } from '../cliente-api';
import { SedeApi } from '../sede-api';
import { UbicacionApi } from '../../location/ubicacion-api';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/**
 * Alta de una sede de un cliente.
 *
 * <p><b>La ciudad se elige de una lista, y la lista se recorta al pais del cliente.</b> Es
 * obligatoria en el contrato, de modo que sin selector esta pantalla exigiria teclear un UUID: seria
 * inservible sin abrir la base de datos al lado. Y recortarla no es cosmetico: el catalogo entero
 * incluye ciudades de otros paises, y una sede en la ciudad equivocada es un error que nadie detecta
 * hasta que un ingeniero viaja.
 *
 * <p>Si el cliente no tiene pais —el contrato lo permite— se ofrecen todas, porque no hay con que
 * recortar. Es la unica lectura posible de «no se sabe».
 */
@Component({
  selector: 'app-nueva-sede',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './nueva-sede.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuevaSede {
  /** El cliente al que se le abre la sede. Viene de la ruta. */
  readonly id = input.required<string>();

  private readonly router = inject(Router);
  private readonly ubicacion = inject(UbicacionApi);

  protected readonly cliente = inject(ClienteApi).detalle(this.id);
  protected readonly alta = inject(SedeApi).crear();
  private readonly ciudades = this.ubicacion.listarCiudades();

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    idCiudad: ['', Validators.required],
    calle: ['', Validators.required],
    carrera: ['', Validators.required],
    numero: ['', Validators.required],
  });

  /** Las ciudades que se ofrecen: las del pais del cliente, o todas si no tiene pais. */
  protected readonly ciudadesOfrecidas = computed(() => {
    const todas = this.ciudades.data() ?? [];
    const pais = this.cliente.data()?.idPais;

    return pais ? todas.filter((ciudad) => ciudad.idPais === pais) : todas;
  });

  /** Que el recorte deje la lista vacia no es lo mismo que que el catalogo no haya cargado. */
  protected readonly sinCiudadesDelPais = computed(
    () => this.ciudades.isSuccess() && this.ciudadesOfrecidas().length === 0,
  );

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

    this.alta.mutate(
      { idCliente: this.id(), sede: this.formulario.getRawValue() },
      { onSuccess: (sede) => void this.router.navigate(['/sedes', sede.id]) },
    );
  }
}
