import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { NuevaIntervencionExterna } from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';
import { fechaCorta, fechaIso, HOY } from '../../../core/fechas';
import { EquipoApi } from '../equipo-api';

const TIPOS = [
  { valor: 'PREVENTIVO', etiqueta: 'Preventivo' },
  { valor: 'CORRECTIVO', etiqueta: 'Correctivo' },
  { valor: 'CALIBRACION', etiqueta: 'Calibración' },
  { valor: 'OTRO', etiqueta: 'Otro' },
] as const;

const RESULTADOS = [
  { valor: '', etiqueta: 'No se sabe' },
  { valor: 'OPERATIVO', etiqueta: 'Operativo' },
  { valor: 'OPERATIVO_CON_RESTRICCIONES', etiqueta: 'Con restricciones' },
  { valor: 'FUERA_DE_SERVICIO', etiqueta: 'Fuera de servicio' },
] as const;

const ETIQUETA_DE_TIPO: Record<string, string> = Object.fromEntries(
  TIPOS.map((t) => [t.valor, t.etiqueta]),
);
const ETIQUETA_DE_RESULTADO: Record<string, string> = Object.fromEntries(
  RESULTADOS.map((r) => [r.valor, r.etiqueta]),
);

/**
 * Lo que otras empresas le hicieron al equipo (V61, 2026-11-23): un mantenimiento, una calibración u otra
 * cosa que no hizo esta empresa. Entra en el historial de la hoja de vida por su fecha, y cuenta para el
 * último servicio.
 *
 * <p>Decidido por el usuario: <b>rápido de meter</b> —cuándo, qué tipo, qué se hizo y qué empresa—, con el
 * resultado y el certificado opcionales, sin adjunto. Lo anotan ingenieros, administradores y el supervisor
 * del cliente, que a veces es quien contrata la calibración; el servidor limita al supervisor a sus equipos.
 * Una mal anotada se retira y se anota otra: no se edita.
 */
