import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReporteApi } from '../reporte-api';
import { AlcanceDeFirma, QuienRecibe } from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';
import { fechaCorta } from '../../../core/fechas';
import { LienzoDeFirma } from '../../../shared/lienzo-de-firma/lienzo-de-firma';

/**
 * A quién se entregó el equipo (V31, 2026-10-16): nombre, cédula y cargo, en texto, porque quien
 * recibe muchas veces no está registrado en el sistema. Es el último bloque del reporte, como en el
 * informe impreso, donde va junto a su firma.
 *
 * <p>Los tres campos en blanco borran lo que hubiera. Cédula o cargo sin nombre no dicen quién recibió,
 * y el servidor los rechaza: aquí se avisa antes.
 *
 * <p><b>Y aquí firma quien recibe</b> (V39, 2026-10-17, RF-18): en el lienzo, antes o después de cerrar
 * —muchas veces el equipo se entrega después—, y una sola vez. Por eso los campos se pueden escribir en un
 * reporte cerrado mientras no esté firmado: firmar registra quién firma. La casilla es RF-21: la misma
 * firma en los reportes de la orden que aún no tengan.
 *
 * <p><b>Se guarda sola desde el 2026-10-27</b>, como la recepción y la verificación: «Guardar entrega»
 * se retiró, y los datos se guardan al salir de cada campo si hay nombre —o si los tres quedan en
 * blanco, que es borrar—. Solo en un borrador sin firmar, que es cuando registrar sin firma tiene
 * sentido. Firmar sigue siendo un botón: es una decisión explícita, y manda los datos con la firma.
 */
