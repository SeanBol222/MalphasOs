import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClienteApi } from '../cliente-api';
import { UbicacionApi } from '../../location/ubicacion-api';
import { detallesDe, traducirError } from '../../../core/errores/traducir';
import { Buscador, opcionesDe } from '../../../shared/buscador/buscador';

/**
 * Edicion de un cliente.
 *
 * <p><b>Solo razon social y pais</b>, porque es lo unico que el backend acepta cambiar: el documento
 * y su tipo son la llave natural del cliente y no se editan. Ofrecerlos deshabilitados sugeriria que
 * alguien con mas permisos podria; no es el caso, y no existe esa operacion.
 */
@Component({
  selector: 'app-editar-cliente',
  imports: [ReactiveFormsModule, RouterLink, Buscador],
  templateUrl: './editar-cliente.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditarCliente {
  readonly id = input.required<string>();

  private readonly api = inject(ClienteApi);
  private readonly router = inject(Router);

  protected readonly cliente = this.api.detalle(this.id);
  private readonly paises = inject(UbicacionApi).listarPaises();

  protected readonly opcionesDePais = computed(() => opcionesDe(this.paises.data()));
  protected readonly cambio = this.api.editar();

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    razonSocial: ['', [Validators.required, Validators.maxLength(50)]],
    idPais: [''],
  });

  constructor() {
    // El formulario se rellena cuando el cliente llega, no antes: la consulta es asincrona y al
    // construir la pantalla todavia no hay datos. Y solo si el usuario no ha escrito nada, para no
    // pisar lo que acaba de teclear si la consulta se refresca por detras.
    effect(() => {
      const datos = this.cliente.data();

      if (datos && this.formulario.pristine) {
        this.formulario.setValue({
          razonSocial: datos.razonSocial ?? '',
          idPais: datos.idPais ?? '',
        });
      }
    });
  }

  protected readonly enviando = computed(() => this.cambio.isPending());
  protected readonly mensajeDeError = computed(() =>
    this.cambio.isError() || this.cliente.isError()
      ? traducirError(this.cambio.error() ?? this.cliente.error())
      : '',
  );
  protected readonly detalles = computed(() =>
    detallesDe(this.cambio.error() ?? this.cliente.error()),
  );

  protected malo(campo: string): boolean {
    const control = this.formulario.get(campo);

    return !!control && control.invalid && control.touched;
  }

  protected enviar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    const { razonSocial, idPais } = this.formulario.getRawValue();

    // Igual que en el alta: una cadena vacia no es un identificador. Aqui ademas quitar el pais de
    // un cliente que lo tenia no es lo que este formulario ofrece, y mandar "" lo intentaria.
    this.cambio.mutate(
      { id: this.id(), cambio: idPais ? { razonSocial, idPais } : { razonSocial } },
      { onSuccess: () => void this.router.navigate(['/clientes', this.id()]) },
    );
  }
}
