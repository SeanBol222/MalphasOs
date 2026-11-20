import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Sentido } from './columna-ordenable';

/**
 * Lo que en un telefono sustituye a la cabecera de una tabla que se lee como tarjetas (2026-11-20):
 * cuantos resultados hay y un boton que invierte el orden por la primera columna. En escritorio no se
 * pinta, porque la cabecera de la tabla ya ordena.
 */
@Component({
  selector: 'app-orden-movil',
  template: `<div class="mt-2 flex items-center justify-between md:hidden">
    <span class="text-sm text-neutral-700" data-cuantos>
      {{ cuantos() === 1 ? '1 resultado' : cuantos() + ' resultados' }}
    </span>
    <button
      class="min-h-tactil px-0.5 text-sm font-semibold"
      type="button"
      data-ordenar-movil
      (click)="invertir.emit()"
    >
      {{ etiqueta() }}: {{ sentido() === 'asc' ? 'de la A a la Z' : 'de la Z a la A' }}
      <span aria-hidden="true">{{ sentido() === 'asc' ? '▲' : '▼' }}</span>
    </button>
  </div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdenMovil {
  readonly etiqueta = input.required<string>();
  readonly sentido = input.required<Sentido>();
  readonly cuantos = input.required<number>();
  readonly invertir = output<void>();
}
