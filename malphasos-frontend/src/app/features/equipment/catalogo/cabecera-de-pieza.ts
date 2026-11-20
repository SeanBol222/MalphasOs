import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  inject,
  Injector,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * La fila de arriba de cada pieza del catalogo (2026-11-20, sobre la maqueta que aprobo el usuario): el
 * titulo y una descripcion corta a la izquierda, y a la derecha, al mismo nivel, una lupa y el boton de
 * alta.
 *
 * <p><b>La busqueda se despliega</b>: la lupa abre el cuadro de texto a su izquierda, en la misma fila,
 * y cerrarla lo borra y la lista vuelve a verse entera —decidido por el usuario—. Lo escrito sale por
 * {@link busqueda}; filtrar es cosa de cada pieza, porque cada una busca en datos distintos.
 *
 * <p>El boton de alta navega si se le da {@link enlace} —el tipo tiene pagina propia— y si no avisa por
 * {@link nuevo}, para que la pieza abra su formulario encima de la lista. Sin {@link boton} no se pinta:
 * quien solo lee el catalogo no da de alta.
 */
@Component({
  selector: 'app-cabecera-de-pieza',
  imports: [RouterLink],
  templateUrl: './cabecera-de-pieza.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CabeceraDePieza {
  private readonly injector = inject(Injector);

  readonly titulo = input.required<string>();
  readonly descripcion = input.required<string>();
  /** El identificador del cuadro de texto: cada pieza el suyo, para su etiqueta y sus pruebas. */
  readonly campo = input.required<string>();
  /** En que datos se busca, dicho dentro del cuadro. */
  readonly pista = input('');
  /** La etiqueta del boton de alta, o nulo si quien mira no puede dar de alta. */
  readonly boton = input<string | null>(null);
  /** La ruta a la que lleva el boton, si el alta tiene pagina propia. */
  readonly enlace = input<string | null>(null);

  readonly busqueda = output<string>();
  readonly nuevo = output<void>();

  protected readonly abierto = signal(false);
  private readonly cuadro = viewChild<ElementRef<HTMLInputElement>>('cuadro');

  protected alternar(): void {
    if (this.abierto()) {
      this.abierto.set(false);
      this.busqueda.emit('');

      return;
    }

    this.abierto.set(true);
    // Quien abre la lupa va a escribir: el cuadro recibe el foco en cuanto existe.
    afterNextRender(() => this.cuadro()?.nativeElement.focus(), { injector: this.injector });
  }

  protected escribir(evento: Event): void {
    this.busqueda.emit((evento.target as HTMLInputElement).value);
  }
}
