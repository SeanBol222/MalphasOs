import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { CatalogoApi } from '../catalogo-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';
import { fechaCorta, fechaIso, HOY } from '../../../core/fechas';
import { Patron } from '../../../core/api/tipos';
import { normalizar } from '../../../core/texto';
import { CabeceraDePieza } from './cabecera-de-pieza';
import { ColumnaOrdenable, Sentido, compararEn, invertido } from './columna-ordenable';

/** Cuántos días antes del vencimiento se avisa. Es el umbral de RF-41, que todavía no se construye. */
export const DIAS_DE_AVISO = 30;

/**
 * La calibración no puede ser posterior al vencimiento del certificado que produjo: es la regla del
 * servidor, y aquí se comprueba antes para decirlo junto al campo. Con fechas ISO la comparación de
 * textos es la de fechas.
 */
export function calibracionAntesDelVencimiento(grupo: AbstractControl): ValidationErrors | null {
  const { fechaCalibracion, vencimientoCertificado } = grupo.getRawValue();

  return fechaCalibracion && vencimientoCertificado && fechaCalibracion > vencimientoCertificado
    ? { calibracionPosterior: true }
    : null;
}

/** Lo que se escribe en un campo opcional: recortado, y en blanco es que falta. */
const opcional = (valor: string) => valor.trim() || undefined;

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
 *
 * <p>Desde el 2026-10-16 lleva su ficha completa —modelo, código interno, rango, resolución, fecha de
 * calibración y empresa calibradora—, que es lo que los informes impresos dicen de cada patrón en su
 * tabla de trazabilidad. Todo opcional.
 */