@Component({
  selector: 'app-entrega-del-equipo',
  imports: [ReactiveFormsModule, LienzoDeFirma],
  templateUrl: './entrega-del-equipo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EntregaDelEquipo {
  readonly idReporte = input.required<string>();
  readonly recibe = input<QuienRecibe | null | undefined>(null);
  readonly editable = input(false);
  /** Cuándo firmó quien recibe; nulo mientras no firme (V39). */
  readonly firmadoRecibe = input<string | null | undefined>(null);
  /**
   * Activo y con {@code report.write}: un retirado no se firma, y quien solo lee no ve un lienzo que el
   * servidor le rechazaría.
   */
  readonly puedeFirmar = input(true);

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ReporteApi);
  protected readonly registro = this.api.registrarEntrega();

  /** Si se guardó lo último que se cambió: para decirlo, ya que no hay botón que lo diga. */
  protected readonly guardado = signal(false);

  /** Un guardado en vuelo, y otro cambio que llegó mientras tanto: ver la recepción. */
  private enVuelo = false;
  private otraVez = false;
  protected readonly firmado = this.api.firmarRecibido();

  protected readonly formulario = this.fb.nonNullable.group({ nombre: '', cedula: '', cargo: '' });
  /**
   * RF-21: a cuántos reportes va la firma. Quien recibe puede firmar uno, todos los del área de este equipo o
   * todos los de la orden, que es la sede entera (2026-10-30).
   */
  protected readonly alcance = this.fb.nonNullable.control<AlcanceDeFirma>('REPORTE');
  protected readonly alcances: readonly { valor: AlcanceDeFirma; etiqueta: string }[] = [
    { valor: 'REPORTE', etiqueta: 'Solo este reporte' },
    { valor: 'AREA', etiqueta: 'Los reportes de esta área en la orden que aún no la tengan' },
    { valor: 'ORDEN', etiqueta: 'Todos los reportes de la orden que aún no la tengan' },
  ];

  private readonly escrito = toSignal(this.formulario.valueChanges);

  /** Se puede firmar: con permiso, activo y sin firma, abierto o cerrado. */
  protected readonly firmable = computed(() => this.puedeFirmar() && !this.firmadoRecibe());
  /** Registrar la entrega sin firmar es solo del borrador, y deja de serlo al firmar. */
  protected readonly registrable = computed(() => this.editable() && !this.firmadoRecibe());

  protected readonly faltaElNombre = computed(() => {
    this.escrito();
    const { nombre, cedula, cargo } = this.formulario.getRawValue();

    return !nombre.trim() && !!(cedula.trim() || cargo.trim());
  });

  protected readonly trazo = signal<string | null>(null);
  protected readonly faltaTrazo = signal(false);
  protected readonly faltaQuienFirma = signal(false);
  /** Cuántos reportes firmó la última firma, y con qué alcance: el aviso dice si fueron más de uno. */
  protected readonly firmados = signal(0);
  protected readonly firmadosEn = signal<AlcanceDeFirma>('REPORTE');

  private readonly lienzo = viewChild(LienzoDeFirma);

  protected readonly mensajeDeError = computed(() => {
    const fallo = this.registro.error() ?? this.firmado.error();

    // El 409 de los reportes habla de la orden y del cierre. Al firmar solo puede ser que alguien firmó
    // antes —otra pestaña, o la réplica de otro reporte de la orden—.
    return this.firmado.error() && fallo instanceof HttpErrorResponse && fallo.status === 409
      ? 'Este reporte ya tiene la firma de quien recibe. Recargue la página para verla.'
      : traducirError(fallo);
  });
  protected readonly fechaCorta = (fecha: string | null | undefined) =>
    fechaCorta(fecha?.slice(0, 10));

  constructor() {
    effect(() => {
      const recibe = this.recibe();

      if (this.formulario.dirty) {
        return;
      }

      this.formulario.setValue(
        { nombre: recibe?.nombre ?? '', cedula: recibe?.cedula ?? '', cargo: recibe?.cargo ?? '' },
        { emitEvent: false },
      );
    });

    effect(() => {
      if (this.registrable() || this.firmable()) {
        this.formulario.enable({ emitEvent: false });
      } else {
        this.formulario.disable({ emitEvent: false });
      }
    });
  }

  /**
   * La guarda si hay algo nuevo y dice quién recibió. Si se está guardando otra cosa, la deja para
   * después: dos peticiones en vuelo podrían llegar al revés.
   */
  protected guardar(): void {
    if (!this.registrable() || !this.formulario.dirty || this.faltaElNombre()) {
      return;
    }
    if (this.enVuelo) {
      this.otraVez = true;

      return;
    }

    const valor = this.formulario.getRawValue();
    const enviado = JSON.stringify(valor);
    const texto = (t: string) => t.trim() || undefined;
    this.guardado.set(false);
    this.enVuelo = true;

    this.registro.mutate(
      {
        id: this.idReporte(),
        recibe: {
          nombre: texto(valor.nombre),
          cedula: texto(valor.cedula),
          cargo: texto(valor.cargo),
        },
      },
      {
        onSuccess: () => {
          // Solo se da por guardado si nadie cambió nada mientras tanto: si no, eso sigue pendiente.
          if (JSON.stringify(this.formulario.getRawValue()) === enviado) {
            this.formulario.markAsPristine();
            this.guardado.set(true);
          }
        },
        onSettled: () => {
          this.enVuelo = false;
          if (this.otraVez) {
            this.otraVez = false;
            this.guardar();
          }
        },
      },
    );
  }

  protected alDibujar(imagen: string | null): void {
    this.trazo.set(imagen);
    if (imagen) {
      this.faltaTrazo.set(false);
    }
  }

  /** RF-18: sin nombre no se sabe quién firmó, y sin trazo no hay firma. Ninguna de las dos se envía. */
  protected firmar(): void {
    const { nombre, cedula, cargo } = this.formulario.getRawValue();
    const imagen = this.trazo();
    this.faltaQuienFirma.set(!nombre.trim());
    this.faltaTrazo.set(!imagen);
    if (!nombre.trim() || !imagen) {
      return;
    }

    const texto = (t: string) => t.trim() || undefined;
    this.firmado.mutate(
      {
        id: this.idReporte(),
        firma: {
          nombre: nombre.trim(),
          cedula: texto(cedula),
          cargo: texto(cargo),
          imagen,
          alcance: this.alcance.value,
        },
      },
      {
        onSuccess: (reportes) => {
          this.formulario.markAsPristine();
          this.firmados.set(reportes.length);
          this.firmadosEn.set(this.alcance.value);
        },
      },
    );
  }
}
