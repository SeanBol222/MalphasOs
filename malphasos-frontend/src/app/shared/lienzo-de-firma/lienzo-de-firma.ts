import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

/** El grosor del trazo, en píxeles del lienzo: constante, como pide RF-18. */
export const GROSOR_DEL_TRAZO = 2.5;

/**
 * Un área donde se firma con el dedo, un lápiz o el ratón (2026-10-17). Es el componente de RF-18: el
 * trazo se ve mientras se dibuja, con grosor constante, y «Limpiar» borra el intento para empezar otro.
 *
 * <p>Emite la firma como PNG en data: URI al terminar cada trazo, y nulo al limpiar: quien lo usa sabe así
 * en todo momento si hay firma o no, que es lo que necesita para no aceptar una vacía. Lo usan «Mi firma»,
 * la del ingeniero, y el reporte, la de quien recibe.
 *
 * <p>Con eventos de puntero, que unifican dedo, lápiz y ratón, y {@code touch-action: none} para que
 * dibujar en un teléfono no desplace la página.
 */
@Component({
  selector: 'app-lienzo-de-firma',
  template: `
    <div class="border border-neutral-400 bg-white">
      <canvas
        #lienzo
        class="block h-[160px] w-full cursor-crosshair touch-none"
        width="600"
        height="200"
        role="img"
        [attr.aria-label]="etiqueta()"
        (pointerdown)="empezar($event)"
        (pointermove)="seguir($event)"
        (pointerup)="terminar($event)"
        (pointercancel)="terminar($event)"
        (pointerleave)="terminar($event)"
      ></canvas>
    </div>
    <div class="mt-1 flex items-center gap-1.5">
      <button class="min-h-tactil px-1 underline" type="button" (click)="limpiar()">Limpiar</button>
      <span class="text-sm text-neutral-700" aria-live="polite">
        {{ vacio() ? 'Sin firma todavía' : 'Firma dibujada' }}
      </span>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LienzoDeFirma {
  readonly etiqueta = input('Área de firma');

  /** La firma como PNG en data: URI tras cada trazo; nulo al limpiar. */
  readonly cambio = output<string | null>();

  protected readonly vacio = signal(true);

  private readonly lienzo = viewChild.required<ElementRef<HTMLCanvasElement>>('lienzo');
  private dibujando = false;

  protected empezar(evento: PointerEvent): void {
    const contexto = this.contexto();
    this.dibujando = true;
    (evento.target as Element).setPointerCapture?.(evento.pointerId);
    if (contexto) {
      const { x, y } = this.punto(evento);
      contexto.beginPath();
      contexto.moveTo(x, y);
      // Un toque sin arrastrar también deja marca: un punto.
      contexto.lineTo(x + 0.01, y);
      contexto.stroke();
    }
    this.vacio.set(false);
  }

  protected seguir(evento: PointerEvent): void {
    if (!this.dibujando) {
      return;
    }
    const contexto = this.contexto();
    if (contexto) {
      const { x, y } = this.punto(evento);
      contexto.lineTo(x, y);
      contexto.stroke();
    }
  }

  protected terminar(evento: PointerEvent): void {
    if (!this.dibujando) {
      return;
    }
    this.dibujando = false;
    (evento.target as Element).releasePointerCapture?.(evento.pointerId);
    this.cambio.emit(this.lienzo().nativeElement.toDataURL('image/png'));
  }

  limpiar(): void {
    const lienzo = this.lienzo().nativeElement;
    this.contexto()?.clearRect(0, 0, lienzo.width, lienzo.height);
    this.vacio.set(true);
    this.cambio.emit(null);
  }

  private contexto(): CanvasRenderingContext2D | null {
    const contexto = this.lienzo().nativeElement.getContext('2d');
    if (contexto) {
      contexto.lineWidth = GROSOR_DEL_TRAZO;
      contexto.lineCap = 'round';
      contexto.lineJoin = 'round';
      contexto.strokeStyle = '#111111';
    }
    return contexto;
  }

  /** Del píxel de pantalla al del lienzo: se pinta a 600×200 aunque se vea más estrecho. */
  private punto(evento: PointerEvent): { x: number; y: number } {
    const lienzo = this.lienzo().nativeElement;
    const caja = lienzo.getBoundingClientRect();

    return {
      x: ((evento.clientX - caja.left) * lienzo.width) / (caja.width || lienzo.width),
      y: ((evento.clientY - caja.top) * lienzo.height) / (caja.height || lienzo.height),
    };
  }
}
