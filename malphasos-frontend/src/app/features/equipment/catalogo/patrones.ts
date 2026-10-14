import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CatalogoApi } from '../catalogo-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';
import { fechaCorta, fechaIso, HOY } from '../../../core/fechas';
import { Patron } from '../../../core/api/tipos';

/** Cuántos días antes del vencimiento se avisa. Es el umbral de RF-41, que todavía no se construye. */
export const DIAS_DE_AVISO = 30;

/**
 * El catálogo de equipos patrón (2026-10-14): los aparatos de referencia de la empresa, contra los que
 * se toman las lecturas de una verificación.
 *
 * <p>Decidido por el usuario: los patrones son un catálogo, no texto en cada reporte, y un reporte con un
 * patrón vencido el día del servicio no se cierra. Por eso lo que más se mira aquí es el vencimiento: la
 * lista lo dice en cada fila, y pesa más cuanto más urge —vencido, por vencer en treinta días—.
 *
 * <p>Corregir un patrón o renovar su certificado no cambia los reportes que ya lo usaron: copiaron lo que
 * tenía ese día.
 */
@Component({
  selector: 'app-patrones',
  imports: [ReactiveFormsModule],
  templateUrl: './patrones.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Patrones {
  private readonly api = inject(CatalogoApi);
  private readonly formBuilder = inject(FormBuilder);
  private readonly sesion = inject(Sesion);
  private readonly hoy = inject(HOY);

  protected readonly patrones = this.api.listarPatrones();
  protected readonly alta = this.api.crearPatron();
  protected readonly cambio = this.api.editarPatron();
  protected readonly baja = this.api.retirarPatron();

  protected readonly puedeEscribir = computed(() => this.sesion.puede('equipment.write'));

  /** Los vigentes primero; entre ellos, los que vencen antes. Los retirados al final. */
  protected readonly filas = computed(() => {
    const hoy = fechaIso(this.hoy());
    const aviso = fechaIso(new Date(this.hoy().getTime() + DIAS_DE_AVISO * 86_400_000));

    return [...(this.patrones.data() ?? [])]
      .sort(
        (a, b) =>
          Number(b.estadoActivo) - Number(a.estadoActivo) ||
          (a.vencimientoCertificado ?? '').localeCompare(b.vencimientoCertificado ?? ''),
      )
      .map((patron) => {
        const vence = patron.vencimientoCertificado ?? '';
        const vencido = !!vence && vence < hoy;
        const porVencer = !vencido && !!vence && vence <= aviso;

        return {
          patron,
          vence: fechaCorta(vence),
          estadoDelCertificado: vencido ? 'Vencido' : porVencer ? 'Por vencer' : 'Vigente',
          // El manual: lo que urge pesa más, sin colores nuevos.
          peso: vencido ? 'font-extrabold' : porVencer ? 'font-bold' : 'font-normal',
        };
      });
  });

  protected readonly formulario = this.formBuilder.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    marca: ['', [Validators.maxLength(80)]],
    serie: ['', [Validators.required, Validators.maxLength(60)]],
    certificado: ['', [Validators.required, Validators.maxLength(80)]],
    vencimientoCertificado: ['', Validators.required],
  });

  /** El patrón que se está corrigiendo, o nulo si el formulario es de alta. */
  protected readonly editando = signal<string | null>(null);
  protected readonly confirmandoRetiro = signal<string | null>(null);

  protected readonly hayError = computed(
    () => this.patrones.isError() || this.alta.isError() || this.cambio.isError() || this.baja.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(this.patrones.error() ?? this.alta.error() ?? this.cambio.error() ?? this.baja.error()),
  );

  protected empezarAEditar(patron: Patron): void {
    this.formulario.setValue({
      nombre: patron.nombre ?? '',
      marca: patron.marca ?? '',
      serie: patron.serie ?? '',
      certificado: patron.certificado ?? '',
      vencimientoCertificado: patron.vencimientoCertificado ?? '',
    });
    this.editando.set(patron.id!);
  }

  protected cancelar(): void {
    this.formulario.reset();
    this.editando.set(null);
  }

  protected guardar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    const valores = this.formulario.getRawValue();
    const datos = {
      nombre: valores.nombre.trim(),
      marca: valores.marca.trim() || undefined,
      serie: valores.serie.trim(),
      certificado: valores.certificado.trim(),
      vencimientoCertificado: valores.vencimientoCertificado,
    };
    const id = this.editando();
    const alTerminar = { onSuccess: () => this.cancelar() };

    if (id) {
      this.cambio.mutate({ id, cambio: datos }, alTerminar);
    } else {
      this.alta.mutate(datos, alTerminar);
    }
  }

  protected retirar(id: string): void {
    this.baja.mutate(id, { onSuccess: () => this.confirmandoRetiro.set(null) });
  }
}
