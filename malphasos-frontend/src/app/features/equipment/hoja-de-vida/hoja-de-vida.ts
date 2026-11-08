import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  OnDestroy,
  signal,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';
import { REDUCIR_FOTO } from '../../../shared/foto/reducir-foto';
import { EquipoApi } from '../equipo-api';

/**
 * La hoja de vida de un equipo instalado (RF-22): el PDF que compone y convierte el servidor, a la vista
 * y con su botón para exportarlo (2026-10-18).
 *
 * <p>Hasta ese día esta pantalla pintaba el documento entero —plantilla, hoja de estilos de impresión, QR—
 * y el PDF salía del diálogo de imprimir del navegador. Lo que salía dependía del navegador, y el usuario lo
 * vio distinto de la pantalla. Ahora es como el informe de un reporte: el diseño vive en la plantilla del
 * servidor, y lo que se ve aquí es exactamente lo que se descarga.
 *
 * <p><b>Es un documento, no un formulario</b>: cada dato se corrige donde vive, que es la decisión tomada
 * sobre RF-24.
 *
 * <p><b>Salvo la foto del equipo</b> (V58, 2026-11-08), que se pone aquí porque aquí se ve su recuadro. La
 * pone quien tiene {@code equipment.write} —el ingeniero, en campo, con la cámara del teléfono—, el
 * navegador la reduce a PNG de 400 px antes de subirla, y el PDF se recarga solo con ella.
 */
