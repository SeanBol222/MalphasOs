import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EquipoApi } from '../../equipment/equipo-api';
import { OrdenApi } from '../../workOrder/orden-api';
import { ReporteApi } from '../reporte-api';
import {
  ETIQUETA_DE_ESTADO_DE_REPORTE,
  ETIQUETA_DE_RESULTADO,
  ETIQUETA_DE_TIPO_DE_SERVICIO,
} from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';

/**
 * Lo que se le ha hecho a un equipo: sus reportes de servicio, del más reciente al más antiguo.
 *
 * <p><b>Esto no es la hoja de vida</b>, y conviene no confundirlo: RF-26 y RF-27 piden un historial de
 * intervenciones <b>dentro de la hoja de vida</b>, que es una entidad que no existe todavía. Esta
 * pantalla es la consulta sobre la que se construirá, y ya responde a la pregunta que importa en campo:
 * qué se le hizo a este aparato y cómo quedó cada vez.
 *
 * <p><b>Salen también los retirados.</b> Un reporte retirado se sacó del listado de su orden, pero
 * ocurrió: retirarlo no reescribe la historia, y un historial que los esconde deja de ser un historial.
 * Se distinguen por peso tipográfico, como todos los estados de esta aplicación.
 *
 * <p>La fecha que se muestra es la de <b>cierre</b> cuando el reporte está cerrado, porque es cuando se
 * prestó el servicio de verdad. Un borrador no tiene esa fecha, así que se cae a la fecha programada de
 * su orden — que es lo único que se sabe de un trabajo que todavía no terminó.
 */
@Component({
  selector: 'app-historial-equipo',
  imports: [RouterLink],
  templateUrl: './historial-equipo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistorialEquipo {
  readonly id = input.required<string>();

  protected readonly reportes = inject(ReporteApi).porEquipo(this.id);

  private readonly equipos = inject(EquipoApi).listarTodos();
  private readonly ordenes = inject(OrdenApi).listar();

  /** La serie del equipo. El API de reportes devuelve identificadores y nada más. */
  protected readonly serie = computed(
    () =>
      (this.equipos.data() ?? []).find((equipo) => equipo.id === this.id())?.serie ??
      'Equipo no disponible',
  );

  protected readonly filas = computed(() => {
    const ordenes = new Map((this.ordenes.data() ?? []).map((orden) => [orden.id, orden]));

    return (this.reportes.data() ?? [])
      .map((reporte) => {
        const orden = ordenes.get(reporte.idOrdenTrabajo);

        return {
          id: reporte.id!,
          idOrden: reporte.idOrdenTrabajo,
          // La de cierre si la hay; si no, la programada de la orden. Un borrador no tiene fecha propia.
          fecha: reporte.finalizado?.slice(0, 10) ?? orden?.fechaMantenimiento ?? '',
          cerrado: !!reporte.finalizado,
          servicio: orden?.tipoServicio ? ETIQUETA_DE_TIPO_DE_SERVICIO[orden.tipoServicio] : '',
          estado: reporte.estadoActivo
            ? reporte.estado
              ? ETIQUETA_DE_ESTADO_DE_REPORTE[reporte.estado]
              : ''
            : 'Retirado',
          activo: !!reporte.estadoActivo,
          resultado: reporte.resultado ? ETIQUETA_DE_RESULTADO[reporte.resultado] : '',
          lecturas: reporte.lecturas?.length ?? 0,
        };
      })
      // Del más reciente al más antiguo: un historial se lee por arriba. Las de fecha desconocida
      // quedan al final, que es donde estorban menos.
      .sort((uno, otro) => (otro.fecha || '').localeCompare(uno.fecha || ''));
  });

  protected readonly hayError = computed(() => this.reportes.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.reportes.error()));
}