@Component({
  selector: 'app-patrones',
  imports: [ReactiveFormsModule, CabeceraDePieza, ColumnaOrdenable],
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

  /** Lo escrito en la lupa de la cabecera. */
  protected readonly busqueda = signal('');

  /** El sentido del orden por nombre: de la A a la Z por defecto, y la cabecera lo invierte. */
  protected readonly sentido = signal<Sentido>('asc');

  protected invertirOrden(): void {
    this.sentido.update(invertido);
  }

  /**
   * Los que casan con lo buscado —nombre, marca, modelo, serie, codigo o certificado—, por nombre en el
   * orden elegido. Hasta el 2026-11-20 iban los vigentes primero y, entre ellos, los que vencen antes;
   * desde entonces el catalogo entero ordena por su primera columna, decidido por el usuario, y lo que
   * urge se sigue viendo por el peso de «Vencido» y «Por vencer».
   */
  protected readonly filas = computed(() => {
    const hoy = fechaIso(this.hoy());
    const aviso = fechaIso(new Date(this.hoy().getTime() + DIAS_DE_AVISO * 86_400_000));
    const palabras = normalizar(this.busqueda()).split(' ').filter(Boolean);

    return [...(this.patrones.data() ?? [])]
      .filter((patron) => {
        const texto = normalizar(
          [
            patron.nombre,
            patron.marca,
            patron.modelo,
            patron.serie,
            patron.codigoInterno,
            patron.certificado,
          ]
            .filter(Boolean)
            .join(' '),
        );

        return palabras.every((palabra) => texto.includes(palabra));
      })
      .sort((a, b) => compararEn(this.sentido(), a.nombre, b.nombre))
      .map((patron) => {
        const vence = patron.vencimientoCertificado ?? '';
        const vencido = !!vence && vence < hoy;
        const porVencer = !vencido && !!vence && vence <= aviso;

        return {
          patron,
          marcaYModelo: [patron.marca, patron.modelo].filter(Boolean).join(' ') || 'Sin marca',
          vence: fechaCorta(vence),
          estadoDelCertificado: vencido ? 'Vencido' : porVencer ? 'Por vencer' : 'Vigente',
          // El manual: lo que urge pesa más, sin colores nuevos.
          peso: vencido ? 'font-extrabold' : porVencer ? 'font-bold' : 'font-normal',
        };
      });
  });

  protected readonly formulario = this.formBuilder.nonNullable.group(
    {
      nombre: ['', [Validators.required, Validators.maxLength(120)]],
      marca: ['', [Validators.maxLength(80)]],
      modelo: ['', [Validators.maxLength(80)]],
      serie: ['', [Validators.required, Validators.maxLength(60)]],
      codigoInterno: ['', [Validators.maxLength(40)]],
      rango: ['', [Validators.maxLength(120)]],
      resolucion: ['', [Validators.maxLength(80)]],
      certificado: ['', [Validators.required, Validators.maxLength(80)]],
      empresaCalibradora: ['', [Validators.maxLength(120)]],
      fechaCalibracion: [''],
      vencimientoCertificado: ['', Validators.required],
    },
    { validators: calibracionAntesDelVencimiento },
  );

  private readonly injector = inject(Injector);
  private readonly formularioEnPantalla = viewChild<ElementRef<HTMLElement>>('formularioDePatron');

  /** El patrón que se está corrigiendo, o nulo si el formulario es de alta. */
  protected readonly editando = signal<string | null>(null);

  /**
   * El formulario se abre con «Nuevo patrón» o al corregir uno, encima de la tabla, y se cierra al
   * guardar o al cancelar.
   */
  private readonly altaAbierta = signal(false);
  protected readonly abierto = computed(() => this.altaAbierta() || !!this.editando());

  protected abrir(): void {
    this.formulario.reset();
    this.editando.set(null);
    this.altaAbierta.set(true);
  }
  protected readonly confirmandoRetiro = signal<string | null>(null);

  protected readonly hayError = computed(
    () => this.patrones.isError() || this.alta.isError() || this.cambio.isError() || this.baja.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(this.patrones.error() ?? this.alta.error() ?? this.cambio.error() ?? this.baja.error()),
  );

  /** Los cuatro obligatorios, aparte de la regla de fechas, que tiene su propio mensaje. */
  protected faltaAlgoObligatorio(): boolean {
    const { nombre, serie, certificado, vencimientoCertificado } = this.formulario.controls;

    return (
      this.formulario.touched &&
      [nombre, serie, certificado, vencimientoCertificado].some((control) => control.invalid)
    );
  }

  protected empezarAEditar(patron: Patron): void {
    this.formulario.setValue({
      nombre: patron.nombre ?? '',
      marca: patron.marca ?? '',
      modelo: patron.modelo ?? '',
      serie: patron.serie ?? '',
      codigoInterno: patron.codigoInterno ?? '',
      rango: patron.rango ?? '',
      resolucion: patron.resolucion ?? '',
      certificado: patron.certificado ?? '',
      empresaCalibradora: patron.empresaCalibradora ?? '',
      fechaCalibracion: patron.fechaCalibracion ?? '',
      vencimientoCertificado: patron.vencimientoCertificado ?? '',
    });
    this.editando.set(patron.id!);
    // El formulario se abre encima de la tabla: si se pulso «Editar» en una fila de abajo, quedaria
    // fuera de la vista. Se sube hasta el en cuanto existe (2026-11-20, pedido por el usuario).
    afterNextRender(
      () =>
        this.formularioEnPantalla()?.nativeElement.scrollIntoView?.({
          behavior: 'smooth',
          block: 'start',
        }),
      { injector: this.injector },
    );
  }

  protected cancelar(): void {
    this.formulario.reset();
    this.editando.set(null);
    this.altaAbierta.set(false);
  }

  protected guardar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    const valores = this.formulario.getRawValue();
    const datos = {
      nombre: valores.nombre.trim(),
      marca: opcional(valores.marca),
      modelo: opcional(valores.modelo),
      serie: valores.serie.trim(),
      codigoInterno: opcional(valores.codigoInterno),
      rango: opcional(valores.rango),
      resolucion: opcional(valores.resolucion),
      certificado: valores.certificado.trim(),
      empresaCalibradora: opcional(valores.empresaCalibradora),
      fechaCalibracion: valores.fechaCalibracion || undefined,
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