@Component({
  selector: 'app-hoja-de-vida',
  imports: [RouterLink],
  template: `
    <div class="flex flex-wrap items-center gap-2">
      <h2 class="titulo-seccion">Hoja de vida</h2>
      @if (pdf.data(); as hoja) {
        <a
          class="boton-primario ml-auto flex min-h-tactil items-center"
          data-exportar
          [href]="direccion()"
          [attr.download]="hoja.nombre"
          >Exportar PDF</a
        >
      }
      <a class="font-semibold underline" [routerLink]="['/equipos', id(), 'historial']"
        >Ver los reportes</a
      >
      <a class="font-semibold underline" routerLink="/equipos">Volver a los equipos</a>
    </div>

    @if (puedeCambiarFoto()) {
      <!--
        Se suelta una foto en cualquier parte del bloque, no solo en el recuadro: es un blanco más fácil de
        acertar, y el bloque entero se marca mientras se arrastra encima.
      -->
      <section
        class="superficie mt-3 flex flex-wrap items-center gap-2"
        [class.outline-2]="encima()"
        [class.outline-dashed]="encima()"
        [class.outline-ink]="encima()"
        aria-labelledby="hv-foto"
        data-foto
        (dragover)="arrastrarEncima($event)"
        (dragleave)="encima.set(false)"
        (drop)="soltar($event)"
      >
        @if (foto.data(); as imagen) {
          <img
            class="h-[132px] w-[132px] border border-neutral-300 object-cover"
            [src]="imagen"
            alt="Foto del equipo"
          />
        } @else {
          <div
            class="flex h-[132px] w-[132px] items-center justify-center border border-neutral-300 text-sm text-neutral-700"
            data-sin-foto
          >
            Sin foto
          </div>
        }
        <div>
          <h3 id="hv-foto" class="font-bold">Foto del equipo</h3>
          <p class="text-sm text-neutral-700">
            Elíjala, arrástrela hasta aquí o péguela con Ctrl+V. Se reduce y se guarda en PNG antes
            de subirla.
          </p>
          <div class="mt-1 flex flex-wrap items-center gap-1.5">
            <label
              class="boton-secundario flex min-h-tactil cursor-pointer items-center"
              for="hv-foto-archivo"
            >
              {{ foto.data() ? 'Cambiar foto' : 'Subir foto' }}
            </label>
            <input
              id="hv-foto-archivo"
              class="sr-only"
              type="file"
              accept="image/*"
              capture="environment"
              [disabled]="ocupado()"
              (change)="elegirFoto($event)"
            />
            @if (foto.data()) {
              <button
                class="min-h-tactil px-1 underline"
                type="button"
                [disabled]="ocupado()"
                (click)="quitarFoto()"
              >
                Quitar
              </button>
            }
          </div>
          @if (ocupado()) {
            <p class="mt-1 text-neutral-700" role="status">
              {{ reduciendo() ? 'Reduciendo la foto…' : 'Guardando…' }}
            </p>
          }
          @if (errorDeFoto(); as mensaje) {
            <p class="mt-1 border-l-2 border-accent-700 bg-accent-100 p-1.5" role="alert">
              {{ mensaje }}
            </p>
          }
        </div>
      </section>
    }

    @if (pdf.isPending()) {
      <p class="mt-3 text-neutral-700" role="status">Preparando el PDF…</p>
    } @else if (pdf.isError()) {
      <div class="mt-3 border-l-2 border-accent-700 bg-accent-100 p-2" role="alert">
        <p class="font-semibold">{{ mensajeDeError() }}</p>
      </div>
    } @else if (visor(); as visor) {
      <iframe
        class="mt-3 block h-[80vh] w-full border border-neutral-300 bg-white"
        title="Hoja de vida del equipo en PDF"
        data-visor
        [src]="visor"
      ></iframe>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Pegar vale en toda la pantalla: no hay otro sitio donde pegar en ella.
  host: { '(window:paste)': 'pegar($event)' },
})
export class HojaDeVida implements OnDestroy {
  readonly id = input.required<string>();

  private readonly sanitizer = inject(DomSanitizer);
  private readonly api = inject(EquipoApi);
  private readonly reducir = inject(REDUCIR_FOTO);

  private readonly sesion = inject(Sesion);
  protected readonly puedeCambiarFoto = computed(() => this.sesion.puede('equipment.write'));
  protected readonly foto = this.api.fotoDe(this.id, this.puedeCambiarFoto);
  private readonly puesta = this.api.ponerFoto();
  private readonly quitada = this.api.quitarFoto();
  protected readonly reduciendo = signal(false);
  /** Lo que se dice cuando lo elegido no se puede usar: no es una imagen, o no se pudo leer. */
  private readonly aviso = signal<string | null>(null);
  /** Si se está arrastrando algo encima del bloque. */
  protected readonly encima = signal(false);
  protected readonly ocupado = computed(
    () => this.reduciendo() || this.puesta.isPending() || this.quitada.isPending(),
  );
  protected readonly errorDeFoto = computed(() => {
    const aviso = this.aviso();
    if (aviso) {
      return aviso;
    }
    const fallo = this.puesta.error() ?? this.quitada.error();
    return fallo ? traducirError(fallo) : null;
  });

  protected readonly pdf = inject(EquipoApi).hojaDeVidaEnPdf(this.id);
  protected readonly mensajeDeError = computed(() => traducirError(this.pdf.error()));

  /** La dirección local del PDF descargado: la usan el visor y el botón de exportar. */
  protected readonly direccion = computed(() => {
    const hoja = this.pdf.data();

    return hoja ? URL.createObjectURL(hoja.pdf) : null;
  });
  protected readonly visor = computed<SafeResourceUrl | null>(() => {
    const direccion = this.direccion();

    // Una dirección blob: creada aquí mismo, no un dato del servidor: es lo único que se marca seguro.
    return direccion ? this.sanitizer.bypassSecurityTrustResourceUrl(direccion) : null;
  });

  private anterior: string | null = null;

  constructor() {
    // Cada PDF nuevo —se cerró un reporte y la hoja se recargó— deja libre la memoria del anterior.
    effect(() => {
      const direccion = this.direccion();
      this.liberar();
      this.anterior = direccion;
    });
  }

  protected elegirFoto(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const archivo = campo.files?.[0];
    // vacío, para que elegir otra vez la misma foto dispare de nuevo
    campo.value = '';
    if (archivo) {
      void this.subir(archivo);
    }
  }

  /** Sin esto el navegador abre el archivo soltado en lugar de dejarlo aquí. */
  protected arrastrarEncima(evento: DragEvent): void {
    evento.preventDefault();
    this.encima.set(true);
  }

  protected soltar(evento: DragEvent): void {
    evento.preventDefault();
    this.encima.set(false);
    const archivo = evento.dataTransfer?.files?.[0];
    if (archivo && !this.ocupado()) {
      void this.subir(archivo);
    }
  }

  /**
   * Ctrl+V con una imagen en el portapapeles —una captura, una foto copiada— la pone como foto. Un pegado
   * sin imagen no se toca: sigue su camino.
   */
  protected pegar(evento: ClipboardEvent): void {
    if (!this.puedeCambiarFoto() || this.ocupado()) {
      return;
    }
    const imagen = [...(evento.clipboardData?.files ?? [])].find((f) =>
      f.type.startsWith('image/'),
    );
    if (imagen) {
      evento.preventDefault();
      void this.subir(imagen);
    }
  }

  private async subir(archivo: File): Promise<void> {
    this.puesta.reset();
    this.quitada.reset();
    if (!archivo.type.startsWith('image/')) {
      this.aviso.set('Eso no es una imagen. Elija una foto.');
      return;
    }
    this.aviso.set(null);
    this.reduciendo.set(true);
    let png: Blob;
    try {
      png = await this.reducir(archivo);
    } catch {
      this.aviso.set('No se pudo leer la foto. Elija una imagen.');
      return;
    } finally {
      this.reduciendo.set(false);
    }
    this.puesta.mutate({ id: this.id(), png });
  }

  protected quitarFoto(): void {
    this.aviso.set(null);
    this.puesta.reset();
    this.quitada.mutate(this.id());
  }

  ngOnDestroy(): void {
    this.liberar();
  }

  private liberar(): void {
    if (this.anterior) {
      URL.revokeObjectURL(this.anterior);
      this.anterior = null;
    }
  }
}
