import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/** Hacia donde ordena una columna. */
export type Sentido = 'asc' | 'desc';

/** El sentido contrario, para el boton que lo invierte. */
export function invertido(sentido: Sentido): Sentido {
  return sentido === 'asc' ? 'desc' : 'asc';
}

/**
 * Compara dos textos en el sentido pedido, en castellano: «Báscula» va antes que «Bomba».
 *
 * <p>Las piezas del catalogo ordenan por su primera columna (2026-11-20, pedido por el usuario), de la A a
 * la Z por defecto.
 */
export function compararEn(sentido: Sentido, a: string | undefined, b: string | undefined): number {
  return (sentido === 'asc' ? 1 : -1) * (a ?? '').localeCompare(b ?? '', 'es');
}

/**
 * La cabecera de una columna que ordena: cada pulsacion invierte el sentido. La flecha lo dibuja y
 * {@code aria-sort} lo dice a un lector de pantalla, como en el listado de ordenes.
 *
 * <p>Se pone sobre el {@code th}, que conserva su {@code scope}: {@code <th scope="col"
 * app-columna-ordenable etiqueta="Tipo" [sentido]="sentido()" (invertir)="..."></th>}.
 */
@Component({
  selector: 'th[app-columna-ordenable]',
  template: `<button
    class="inline-flex min-h-tactil items-center gap-0.5 font-[inherit] underline-offset-2 hover:underline"
    type="button"
    data-ordenar
    [attr.aria-label]="
      etiqueta() +
      (sentido() === 'asc'
        ? ', de la A a la Z. Ordenar de la Z a la A'
        : ', de la Z a la A. Ordenar de la A a la Z')
    "
    (click)="invertir.emit()"
  >
    {{ etiqueta() }}
    <span class="inline-block w-[1em] text-center" aria-hidden="true">{{
      sentido() === 'asc' ? '▲' : '▼'
    }}</span>
  </button>`,
  host: { '[attr.aria-sort]': "sentido() === 'asc' ? 'ascending' : 'descending'" },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ColumnaOrdenable {
  readonly etiqueta = input.required<string>();
  readonly sentido = input.required<Sentido>();
  readonly invertir = output<void>();
}
