import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  ETIQUETA_DE_RESULTADO,
  ETIQUETA_DE_TIPO_DE_SERVICIO,
  ResultadoDeServicio,
  TipoDeServicio,
} from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';
import { EquipoApi } from '../equipo-api';

/**
 * La hoja de vida de un equipo instalado: las cuatro secciones de RF-22 en una sola pagina.
 *
 * <p><b>Es un documento, no un formulario.</b> No tiene un solo control editable, y es la decision
 * tomada sobre RF-24: cada dato vive en su propio agregado —la sede en la sede, el modelo en el
 * modelo— y se corrige alli. Editarlo desde aqui ocultaria que cambiar la marca de un equipo la cambia
 * para todos los que la comparten. Se lee y se imprime.
 *
 * <p><b>Existe desde que el equipo se registra.</b> Un equipo recien dado de alta tiene sus tres
 * primeras secciones llenas y el historial vacio, y la pagina lo dice asi en vez de esconder la
 * seccion: no es que falte la hoja de vida, es que todavia no le han hecho nada.
 *
 * <p>La cuarta seccion se escribe sola cuando se cierra un reporte de servicio. No hay forma de anadir
 * una linea a mano, que es lo que pide el segundo criterio de RF-26.
 */
@Component({
  selector: 'app-hoja-de-vida',
  imports: [RouterLink],
  templateUrl: './hoja-de-vida.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HojaDeVida {
  readonly id = input.required<string>();

  protected readonly hoja = inject(EquipoApi).hojaDeVida(this.id);

  protected readonly identificacion = computed(() => this.hoja.data()?.identificacion);
  protected readonly tecnica = computed(() => this.hoja.data()?.tecnica);
  protected readonly fabricante = computed(() => this.hoja.data()?.fabricante);

  protected readonly historial = computed(() =>
    (this.hoja.data()?.servicioTecnico ?? []).map((linea) => ({
      id: linea.id!,
      idReporte: linea.idReporteServicio,
      // El servidor manda fecha y hora; en un historial se lee el dia.
      fecha: linea.fechaServicio?.slice(0, 10) ?? '',
      servicio: linea.tipoServicio
        ? ETIQUETA_DE_TIPO_DE_SERVICIO[linea.tipoServicio as TipoDeServicio]
        : '',
      resultado: linea.resultado
        ? ETIQUETA_DE_RESULTADO[linea.resultado as ResultadoDeServicio]
        : '',
    })),
  );

  protected readonly hayError = computed(() => this.hoja.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.hoja.error()));

  /** El valor de compra en pesos, sin decimales: es lo que se escribe en una hoja de vida impresa. */
  protected readonly valorDeCompra = computed(() => {
    const valor = this.identificacion()?.valorCompra;

    return valor == null
      ? null
      : new Intl.NumberFormat('es-CO', {
          style: 'currency',
          currency: 'COP',
          maximumFractionDigits: 0,
        }).format(valor);
  });

  protected imprimir(): void {
    window.print();
  }
}
