import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { ImageCropperComponent, ImageTransform } from 'ngx-image-cropper';
import { LADO_MAYOR } from './reducir-foto';

/** Cuánto se puede acercar la foto, y de cuánto en cuánto con los botones. */
export const ESCALA_MINIMA = 1;
export const ESCALA_MAXIMA = 4;
export const PASO_DE_ESCALA = 0.25;

/** La escala, dentro de lo que se admite. */
export function escalaEntre(escala: number): number {
  return Math.min(ESCALA_MAXIMA, Math.max(ESCALA_MINIMA, Math.round(escala * 100) / 100));
}

/**
 * Encuadrar la foto de un equipo antes de subirla (2026-11-08), pedido por el usuario: una foto alargada,
 * o con el equipo a un lado o lejos, se mueve y se acerca dentro de un recuadro cuadrado, que es la forma
 * del de la hoja de vida.
 *
 * <p>El recorte lo hace {@code ngx-image-cropper}: la foto se arrastra para encuadrarla —en vertical o en
 * horizontal— y la barra o los botones la acercan y la alejan. «Guardar» devuelve el recuadro en PNG de
 * {@link LADO_MAYOR} px de lado, sin agrandar una foto que ya sea más pequeña. La librería lee también la
 * orientación de la cámara del teléfono.
 */
@Component({
  selector: 'app-encuadre-de-foto',
  imports: [CdkTrapFocus, ImageCropperComponent],
  template: `
    <div class="fixed inset-0 z-30 bg-ink/45" aria-hidden="true" (click)="cancelado.emit()"></div>
    <section
      class="fixed inset-0 z-40 m-auto flex h-fit max-h-[100dvh] w-full max-w-[560px] flex-col gap-2 overflow-y-auto bg-paper p-2 text-ink md:border md:border-ink md:p-3"
      role="dialog"
      aria-modal="true"
      aria-labelledby="encuadre-titulo"
      cdkTrapFocus
      [cdkTrapFocusAutoCapture]="true"
    >
      <h2 id="encuadre-titulo" class="titulo-seccion">Encuadrar la foto</h2>
      <p class="text-sm text-neutral-700">
        Arrastre la foto para dejar el equipo dentro del recuadro, y acérquela o aléjela con la
        barra.
      </p>

      <image-cropper
        class="max-h-[60dvh]"
        [imageFile]="archivo()"
        [maintainAspectRatio]="true"
        [aspectRatio]="1"
        [allowMoveImage]="true"
        [hideResizeSquares]="true"
        [autoCrop]="false"
        [transform]="transformacion()"
        (transformChange)="transformacion.set($event)"
        [resizeToWidth]="lado"
        [onlyScaleDown]="true"
        format="png"
        output="blob"
        cropperFrameAriaLabel="El recuadro de la foto"
        imageAltText="La foto que se está encuadrando"
        (cropperReady)="lista.set(true)"
        (loadImageFailed)="fallo.emit()"
      />

      <div class="flex items-center gap-1.5">
        <button
          class="boton-secundario min-h-tactil min-w-tactil"
          type="button"
          aria-label="Alejar"
          [disabled]="escala() <= minima"
          (click)="escalar(escala() - paso)"
        >
          −
        </button>
        <label class="sr-only" for="encuadre-escala">Acercar o alejar</label>
        <input
          id="encuadre-escala"
          class="flex-1"
          type="range"
          [min]="minima"
          [max]="maxima"
          step="0.05"
          [value]="escala()"
          (input)="escalar(+$any($event.target).value)"
        />
        <button
          class="boton-secundario min-h-tactil min-w-tactil"
          type="button"
          aria-label="Acercar"
          [disabled]="escala() >= maxima"
          (click)="escalar(escala() + paso)"
        >
          +
        </button>
      </div>

      <div class="flex flex-wrap justify-end gap-1.5">
        <button class="boton-secundario min-h-tactil" type="button" (click)="cancelado.emit()">
          Cancelar
        </button>
        <button
          class="boton-primario min-h-tactil"
          type="button"
          [disabled]="!lista() || guardando()"
          (click)="guardar()"
        >
          {{ guardando() ? 'Recortando…' : 'Guardar' }}
        </button>
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(keydown.escape)': 'cancelado.emit()' },
})
export class EncuadreDeFoto {
  readonly archivo = input.required<File>();

  /** El recuadro encuadrado, en PNG. */
  readonly listo = output<Blob>();
  readonly cancelado = output<void>();
  /** La librería no pudo leer la imagen. */
  readonly fallo = output<void>();

  protected readonly lado = LADO_MAYOR;
  protected readonly minima = ESCALA_MINIMA;
  protected readonly maxima = ESCALA_MAXIMA;
  protected readonly paso = PASO_DE_ESCALA;

  protected readonly transformacion = signal<ImageTransform>({ scale: 1 });
  protected readonly lista = signal(false);
  protected readonly guardando = signal(false);

  private readonly recortador = viewChild(ImageCropperComponent);

  protected escala(): number {
    return this.transformacion().scale ?? 1;
  }

  protected escalar(escala: number): void {
    this.transformacion.update((t) => ({ ...t, scale: escalaEntre(escala) }));
  }

  protected async guardar(): Promise<void> {
    const recorte = this.recortador()?.crop('blob');
    if (!recorte) {
      return;
    }
    this.guardando.set(true);
    try {
      const resultado = await recorte;
      if (resultado.blob) {
        this.listo.emit(resultado.blob);
      } else {
        this.fallo.emit();
      }
    } catch {
      this.fallo.emit();
    } finally {
      this.guardando.set(false);
    }
  }
}
