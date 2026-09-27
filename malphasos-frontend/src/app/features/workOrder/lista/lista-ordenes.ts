import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClienteApi } from '../../client/cliente-api';
import { SedeApi } from '../../client/sede-api';
import { OrdenApi } from '../orden-api';
import { Sesion } from '../../../core/auth/sesion';
import {
  ETIQUETA_DE_ESTADO,
  ETIQUETA_DE_TIPO_DE_SERVICIO,
  EstadoDeEjecucion,
  TipoDeServicio,
} from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';

/**
 * Las ordenes de trabajo programadas.
 *
 * <p>Cierra la mitad de lectura de RF-01 y es la puerta al modulo que el backend termino el 2026-09-13 y
 * nadie podia usar: de sus siete requisitos, cuatro describen un formulario, y esto es el primero.
 *
 * <p>Cada fila necesita dos nombres que la orden no trae —el del cliente y el de su sede—, de modo que se
 * cruzan con la lista de clientes y con una consulta por sede distinta. Es la misma ausencia anotada ya
 * en dos modulos: las respuestas devuelven identificadores.
 */
@Component({
  selector: 'app-lista-ordenes',
  imports: [RouterLink],
  templateUrl: './lista-ordenes.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaOrdenes {
  private readonly sesion = inject(Sesion);

  protected readonly ordenes = inject(OrdenApi).listar();
  private readonly clientes = inject(ClienteApi).listar();

  /** Las sedes que aparecen de verdad en la lista, sin repetir. */
  private readonly idsDeSedes = computed(() => [
    ...new Set((this.ordenes.data() ?? []).map((orden) => orden.idSede!).filter(Boolean)),
  ]);

  private readonly sedes = inject(SedeApi).variasPorId(this.idsDeSedes);

  protected readonly puedeProgramar = computed(() => this.sesion.puede('work-order.write'));

  protected readonly filas = computed(() => {
    const clientes = new Map((this.clientes.data() ?? []).map((c) => [c.id, c.razonSocial]));
    const sedes = new Map((this.sedes.data() ?? []).map((s) => [s.id, s.nombre]));

    return (this.ordenes.data() ?? []).map((orden) => ({
      id: orden.id!,
      fecha: orden.fechaMantenimiento,
      cliente: clientes.get(orden.idCliente!) ?? 'Cliente no disponible',
      sede: sedes.get(orden.idSede!) ?? 'Sede no disponible',
      servicio: this.etiquetaDeServicio(orden.tipoServicio),
      estado: this.etiquetaDeEstado(orden.estadoEjecucion),
      // Sin equipos una orden esta programada pero no dice sobre que se trabaja: se ve de un golpe.
      equipos: orden.equipos?.length ?? 0,
      estadoActivo: orden.estadoActivo,
    }));
  });

  protected readonly hayError = computed(() => this.ordenes.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.ordenes.error()));

  private etiquetaDeEstado(estado: EstadoDeEjecucion | undefined): string {
    return estado ? ETIQUETA_DE_ESTADO[estado] : '';
  }

  private etiquetaDeServicio(tipo: TipoDeServicio | undefined): string {
    return tipo ? ETIQUETA_DE_TIPO_DE_SERVICIO[tipo] : '';
  }
}
