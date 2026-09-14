import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClienteApi } from '../cliente-api';
import {
  ETIQUETA_DE_IDENTIFICACION,
  NuevoCliente,
  TIPOS_DE_IDENTIFICACION,
} from '../../../core/api/tipos';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/**
 * Alta de un cliente. Cierra la mitad de escritura de RF-08.
 *
 * <p>Las cotas de los campos no se inventan: salen del contrato que el backend publica —documento
 * de 11 caracteres, razon social de 50—. Si el backend las cambia, se regeneran los tipos y esto
 * queda desalineado a la vista en vez de fallar en produccion.
 */
@Component({
  selector: 'app-nuevo-cliente',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './nuevo-cliente.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuevoClienteComponent {
  private readonly router = inject(Router);

  protected readonly tipos = TIPOS_DE_IDENTIFICACION;
  protected readonly etiquetas = ETIQUETA_DE_IDENTIFICACION;

  protected readonly alta = inject(ClienteApi).crear();

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    razonSocial: ['', [Validators.required, Validators.maxLength(50)]],
    tipoIdentificacion: ['NIT_JURIDICO' as NuevoCliente['tipoIdentificacion'], Validators.required],
    documento: ['', [Validators.required, Validators.maxLength(11)]],
  });

  protected readonly enviando = computed(() => this.alta.isPending());
  protected readonly mensajeDeError = computed(() =>
    this.alta.isError() ? traducirError(this.alta.error()) : '',
  );
  protected readonly detalles = computed(() => detallesDe(this.alta.error()));

  /** Si el campo debe mostrar su error: invalido y ya tocado, no antes. */
  protected malo(campo: string): boolean {
    const control = this.formulario.get(campo);

    return !!control && control.invalid && control.touched;
  }

  protected enviar(): void {
    if (this.formulario.invalid) {
      // Marcar todo como tocado es lo que hace visibles los errores de los campos que el usuario
      // nunca llego a enfocar. Sin esto, pulsar Guardar en un formulario vacio no dice nada.
      this.formulario.markAllAsTouched();

      return;
    }

    this.alta.mutate(this.formulario.getRawValue(), {
      onSuccess: () => void this.router.navigate(['/clientes']),
    });
  }
}
