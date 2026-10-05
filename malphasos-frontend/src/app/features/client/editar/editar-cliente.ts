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
 * <p><b>Razon social, pais y sigla</b>, porque es lo unico que el backend acepta cambiar: el documento
 * y su tipo son la llave natural del cliente y no se editan. La sigla va por su propia ruta y solo se
 * manda si cambio: se genero sola al crear el cliente, y corregirla puede chocar con la de otro. Ofrecerlos deshabilitados sugeriria que
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
  protected readonly cambioDeSigla = this.api.corregirSigla();

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    razonSocial: ['', [Validators.required, Validators.maxLength(50)]],
    idPais: [''],
    // El mismo formato que exige el backend; se admite en minusculas y el servidor la guarda en
    // mayusculas.
    sigla: ['', [Validators.required, Validators.pattern(/^\s*[A-Za-z][A-Za-z0-9]{2,5}\s*$/)]],
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
          sigla: datos.sigla ?? '',
        });
      }
    });
  }

  protected readonly enviando = computed(
    () => this.cambio.isPending() || this.cambioDeSigla.isPending(),
  );
  private readonly error = computed(
    () => this.cambio.error() ?? this.cambioDeSigla.error() ?? this.cliente.error(),
  );
  protected readonly mensajeDeError = computed(() =>
    this.error() ? traducirError(this.error()) : '',
  );
  protected readonly detalles = computed(() => detallesDe(this.error()));

  protected malo(campo: string): boolean {
    const control = this.formulario.get(campo);

    return !!control && control.invalid && control.touched;
  }

  protected enviar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    const { razonSocial, idPais, sigla } = this.formulario.getRawValue();
    const nuevaSigla = sigla.trim().toUpperCase();
    const volver = () => void this.router.navigate(['/clientes', this.id()]);
    // Primero los datos y despues la sigla, y solo si cambio: si la sigla la tiene otro cliente, el
    // 409 se ve aqui y lo demas ya quedo guardado.
    const despues = () =>
      nuevaSigla === this.cliente.data()?.sigla
        ? volver()
        : this.cambioDeSigla.mutate({ id: this.id(), sigla: nuevaSigla }, { onSuccess: volver });

    // Igual que en el alta: una cadena vacia no es un identificador. Aqui ademas quitar el pais de
    // un cliente que lo tenia no es lo que este formulario ofrece, y mandar "" lo intentaria.
    this.cambio.mutate(
      { id: this.id(), cambio: idPais ? { razonSocial, idPais } : { razonSocial } },
      { onSuccess: despues },
    );
  }
}
