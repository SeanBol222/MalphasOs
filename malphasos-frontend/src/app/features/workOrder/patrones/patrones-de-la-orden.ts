import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogoApi } from '../../equipment/catalogo-api';
import { OrdenApi } from '../orden-api';
import { traducirError } from '../../../core/errores/traducir';
import { PatronesDeUnTipo } from './patrones-de-un-tipo';

/**
 * Los patrones que la orden lleva a su visita, por tipo de equipo (V60, 2026-11-19).
 *
 * <p>Decidido por el usuario: la orden elige los suyos por tipo, para repartir dos del mismo uso entre dos
 * tipos —las pesas de 5 kg para las pesa bebé y las de 20 kg para las de piso—, y cada reporte de ese tipo
 * los recibe. Se cambian hasta ejecutarla. Si el catálogo admite para un tipo un solo patrón vigente, la
 * orden ya lo trae puesto al llegar aquí.
 */
@Component({
  selector: 'app-patrones-de-la-orden',
  imports: [RouterLink, PatronesDeUnTipo],
  templateUrl: './patrones-de-la-orden.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatronesDeLaOrden {
  readonly id = input.required<string>();

  private readonly ordenes = inject(OrdenApi);
  private readonly catalogo = inject(CatalogoApi);

  protected readonly orden = this.ordenes.detalle(this.id);
  protected readonly porTipo = this.ordenes.patrones(this.id);
  private readonly tipos = this.catalogo.listarTipos();
  protected readonly patrones = this.catalogo.listarPatrones();

  protected readonly cargando = computed(
    () =>
      this.orden.isPending() ||
      this.porTipo.isPending() ||
      this.tipos.isPending() ||
      this.patrones.isPending(),
  );

  protected readonly mensajeDeError = computed(() => {
    const error =
      this.orden.error() ?? this.porTipo.error() ?? this.tipos.error() ?? this.patrones.error();
    return error ? traducirError(error) : '';
  });

  /** Mientras no se ejecuta, se cambian: lo mismo que comprueba el servidor. */
  protected readonly editable = computed(
    () => this.orden.data()?.estadoEjecucion !== 'EJECUTADA' && !!this.orden.data()?.estadoActivo,
  );

  /** Un bloque por tipo de la orden, por nombre. */
  protected readonly bloques = computed(() => {
    const nombres = new Map((this.tipos.data() ?? []).map((t) => [t.id, t.nombre ?? '']));

    return (this.porTipo.data() ?? [])
      .map((tipo) => ({
        idTipo: tipo.idTipoEquipo!,
        nombre: nombres.get(tipo.idTipoEquipo) ?? 'Tipo de equipo',
        guardados: tipo.patrones ?? [],
      }))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  });
}
