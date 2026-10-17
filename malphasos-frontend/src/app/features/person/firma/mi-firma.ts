import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { traducirError } from '../../../core/errores/traducir';
import { fechaCorta } from '../../../core/fechas';
import { LienzoDeFirma } from '../../../shared/lienzo-de-firma/lienzo-de-firma';
import { FirmaApi } from '../firma-api';

/**
 * La firma de quien tiene la sesión (V38, 2026-10-17). Decidido por el usuario: el ingeniero no firma cada
 * servicio; la dibuja aquí una vez y cada informe que cierra la copia. Cambiarla no altera los informes
 * ya cerrados, que copiaron la que había ese día.
 *
 * <p>No se acepta vacía, como pide RF-18: guardar sin trazo lo dice en vez de mandar nada.
 */
@Component({
  selector: 'app-mi-firma',
  imports: [LienzoDeFirma],
  template: `
    <section class="max-w-[720px]">
      <h2 class="titulo-seccion">Mi firma</h2>
      <p class="text-neutral-700">
        Los informes que cierre la llevan en la línea del profesional de servicio. Cambiarla no
        altera los informes ya cerrados.
      </p>

      @if (hayError()) {
        <div class="mt-1 border-l-2 border-accent-700 bg-accent-100 p-2" role="alert">
          <p class="font-semibold">{{ mensajeDeError() }}</p>
        </div>
      }

      <section class="superficie mt-3" aria-labelledby="firma-actual">
        <h3 id="firma-actual" class="titulo-seccion">La que tiene ahora</h3>
        @if (firma.isPending()) {
          <p class="mt-1 text-neutral-700" role="status">Cargando…</p>
        } @else if (firma.data(); as actual) {
          <img
            class="mt-1 max-h-[120px] border border-neutral-300 bg-white"
            data-firma-actual
            [src]="actual.imagen"
            alt="Su firma guardada"
          />
          <p class="mt-1 text-sm text-neutral-700">
            Guardada el {{ fechaCorta(actual.actualizada) }}.
          </p>
        } @else {
          <p class="mt-1 text-neutral-700" data-sin-firma>
            Todavía no tiene firma: los informes que cierre dejan la línea para firmar a mano.
          </p>
        }
      </section>

      <section class="superficie mt-3" aria-labelledby="firma-nueva">
        <h3 id="firma-nueva" class="titulo-seccion">
          {{ firma.data() ? 'Cambiarla' : 'Dibujarla' }}
        </h3>
        <div class="mt-1">
          <app-lienzo-de-firma etiqueta="Área para dibujar su firma" (cambio)="alDibujar($event)" />
        </div>
        @if (faltaTrazo()) {
          <p class="mt-1 font-semibold text-accent-700">Dibuje su firma antes de guardarla.</p>
        }
        @if (guardadaConExito()) {
          <p class="mt-1 font-semibold" role="status">Firma guardada.</p>
        }
        <div class="mt-2">
          <button
            class="boton-primario min-h-tactil"
            type="button"
            [disabled]="guardado.isPending()"
            (click)="guardar()"
          >
            {{ guardado.isPending() ? 'Guardando…' : 'Guardar firma' }}
          </button>
        </div>
      </section>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MiFirma {
  private readonly api = inject(FirmaApi);

  protected readonly firma = this.api.consultar();
  protected readonly guardado = this.api.guardar();
  protected readonly fechaCorta = (fecha: string | undefined) => fechaCorta(fecha?.slice(0, 10));

  protected readonly trazo = signal<string | null>(null);
  protected readonly faltaTrazo = signal(false);
  protected readonly guardadaConExito = signal(false);

  private readonly lienzo = viewChild.required(LienzoDeFirma);

  protected readonly hayError = computed(() => this.firma.isError() || this.guardado.isError());
  protected readonly mensajeDeError = computed(() => {
    const fallo = this.firma.error() ?? this.guardado.error();

    // Una cuenta creada a mano en Keycloak —el SuperUsuario— no tiene persona: no tiene dónde guardar
    // una firma, y «esa persona no existe» no le diría qué hacer.
    return fallo instanceof HttpErrorResponse && fallo.error?.code === 'ERR_PERSON_001'
      ? 'Su cuenta no corresponde a ninguna persona registrada. Para guardar una firma, pida que le den de alta como ingeniero o administrador.'
      : traducirError(fallo);
  });

  protected alDibujar(imagen: string | null): void {
    this.trazo.set(imagen);
    if (imagen) {
      this.faltaTrazo.set(false);
      this.guardadaConExito.set(false);
    }
  }

  protected guardar(): void {
    const imagen = this.trazo();
    this.guardadaConExito.set(false);
    if (!imagen) {
      this.faltaTrazo.set(true);

      return;
    }

    this.faltaTrazo.set(false);
    this.guardado.mutate(imagen, {
      onSuccess: () => {
        this.lienzo().limpiar();
        this.guardadaConExito.set(true);
      },
    });
  }
}
