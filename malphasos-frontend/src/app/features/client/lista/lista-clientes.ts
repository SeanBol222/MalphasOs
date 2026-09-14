import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClienteApi } from '../cliente-api';
import { ETIQUETA_DE_IDENTIFICACION, TipoIdentificacion } from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';

/** Los clientes registrados. Cierra la mitad de lectura de RF-08. */
@Component({
  selector: 'app-lista-clientes',
  imports: [RouterLink],
  templateUrl: './lista-clientes.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaClientes {
  protected readonly clientes = inject(ClienteApi).listar();

  protected readonly mensajeDeError = computed(() => traducirError(this.clientes.error()));

  protected etiqueta(tipo: TipoIdentificacion | undefined): string {
    return tipo ? ETIQUETA_DE_IDENTIFICACION[tipo] : '';
  }
}
