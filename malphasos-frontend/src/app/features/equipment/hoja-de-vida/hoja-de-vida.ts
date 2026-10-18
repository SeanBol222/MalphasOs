import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  OnDestroy,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { traducirError } from '../../../core/errores/traducir';
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
})
export class HojaDeVida implements OnDestroy {
  readonly id = input.required<string>();

  private readonly sanitizer = inject(DomSanitizer);

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
