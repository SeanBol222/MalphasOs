import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogoApi } from '../../equipment/catalogo-api';
import { OrdenApi } from '../orden-api';

/**
 * Lo que la orden lleva a la visita, en su ficha (V60): por tipo de equipo, los nombres de sus patrones,
 * y el enlace para cambiarlos mientras no se ejecute. Es lo que el ingeniero mira antes de salir.
 */
@Component({
  selector: 'app-patrones-de-la-visita',
  imports: [RouterLink],
  templateUrl: './patrones-de-la-visita.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatronesDeLaVisita {
  readonly idOrden = input.required<string>();
  /** Si quien mira puede cambiarlos ahora: con work-order.write y la orden sin ejecutar. */
  readonly cambiables = input(false);

  private readonly catalogo = inject(CatalogoApi);
  protected readonly porTipo = inject(OrdenApi).patrones(this.idOrden);
  private readonly tipos = this.catalogo.listarTipos();
  private readonly patrones = this.catalogo.listarPatrones();

  protected readonly cargando = computed(
    () => this.porTipo.isPending() || this.tipos.isPending() || this.patrones.isPending(),
  );

  protected readonly filas = computed(() => {
    const tipos = new Map((this.tipos.data() ?? []).map((t) => [t.id, t.nombre ?? '']));
    const patrones = new Map(
      (this.patrones.data() ?? []).map((p) => [p.id, `${p.nombre} · serie ${p.serie}`]),
    );

    return (this.porTipo.data() ?? [])
      .map((tipo) => ({
        idTipo: tipo.idTipoEquipo!,
        tipo: tipos.get(tipo.idTipoEquipo) ?? 'Tipo de equipo',
        patrones: (tipo.patrones ?? [])
          .map((id) => patrones.get(id) ?? 'Patrón')
          .sort((a, b) => a.localeCompare(b, 'es')),
      }))
      .sort((a, b) => a.tipo.localeCompare(b.tipo, 'es'));
  });
}
