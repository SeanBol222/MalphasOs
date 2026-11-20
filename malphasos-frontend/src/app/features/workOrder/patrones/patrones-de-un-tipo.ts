import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { CatalogoApi } from '../../equipment/catalogo-api';
import { OrdenApi } from '../orden-api';
import { traducirError } from '../../../core/errores/traducir';
import { fechaCorta, fechaIso, HOY } from '../../../core/fechas';
import { Patron } from '../../../core/api/tipos';

/** Un patrón tal como se ofrece para el tipo. */
interface Candidato {
  patron: Patron;
  elegido: boolean;
  vence: string;
  vencido: boolean;
  /** Elegido antes y que el catálogo ya no admite para el tipo: se enseña para poder quitarlo. */
  fueraDelCatalogo: boolean;
}

/**
 * Los patrones que la orden lleva para un tipo de equipo (V60, 2026-11-19).
 *
 * <p>Se ofrecen los que el catálogo admite en <b>alguna</b> verificación del tipo —las pesas de 5, 10 y
 * 20 kg de una báscula, todas a la vez si hace falta—, vigentes y sin retirar, y además los ya elegidos
 * aunque estén retirados, vencidos o fuera del catálogo, marcados, para poder quitarlos. Es lo mismo que
 * comprueba el servidor.
 *
 * <p>Cada tipo se guarda por su cuenta, con su botón: el servidor fija los de un tipo por petición, y
 * cada cambio llega en el acto a los reportes abiertos de ese tipo.
 */
@Component({
  selector: 'app-patrones-de-un-tipo',
  templateUrl: './patrones-de-un-tipo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatronesDeUnTipo {
  readonly idOrden = input.required<string>();
  readonly idTipo = input.required<string>();
  readonly nombre = input.required<string>();
  /** Los que la orden lleva hoy para el tipo, como los guardó el servidor. */
  readonly guardados = input.required<readonly string[]>();
  /** El catálogo de patrones de la empresa, para poner nombres y vencimientos. */
  readonly patrones = input.required<readonly Patron[]>();
  readonly editable = input(true);

  private readonly hoy = inject(HOY);
  private readonly catalogo = inject(CatalogoApi).patronesDeVerificaciones(this.idTipo);
  protected readonly asignacion = inject(OrdenApi).asignarPatrones();

  /** Lo elegido en pantalla. Nulo hasta que llega lo guardado; se rehace al guardar. */
  protected readonly elegidos = signal<readonly string[] | null>(null);
  protected readonly fallo = signal('');
  protected readonly guardadoAhora = signal(false);

  constructor() {
    effect(() => {
      const guardados = this.guardados();

      if (this.elegidos() === null) {
        this.elegidos.set([...guardados]);
      }
    });
  }

  protected readonly cargando = computed(() => this.catalogo.isPending());

  protected readonly errorDelCatalogo = computed(() => {
    const error = this.catalogo.error();
    return error ? traducirError(error) : '';
  });

  /** Lo que el catálogo admite en alguna verificación del tipo. */
  private readonly admitidos = computed(
    () => new Set((this.catalogo.data() ?? []).flatMap((v) => v.patrones ?? [])),
  );

  protected readonly sinCatalogo = computed(
    () => this.catalogo.isSuccess() && this.admitidos().size === 0,
  );

  protected readonly candidatos = computed<readonly Candidato[]>(() => {
    const elegidos = new Set(this.elegidos() ?? []);
    const admitidos = this.admitidos();
    const hoy = fechaIso(this.hoy());

    return this.patrones()
      .filter(
        (p) =>
          elegidos.has(p.id!) ||
          (admitidos.has(p.id!) && p.estadoActivo && (p.vencimientoCertificado ?? '') >= hoy),
      )
      .map((patron) => ({
        patron,
        elegido: elegidos.has(patron.id!),
        vence: fechaCorta(patron.vencimientoCertificado),
        vencido: !!patron.vencimientoCertificado && patron.vencimientoCertificado < hoy,
        fueraDelCatalogo: !admitidos.has(patron.id!),
      }))
      .sort((a, b) => (a.patron.nombre ?? '').localeCompare(b.patron.nombre ?? '', 'es'));
  });

  protected readonly cuantos = computed(() => (this.elegidos() ?? []).length);

  protected readonly hayCambios = computed(() => {
    const antes = new Set(this.guardados());
    const ahora = this.elegidos() ?? [];

    return antes.size !== ahora.length || ahora.some((id) => !antes.has(id));
  });

  protected alternar(idPatron: string): void {
    this.guardadoAhora.set(false);
    this.elegidos.update((suyos) =>
      (suyos ?? []).includes(idPatron)
        ? (suyos ?? []).filter((id) => id !== idPatron)
        : [...(suyos ?? []), idPatron],
    );
  }

  protected async guardar(): Promise<void> {
    this.fallo.set('');
    try {
      const fijados = await this.asignacion.mutateAsync({
        id: this.idOrden(),
        idTipoEquipo: this.idTipo(),
        asignacion: { patrones: [...(this.elegidos() ?? [])] },
      });
      this.elegidos.set([...(fijados.patrones ?? [])]);
      this.guardadoAhora.set(true);
    } catch (error) {
      this.fallo.set(traducirError(error));
    }
  }
}
