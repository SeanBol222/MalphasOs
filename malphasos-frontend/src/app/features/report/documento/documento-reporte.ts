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
import { ReporteApi } from '../reporte-api';
import { traducirError } from '../../../core/errores/traducir';

/**
 * El informe de un reporte (RF-17): el PDF que compone y convierte el servidor, a la vista y con su botón
 * para exportarlo (2026-10-17).
 *
 * <p>Hasta ese día esta pantalla pintaba el documento entero —plantilla, hoja de estilos, QR y gráfica— y
 * el PDF salía del navegador al imprimir. Con el PDF en el servidor el diseño vive en un solo sitio, la
 * plantilla del backend; dos copias se habrían desviado, como ya pasó con las reglas de cierre antes de
 * reflejarlas. Aquí quedan los avisos que no son parte del documento entregado.
 */
@Component({
  selector: 'app-documento-reporte',
  imports: [RouterLink],
  template: `
    <div class="flex flex-wrap items-center gap-2">
      <h2 class="titulo-seccion">Informe de mantenimiento</h2>
      @if (pdf.data(); as informe) {
        <a
          class="boton-primario ml-auto flex min-h-tactil items-center"
          data-exportar
          [href]="direccion()"
          [attr.download]="informe.nombre"
          >Exportar PDF</a
        >
      }
      <a class="font-semibold underline" [routerLink]="['/reportes', id()]">Volver al reporte</a>
    </div>

    @if (esBorrador()) {
      <p class="mt-2 border-l-2 border-accent-700 bg-accent-100 p-2 font-semibold" role="status">
        Este reporte está en borrador: el informe todavía puede cambiar, y no es el que se entrega.
      </p>
    }
    @if (tardio()) {
      <!--
        Decidido por el usuario el 2026-10-17: un reporte cerrado antes de que el informe se congelara se
        congeló después, con los datos de ese día, y se dice. Fuera del PDF: es un aviso para quien lo
        mira, no una parte del documento entregado.
      -->
      <p
        class="mt-2 border-l-2 border-accent-700 bg-accent-100 p-2 font-semibold"
        role="status"
        data-tardio
      >
        Este informe se congeló después de cerrarse el reporte: los datos del equipo, del cliente y
        de la empresa son los del día en que se congeló, no necesariamente los del día del servicio.
      </p>
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
        title="Informe de mantenimiento en PDF"
        data-visor
        [src]="visor"
      ></iframe>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentoReporte implements OnDestroy {
  readonly id = input.required<string>();

  private readonly api = inject(ReporteApi);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly pdf = this.api.pdf(this.id);
  private readonly reporte = this.api.detalle(this.id);
  private readonly informe = this.api.informe(this.id);

  protected readonly esBorrador = computed(
    () => !!this.reporte.data() && this.reporte.data()?.estado !== 'FINALIZADO',
  );
  protected readonly tardio = computed(() => !!this.informe.data()?.tardio);
  protected readonly mensajeDeError = computed(() => traducirError(this.pdf.error()));

  /** La dirección local del PDF descargado: la usan el visor y el botón de exportar. */
  protected readonly direccion = computed(() => {
    const informe = this.pdf.data();

    return informe ? URL.createObjectURL(informe.pdf) : null;
  });
  protected readonly visor = computed<SafeResourceUrl | null>(() => {
    const direccion = this.direccion();

    // Una dirección blob: creada aquí mismo, no un dato del servidor: es lo único que se marca seguro.
    return direccion ? this.sanitizer.bypassSecurityTrustResourceUrl(direccion) : null;
  });

  private anterior: string | null = null;

  constructor() {
    // Cada PDF nuevo —el reporte cambió y se recargó— deja libre la memoria del anterior.
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