@Component({
  selector: 'app-historial-externo',
  imports: [ReactiveFormsModule],
  template: `
    <section class="superficie mt-3" aria-labelledby="hv-externas" data-historial-externo>
      <div class="flex flex-wrap items-center gap-2">
        <div class="min-w-0 flex-[1_1_18rem]">
          <h3 id="hv-externas" class="font-bold">Historia adicional</h3>
          <p class="text-sm text-neutral-700">
            Mantenimientos, calibraciones y demás que no hizo esta empresa. Entran en el historial
            de la hoja de vida por su fecha.
          </p>
        </div>
        @if (!abierto()) {
          <button
            class="boton-primario ml-auto min-h-tactil"
            type="button"
            data-anadir
            (click)="abrir()"
          >
            Añadir al historial
          </button>
        }
      </div>

      @if (abierto()) {
        <form class="mt-2 max-w-[720px]" [formGroup]="formulario" (ngSubmit)="guardar()">
          <p class="text-sm text-neutral-700">Los campos con * son obligatorios.</p>

          <label class="mt-1.5 block font-semibold" for="externa-fecha"
            >Fecha en que se hizo *</label
          >
          <input
            id="externa-fecha"
            class="campo mt-0.5 min-h-tactil"
            type="date"
            aria-required="true"
            [max]="hoy()"
            formControlName="realizada"
          />

          <fieldset class="mt-2">
            <legend class="font-semibold">Tipo de servicio *</legend>
            <div class="mt-0.5 flex flex-wrap gap-1">
              @for (tipo of TIPOS; track tipo.valor) {
                <label [class]="recuadro">
                  <input
                    class="sr-only"
                    type="radio"
                    formControlName="tipoServicio"
                    [value]="tipo.valor"
                    [attr.data-tipo]="tipo.valor"
                  />
                  {{ tipo.etiqueta }}
                </label>
              }
            </div>
          </fieldset>

          <label class="mt-2 block font-semibold" for="externa-descripcion">Qué se hizo *</label>
          <textarea
            id="externa-descripcion"
            class="campo mt-0.5 w-full"
            rows="3"
            maxlength="500"
            aria-required="true"
            formControlName="descripcion"
          ></textarea>

          <label class="mt-2 block font-semibold" for="externa-empresa"
            >Empresa que lo hizo *</label
          >
          <input
            id="externa-empresa"
            class="campo mt-0.5 min-h-tactil w-full"
            maxlength="120"
            aria-required="true"
            formControlName="empresa"
          />

          <fieldset class="mt-2">
            <legend class="font-semibold">Cómo quedó el equipo</legend>
            <div class="mt-0.5 flex flex-wrap gap-1">
              @for (resultado of RESULTADOS; track resultado.valor) {
                <label [class]="recuadro">
                  <input
                    class="sr-only"
                    type="radio"
                    formControlName="resultado"
                    [value]="resultado.valor"
                  />
                  {{ resultado.etiqueta }}
                </label>
              }
            </div>
          </fieldset>

          <label class="mt-2 block font-semibold" for="externa-documento"
            >N.º del certificado o informe</label
          >
          <input
            id="externa-documento"
            class="campo mt-0.5 min-h-tactil w-full max-w-[20rem]"
            maxlength="80"
            formControlName="documento"
          />

          @if (intentado() && falta()) {
            <p class="mt-2 font-semibold text-accent-700" role="alert">{{ falta() }}</p>
          }
          @if (anotar.error(); as fallo) {
            <p class="mt-2 border-l-2 border-accent-700 bg-accent-100 p-1.5" role="alert">
              {{ mensaje(fallo) }}
            </p>
          }

          <div class="mt-2 flex flex-wrap gap-1.5">
            <button
              class="boton-primario min-h-tactil"
              type="submit"
              [disabled]="anotar.isPending()"
            >
              {{ anotar.isPending() ? 'Guardando…' : 'Guardar' }}
            </button>
            <button class="boton-secundario min-h-tactil" type="button" (click)="cerrar()">
              Cancelar
            </button>
          </div>
        </form>
      }

      @if (externas.data(); as lista) {
        @if (lista.length) {
          <ul class="mt-2 border-t border-neutral-300">
            @for (externa of lista; track externa.id) {
              <li
                class="flex flex-wrap items-start gap-2 border-b border-neutral-200 py-1.5"
                [attr.data-externa]="externa.id"
              >
                <div class="min-w-0 flex-[1_1_18rem]">
                  <p class="font-bold">
                    {{ fecha(externa.realizada) }} · {{ tipo(externa.tipoServicio) }} ·
                    {{ externa.empresa }}
                  </p>
                  <p class="text-sm">{{ externa.descripcion }}</p>
                  <p class="text-sm text-neutral-700">
                    {{ resultado(externa.resultado) }}
                    @if (externa.documento) {
                      · {{ externa.documento }}
                    }
                  </p>
                </div>
                <div class="ml-auto flex flex-wrap justify-end gap-1">
                  @if (porRetirar() === externa.id) {
                    <button
                      class="boton-primario min-h-tactil text-sm"
                      type="button"
                      [disabled]="retirar.isPending()"
                      (click)="confirmarRetiro(externa.id!)"
                    >
                      Retirar
                    </button>
                    <button
                      class="boton-secundario min-h-tactil text-sm"
                      type="button"
                      (click)="porRetirar.set(null)"
                    >
                      Cancelar
                    </button>
                  } @else {
                    <button
                      class="min-h-tactil px-1 text-sm underline"
                      type="button"
                      [attr.aria-label]="'Retirar la de ' + fecha(externa.realizada)"
                      (click)="porRetirar.set(externa.id!)"
                    >
                      Retirar
                    </button>
                  }
                </div>
              </li>
            }
          </ul>
        } @else if (!abierto()) {
          <p class="mt-1.5 text-sm text-neutral-700" data-sin-externas>
            No hay nada anotado de otras empresas.
          </p>
        }
      }
      @if (retirar.error(); as fallo) {
        <p class="mt-1.5 border-l-2 border-accent-700 bg-accent-100 p-1.5" role="alert">
          {{ mensaje(fallo) }}
        </p>
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistorialExterno {
  readonly id = input.required<string>();

  protected readonly TIPOS = TIPOS;
  protected readonly RESULTADOS = RESULTADOS;
  /** Un recuadro entero que se pone en tinta al elegirlo, sin el circulito: como en el reporte. */
  protected readonly recuadro =
    'flex min-h-tactil cursor-pointer items-center border border-neutral-700 px-1.5 text-sm font-semibold has-[:checked]:border-2 has-[:checked]:border-ink has-[:checked]:bg-ink has-[:checked]:text-white has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-accent';

  private readonly api = inject(EquipoApi);
  private readonly ahora = inject(HOY);
  private readonly fb = inject(FormBuilder);

  protected readonly externas = this.api.intervencionesExternas(this.id, signal(true));
  protected readonly anotar = this.api.anotarIntervencionExterna();
  protected readonly retirar = this.api.retirarIntervencionExterna();

  protected readonly abierto = signal(false);
  protected readonly intentado = signal(false);
  protected readonly porRetirar = signal<string | null>(null);
  protected readonly hoy = computed(() => fechaIso(this.ahora()));

  protected readonly formulario = this.fb.nonNullable.group({
    realizada: ['', Validators.required],
    tipoServicio: ['', Validators.required],
    descripcion: ['', [Validators.required, Validators.maxLength(500)]],
    empresa: ['', [Validators.required, Validators.maxLength(120)]],
    resultado: [''],
    documento: ['', Validators.maxLength(80)],
  });
  private readonly valores = toSignal(this.formulario.valueChanges, {
    initialValue: this.formulario.getRawValue(),
  });

  /** Lo primero que falta, dicho como se pide. */
  protected readonly falta = computed(() => {
    const v = this.valores();
    if (!v.realizada) {
      return 'Falta la fecha en que se hizo.';
    }
    if (v.realizada > this.hoy()) {
      return 'La fecha no puede ser futura.';
    }
    if (!v.tipoServicio) {
      return 'Elija el tipo de servicio.';
    }
    if (!v.descripcion?.trim()) {
      return 'Falta qué se hizo.';
    }
    if (!v.empresa?.trim()) {
      return 'Falta la empresa que lo hizo.';
    }
    return '';
  });

  protected abrir(): void {
    this.formulario.reset({
      realizada: this.hoy(),
      tipoServicio: '',
      descripcion: '',
      empresa: '',
      resultado: '',
      documento: '',
    });
    this.intentado.set(false);
    this.anotar.reset();
    this.abierto.set(true);
  }

  protected cerrar(): void {
    this.abierto.set(false);
  }

  protected guardar(): void {
    this.intentado.set(true);
    if (this.formulario.invalid || this.falta()) {
      return;
    }
    const v = this.formulario.getRawValue();
    const intervencion: NuevaIntervencionExterna = {
      realizada: v.realizada,
      tipoServicio: v.tipoServicio,
      descripcion: v.descripcion.trim(),
      empresa: v.empresa.trim(),
      resultado: v.resultado || undefined,
      documento: v.documento.trim() || undefined,
    };
    this.anotar.mutate({ id: this.id(), intervencion }, { onSuccess: () => this.cerrar() });
  }

  protected confirmarRetiro(idIntervencion: string): void {
    this.retirar.mutate(
      { id: this.id(), idIntervencion },
      { onSuccess: () => this.porRetirar.set(null) },
    );
  }

  protected fecha(iso: string | undefined): string {
    return fechaCorta(iso);
  }

  protected tipo(valor: string | undefined): string {
    return ETIQUETA_DE_TIPO[valor ?? ''] ?? valor ?? '';
  }

  protected resultado(valor: string | null | undefined): string {
    return valor ? ETIQUETA_DE_RESULTADO[valor] : 'Resultado no anotado';
  }

  protected mensaje(fallo: unknown): string {
    return traducirError(fallo);
  }
}
