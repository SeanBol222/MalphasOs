import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AreaApi } from '../../client/area-api';
import { CatalogoApi } from '../catalogo-api';
import { EquipoApi } from '../equipo-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';

/**
 * Un area de servicio con los equipos instalados en ella.
 *
 * <p>Vive en {@code features/equipment} y no en {@code features/client} aunque el area sea del modulo
 * de clientes, porque <b>lo que esta pantalla hace es gestionar equipos</b>: el area es el contexto.
 * Lo que se lee del area —su nombre y si esta abierta— cabe en una consulta.
 *
 * <p>Es el final de la cadena del catalogo y el sitio donde una orden de trabajo encontrara equipos que
 * tocar: una orden solo puede incluir equipos de areas de su propia sede.
 */
@Component({
  selector: 'app-detalle-area',
  imports: [RouterLink],
  templateUrl: './detalle-area.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleArea {
  readonly id = input.required<string>();

  private readonly api = inject(EquipoApi);
  private readonly sesion = inject(Sesion);
  private readonly catalogo = inject(CatalogoApi);

  protected readonly area = inject(AreaApi).detalle(this.id);
  private readonly equipos = this.api.listarDe(this.id);
  protected readonly baja = this.api.darDeBaja();

  private readonly modelos = this.catalogo.listarModelos();
  private readonly equiposDeCatalogo = this.catalogo.listarEquipos();
  private readonly tipos = this.catalogo.listarTipos();
  private readonly marcas = this.catalogo.listarMarcas();

  protected readonly puedeRegistrar = computed(() => this.sesion.puede('equipment.assign'));
  protected readonly puedeEditar = computed(() => this.sesion.puede('equipment.write'));

  /** El equipo cuya baja se esta confirmando. Dar de baja no es un clic. */
  protected readonly confirmandoBaja = signal<string | null>(null);

  protected readonly cargando = computed(() => this.equipos.isPending());

  /**
   * Cada equipo con su modelo en palabras: «tipo · marca · fabricante».
   *
   * <p>Cuatro listas del catalogo para componer una etiqueta, porque {@code ClientEquipmentResponse}
   * solo trae {@code idModelo}. La serie es lo que identifica la maquina y va delante.
   */
  protected readonly filas = computed(() => {
    const tipos = new Map((this.tipos.data() ?? []).map((t) => [t.id, t.nombre]));
    const marcas = new Map((this.marcas.data() ?? []).map((m) => [m.id, m.nombre]));
    const equipos = new Map(
      (this.equiposDeCatalogo.data() ?? []).map((equipo) => [
        equipo.id,
        `${tipos.get(equipo.idTipoEquipo!) ?? 'Tipo no disponible'} · ${
          marcas.get(equipo.idMarca!) ?? 'Marca no disponible'
        }`,
      ]),
    );
    const modelos = new Map(
      (this.modelos.data() ?? []).map((modelo) => [
        modelo.id,
        equipos.get(modelo.idEquipo!) ?? 'Modelo no disponible',
      ]),
    );

    return (this.equipos.data() ?? []).map((equipo) => ({
      id: equipo.id!,
      serie: equipo.serie,
      numeroInventario: equipo.numeroInventario,
      fechaCompra: equipo.fechaCompra,
      estadoActivo: equipo.estadoActivo,
      modelo: modelos.get(equipo.idModelo!) ?? 'Modelo no disponible',
    }));
  });

  protected readonly hayError = computed(() => this.area.isError() || this.equipos.isError() || this.baja.isError());
  protected readonly mensajeDeError = computed(() =>
    traducirError(this.area.error() ?? this.equipos.error() ?? this.baja.error()),
  );

  protected darDeBaja(id: string): void {
    this.baja.mutate(id, { onSuccess: () => this.confirmandoBaja.set(null) });
  }
}
