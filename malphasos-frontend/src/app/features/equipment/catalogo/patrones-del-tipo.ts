import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CatalogoApi } from '../catalogo-api';
import { traducirError } from '../../../core/errores/traducir';
import { fechaCorta, fechaIso, HOY } from '../../../core/fechas';
import { normalizar } from '../../../core/texto';
import { Patron, VerificacionDeTipo } from '../../../core/api/tipos';

/** Un patrón tal como se ofrece en una verificación. */
interface Candidato {
  patron: Patron;
  elegido: boolean;
  vence: string;
  vencido: boolean;
}

/**
 * Con qué equipos patrón se hace cada verificación de un tipo de equipo (V60, 2026-11-19).
 *
 * <p>Decidido por el usuario: el ingeniero no elige entre todos los patrones de la empresa, sino entre
 * los que el catálogo dice que sirven para esa verificación. Aquí se dice cuáles.
 *
 * <p><b>Página propia y no un bloque de la verificación</b>, porque la verificación se redeclara entera
 * desde la lista y vuelve con identificadores nuevos; los patrones cuelgan de la que ya existe. El
 * servidor los hereda al redeclarar, de modo que cambiar una tolerancia no los pierde.
 *
 * <p>Se ofrecen los vigentes, y además los retirados que ya estaban elegidos, marcados: así se pueden
 * quitar. El filtro no esconde nunca uno elegido, para que nadie quite uno sin verlo.
 */
@Component({
  selector: 'app-patrones-del-tipo',
  imports: [RouterLink],
  templateUrl: './patrones-del-tipo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatronesDelTipo {
  readonly id = input.required<string>();

  private readonly api = inject(CatalogoApi);
  private readonly router = inject(Router);
  private readonly hoy = inject(HOY);

  protected readonly tipo = this.api.detalleTipo(this.id);
  protected readonly patrones = this.api.listarPatrones();
  protected readonly actuales = this.api.patronesDeVerificaciones(this.id);
  protected readonly asignacion = this.api.asignarPatrones();

  /** Lo elegido en pantalla, por verificación. Nulo hasta que llegan los del servidor. */
  protected readonly elegidos = signal<Record<string, string[]> | null>(null);
  protected readonly filtro = signal('');
  protected readonly enviando = signal(false);
  protected readonly fallo = signal('');

  constructor() {
    effect(() => {
      const datos = this.actuales.data();

      if (datos && this.elegidos() === null) {
        this.elegidos.set(
          Object.fromEntries(datos.map((d) => [d.idVerificacion!, [...(d.patrones ?? [])]])),
        );
      }
    });
  }

  protected readonly cargando = computed(
    () => this.tipo.isPending() || this.patrones.isPending() || this.actuales.isPending(),
  );

  protected readonly mensajeDeError = computed(() => {
    if (this.fallo()) {
      return this.fallo();
    }
    const error = this.tipo.error() ?? this.patrones.error() ?? this.actuales.error();

    return error ? traducirError(error) : '';
  });

  protected readonly bloques = computed(() => {
    const elegidos = this.elegidos() ?? {};
    const hoy = fechaIso(this.hoy());
    const buscado = normalizar(this.filtro().trim());

    return (this.tipo.data()?.verificaciones ?? []).map((verificacion) => {
      const suyos = new Set(elegidos[verificacion.id!] ?? []);
      const candidatos: Candidato[] = (this.patrones.data() ?? [])
        .filter((p) => suyos.has(p.id!) || (p.estadoActivo && this.casa(p, buscado)))
        .map((patron) => ({
          patron,
          elegido: suyos.has(patron.id!),
          vence: fechaCorta(patron.vencimientoCertificado),
          vencido: !!patron.vencimientoCertificado && patron.vencimientoCertificado < hoy,
        }))
        .sort(
          (a, b) =>
            Number(b.elegido) - Number(a.elegido) ||
            (a.patron.nombre ?? '').localeCompare(b.patron.nombre ?? ''),
        );

      return { verificacion, titulo: this.titulo(verificacion), cuantos: suyos.size, candidatos };
    });
  });

  /** Las verificaciones cuya lista cambió respecto de la guardada. */
  private readonly cambiadas = computed(() => {
    const elegidos = this.elegidos() ?? {};

    return (this.actuales.data() ?? []).filter((guardada) => {
      const antes = new Set(guardada.patrones ?? []);
      const ahora = elegidos[guardada.idVerificacion!] ?? [];

      return antes.size !== ahora.length || ahora.some((id) => !antes.has(id));
    });
  });

  protected readonly hayCambios = computed(() => this.cambiadas().length > 0);

  protected alternar(idVerificacion: string, idPatron: string): void {
    this.elegidos.update((todos) => {
      const suyos = todos?.[idVerificacion] ?? [];

      return {
        ...todos,
        [idVerificacion]: suyos.includes(idPatron)
          ? suyos.filter((id) => id !== idPatron)
          : [...suyos, idPatron],
      };
    });
  }

  /**
   * Una petición por verificación cambiada, una tras otra. Si una falla se dice cuántas entraron: el
   * servidor guarda cada verificación por su cuenta.
   */
  protected async guardar(): Promise<void> {
    const elegidos = this.elegidos() ?? {};
    const pendientes = this.cambiadas();
    let guardadas = 0;

    this.enviando.set(true);
    this.fallo.set('');
    try {
      for (const verificacion of pendientes) {
        await this.asignacion.mutateAsync({
          id: this.id(),
          idVerificacion: verificacion.idVerificacion!,
          asignacion: { patrones: elegidos[verificacion.idVerificacion!] ?? [] },
        });
        guardadas++;
      }
      void this.router.navigate(['/catalogo']);
    } catch (error) {
      this.fallo.set(
        (guardadas ? `Se guardaron ${guardadas} de ${pendientes.length} verificaciones. ` : '') +
          traducirError(error),
      );
    } finally {
      this.enviando.set(false);
    }
  }

  private titulo(verificacion: VerificacionDeTipo): string {
    return `${verificacion.magnitud} · ${verificacion.unidad}`;
  }

  private casa(patron: Patron, buscado: string): boolean {
    return (
      !buscado ||
      [patron.nombre, patron.marca, patron.modelo, patron.serie, patron.codigoInterno].some(
        (campo) => !!campo && normalizar(campo).includes(buscado),
      )
    );
  }
}
