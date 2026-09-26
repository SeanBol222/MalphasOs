import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClienteApi } from '../cliente-api';
import { UbicacionApi } from '../../location/ubicacion-api';
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
 *
 * <p><b>El pais se elige de una lista y no se escribe.</b> El contrato lo pide como identificador, de
 * modo que un campo de texto obligaria a teclear un UUID: la pantalla seria inservible sin consultar
 * la base de datos. Es opcional en el contrato y aqui tambien, porque hay clientes de los que solo se
 * conoce el documento cuando se registran.
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
  protected readonly paises = inject(UbicacionApi).listarPaises();

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    razonSocial: ['', [Validators.required, Validators.maxLength(50)]],
    tipoIdentificacion: ['NIT_JURIDICO' as NuevoCliente['tipoIdentificacion'], Validators.required],
    documento: ['', [Validators.required, Validators.maxLength(11)]],
    idPais: [''],
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

    const { idPais, ...resto } = this.formulario.getRawValue();

    // Sin pais elegido no se manda la clave: una cadena vacia no es un identificador, y el backend
    // la rechazaria como referencia inexistente en vez de entenderla como «no se sabe».
    // Se va a la ficha del cliente creado y no al listado: lo siguiente que hace quien registra un
    // cliente es anadirle un correo o una sede, y las dos cosas estan ahi.
    this.alta.mutate(idPais ? { ...resto, idPais } : resto, {
      onSuccess: (cliente) => void this.router.navigate(['/clientes', cliente.id]),
    });
  }
}
