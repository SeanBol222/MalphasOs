import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { EquipoApi } from '../../equipment/equipo-api';
import { ReporteApi } from '../reporte-api';
import { Sesion } from '../../../core/auth/sesion';
import {
  ETIQUETA_DE_ESTADO_DE_REPORTE,
  ETIQUETA_DE_RESULTADO,
  RESULTADOS_DE_SERVICIO,
  sePuedeCerrar,
} from '../../../core/api/tipos';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/**
 * Un reporte de servicio: qué se encontró, qué se hizo y cómo quedó el equipo.
 *
 * <p><b>Ningún campo es obligatorio para guardar, y dos lo son para cerrar.</b> Es deliberado y es lo
 * que hace el backend: el reporte se abre vacío al llegar al equipo y se llena a trozos mientras se
 * trabaja. Procedimientos y resultado se exigen al cerrarlo, no al escribir.
 *
 * <p><b>Cerrar es una operación, no un campo.</b> No hay desplegable de estado: hay un botón que
 * aparece cuando se puede usar, igual que iniciar y ejecutar en una orden. Y va con confirmación,
 * porque desde ahí el reporte ya no cambia — corregirlo es retirarlo y abrir otro, que es justo lo que
 * dice el aviso.
 *
 * <p><b>Un campo que se deja vacío se borra</b>, y eso también es del backend: manda lo que el
 * formulario muestra, de modo que vaciar una casilla en pantalla vacía el dato. La alternativa
 * —distinguir «no lo toqué» de «lo borré»— exigiría comparar contra el original en cada tecla para
 * mandar solo lo cambiado, y en un formulario que muestra todo lo que hay no tiene sentido.
 */
@Component({
  selector: 'app-detalle-reporte',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './detalle-reporte.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleReporte {
  readonly id = input.required<string>();

  private readonly api = inject(ReporteApi);
  private readonly sesion = inject(Sesion);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly reporte = this.api.detalle(this.id);

  protected readonly guardado = this.api.llenar();
  protected readonly cierre = this.api.cerrar();
  protected readonly retiro = this.api.retirar();

  private readonly equipos = inject(EquipoApi).listarTodos();

  protected readonly puedeEscribir = computed(() => this.sesion.puede('report.write'));

  protected readonly confirmandoCierre = signal(false);
  protected readonly confirmandoRetiro = signal(false);

  protected readonly RESULTADOS = RESULTADOS_DE_SERVICIO;
  protected readonly etiquetaDeResultado = ETIQUETA_DE_RESULTADO;

  protected readonly formulario = this.fb.nonNullable.group({
    fallaReportada: '',
    diagnostico: '',
    procedimientos: '',
    observaciones: '',
    resultado: '',
  });

  /**
   * Un reporte cerrado o retirado no se toca, y el formulario lo refleja desactivándose.
   *
   * <p>Se desactiva en vez de esconderse porque lo escrito sigue siendo lo que hay que leer: un reporte
   * finalizado es el documento que se entregó.
   */
  protected readonly editable = computed(
    () =>
      this.puedeEscribir() &&
      this.reporte.data()?.estado === 'BORRADOR' &&
      !!this.reporte.data()?.estadoActivo,
  );

  protected readonly cerrable = computed(() => sePuedeCerrar(this.reporte.data()));

  protected readonly estado = computed(() => {
    const datos = this.reporte.data();

    if (!datos) {
      return '';
    }
    if (!datos.estadoActivo) {
      return 'Retirado';
    }

    return datos.estado ? ETIQUETA_DE_ESTADO_DE_REPORTE[datos.estado] : '';
  });

  /** La serie del equipo del que habla el reporte. El API devuelve el identificador y nada más. */
  protected readonly equipo = computed(() => {
    const id = this.reporte.data()?.idEquipoCliente;

    return (
      (this.equipos.data() ?? []).find((equipo) => equipo.id === id)?.serie ?? 'Equipo no disponible'
    );
  });

  protected readonly hayError = computed(
    () =>
      this.reporte.isError() ||
      this.guardado.isError() ||
      this.cierre.isError() ||
      this.retiro.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.reporte.error() ?? this.guardado.error() ?? this.cierre.error() ?? this.retiro.error(),
    ),
  );
  protected readonly detalles = computed(() =>
    detallesDe(this.guardado.error() ?? this.cierre.error()),
  );

  constructor() {
    /**
     * Vuelca en el formulario lo que el servidor tiene.
     *
     * <p>Solo mientras nadie haya escrito: sin la guarda, cada recarga de la caché —y hay una después de
     * cada guardado— pisaría lo que la persona está tecleando. Es el mismo problema que la edición de un
     * cliente, resuelto igual.
     */
    effect(() => {
      const datos = this.reporte.data();

      if (!datos || this.formulario.dirty) {
        return;
      }

      this.formulario.patchValue(
        {
          fallaReportada: datos.fallaReportada ?? '',
          diagnostico: datos.diagnostico ?? '',
          procedimientos: datos.procedimientos ?? '',
          observaciones: datos.observaciones ?? '',
          resultado: datos.resultado ?? '',
        },
        { emitEvent: false },
      );
    });

    // El formulario se desactiva cuando el reporte deja de ser un borrador. Un control desactivado no
    // se puede tocar ni con el teclado, que es lo que hace falta: esconderlo perderia el contenido.
    effect(() => {
      if (this.editable()) {
        this.formulario.enable({ emitEvent: false });
      } else {
        this.formulario.disable({ emitEvent: false });
      }
    });
  }

  protected guardar(): void {
    const valores = this.formulario.getRawValue();

    this.guardado.mutate(
      {
        id: this.id(),
        datos: {
          fallaReportada: valores.fallaReportada,
          diagnostico: valores.diagnostico,
          procedimientos: valores.procedimientos,
          observaciones: valores.observaciones,
          // Sin resultado se manda ausente, no vacio: la cadena vacia no es un valor del catalogo y el
          // servidor la rechazaria. Un resultado puesto por error se sustituye, no se quita.
          resultado: valores.resultado
            ? (valores.resultado as NonNullable<(typeof RESULTADOS_DE_SERVICIO)[number]>)
            : undefined,
        },
      },
      { onSuccess: () => this.formulario.markAsPristine() },
    );
  }

  protected cerrar(): void {
    this.cierre.mutate(this.id(), { onSuccess: () => this.confirmandoCierre.set(false) });
  }

  protected retirar(): void {
    const idOrden = this.reporte.data()?.idOrdenTrabajo;

    this.retiro.mutate(this.id(), {
      // Se vuelve a la orden: quedarse en un reporte retirado invita a seguir tocandolo, y desde la
      // orden se puede abrir otro, que es la forma de corregirlo.
      onSuccess: () => void this.router.navigate(idOrden ? ['/ordenes', idOrden] : ['/ordenes']),
    });
  }
}
