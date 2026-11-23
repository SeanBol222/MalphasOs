import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  Injectable,
  input,
  Signal,
  signal,
} from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { AreaApi } from '../../client/area-api';
import { EquipoApi } from '../../equipment/equipo-api';
import { Sesion } from '../../../core/auth/sesion';
import { OrdenApi } from '../orden-api';
import { environment } from '../../../../environments/environment';
import { EquipoDeCliente } from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';

/**
 * Elegir los equipos de una orden: por área de la sede, varios a la vez.
 *
 * <p><b>Es la pantalla que cierran RF-04, RF-06 y RF-07</b> —ver las áreas de la sede, ver los equipos de
 * cada área y seleccionar varios—, los tres requisitos que el backend satisfacía desde el 2026-09-13 y que
 * seguían contando como no implementados porque describen un formulario.
 *
 * <p><b>Solo se ofrecen equipos de áreas de la sede de la orden</b>, y eso no es una comodidad: es la
 * regla que el backend construyó el 2026-09-13 —el área del equipo tiene que ser de la sede de la orden— y
 * que hasta hoy no se había ejercido desde un navegador. Ofrecer el catálogo entero dejaría que el
 * servidor rechazara la mitad de las casillas marcadas.
 *
 * <p><b>El API suma un equipo por llamada, así que varios son varias llamadas en serie.</b> Sin
 * transacción que las envuelva: si la tercera falla, las dos primeras quedan dentro. Se dice cuántos
 * entraron en lugar de dejar la pantalla en un estado que no corresponde a nada — es la misma honestidad
 * que el panel que crea un modelo con sus piezas.
 *
 * <p><b>Cada equipo dice qué es, no solo su serie</b> (2026-10-27, pedido por el usuario): tipo y marca,
 * sacados del listado que compone el servidor. Los equipos de un área solo traen el identificador de su
 * modelo, y el tipo y la marca están dos eslabones más arriba en el catálogo.
 *
 * <p><b>Las áreas son desplegables y van en orden alfabético, como sus equipos</b> (2026-11-20, pedido
 * por el usuario): con todas abiertas la pantalla se llenaba, y un área del final obligaba a recorrer
 * las de arriba. Cerradas, la lista de áreas cabe en una pantalla, y cada una dice cuántos equipos
 * tiene y cuántos lleva marcados, para que cerrarla no esconda lo elegido. Un equipo marcado se ve
 * por la fila entera, oscura, y no por una casilla: la casilla sigue ahí para el teclado y el lector de
 * pantalla.
 */
@Component({
  selector: 'app-agregar-equipos',
  imports: [RouterLink],
  templateUrl: './agregar-equipos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgregarEquipos {
  readonly id = input.required<string>();

  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly ordenApi = inject(OrdenApi);

  protected readonly orden = this.ordenApi.detalle(this.id);
  private readonly alta = this.ordenApi.agregarEquipo();

  /** Las áreas de la sede de la orden: el límite de lo que se puede elegir. */
  protected readonly areas = inject(AreaApi).listarDe(
    computed(() => this.orden.data()?.idSede ?? ''),
  );

  /**
   * Los equipos de cada área, consultados cuando se sabe qué áreas hay.
   *
   * <p>Una consulta por área, porque es lo que el API ofrece: los equipos cuelgan del área. Van en una
   * sola entrada de caché con clave derivada de las áreas, de modo que no se repiten.
   */
  protected readonly equiposPorArea = inject(EquiposDeAreas).de(
    computed(() => (this.areas.data() ?? []).filter((area) => area.estadoActivo).map((a) => a.id!)),
  );

  /**
   * Tipo y marca de cada equipo ofrecido, del listado del servidor. Exige {@code equipment.read}: sin
   * él, cada equipo se ofrece por su serie, como antes.
   */
  private readonly sesion = inject(Sesion);
  private readonly descripciones = inject(EquipoApi).resumenDe(
    computed(() =>
      this.sesion.puede('equipment.read')
        ? (this.equiposPorArea.data() ?? []).flatMap((grupo) => grupo.equipos.map((e) => e.id!))
        : [],
    ),
  );

  protected readonly seleccionados = signal<ReadonlySet<string>>(new Set());
  /** Las áreas desplegadas. Todas empiezan cerradas. */
  protected readonly abiertas = signal<ReadonlySet<string>>(new Set());
  protected readonly enviando = signal(false);
  protected readonly resultado = signal<{ entraron: number; fallaron: number } | null>(null);
  protected readonly fallo = signal<unknown>(null);

  /** Lo que ya está en la orden no se vuelve a ofrecer: añadirlo otra vez no significa nada. */
  private readonly yaEnLaOrden = computed(
    () => new Set((this.orden.data()?.equipos ?? []).map((equipo) => equipo.idEquipoCliente!)),
  );

  protected readonly grupos = computed(() => {
    const porArea = this.equiposPorArea.data() ?? [];
    const yaEstan = this.yaEnLaOrden();
    const descripcion = new Map(
      (this.descripciones.data()?.filas ?? []).map((fila) => [
        fila.id,
        [fila.tipoEquipo, fila.marca].filter(Boolean).join(' · '),
      ]),
    );

    return (this.areas.data() ?? [])
      .filter((area) => area.estadoActivo)
      .sort((a, b) => ALFABETICO.compare(a.nombre ?? '', b.nombre ?? ''))
      .map((area) => ({
        id: area.id!,
        nombre: area.nombre,
        equipos: (porArea.find((grupo) => grupo.idArea === area.id)?.equipos ?? [])
          .filter((equipo) => equipo.estadoActivo && !yaEstan.has(equipo.id!))
          .map((equipo) => ({
            id: equipo.id!,
            serie: equipo.serie,
            inventario: equipo.numeroInventario,
            descripcion: descripcion.get(equipo.id) ?? '',
          }))
          // Por lo que es; sin descripcion -sin equipment.read-, por la serie, que es lo que se ve.
          .sort((a, b) =>
            ALFABETICO.compare(a.descripcion || a.serie || '', b.descripcion || b.serie || ''),
          ),
      }));
  });

  /** Que no haya nada que elegir es distinto de que no haya llegado. */
  protected readonly sinNada = computed(
    () =>
      this.areas.isSuccess() &&
      this.equiposPorArea.isSuccess() &&
      this.grupos().every((grupo) => grupo.equipos.length === 0),
  );

  protected readonly cuantos = computed(() => this.seleccionados().size);

  protected readonly mensajeDeError = computed(() =>
    this.fallo() === null ? '' : traducirError(this.fallo()),
  );

  protected abierta(idArea: string): boolean {
    return this.abiertas().has(idArea);
  }

  protected desplegar(idArea: string): void {
    this.abiertas.update((actuales) => {
      const nuevas = new Set(actuales);

      if (!nuevas.delete(idArea)) {
        nuevas.add(idArea);
      }

      return nuevas;
    });
  }

  /** Lo que el área dice cerrada: cuántos equipos ofrece y cuántos lleva marcados. */
  protected resumen(grupo: { equipos: readonly { id: string }[] }): string {
    const total = grupo.equipos.length;

    if (!total) {
      return 'Sin equipos disponibles';
    }

    const marcados = grupo.equipos.filter((equipo) => this.marcado(equipo.id)).length;
    const equipos = `${total} ${total === 1 ? 'equipo' : 'equipos'}`;

    return marcados
      ? `${equipos} · ${marcados} ${marcados === 1 ? 'marcado' : 'marcados'}`
      : equipos;
  }

  protected marcado(id: string): boolean {
    return this.seleccionados().has(id);
  }

  protected alternar(id: string): void {
    this.seleccionados.update((actuales) => {
      const nuevos = new Set(actuales);

      if (!nuevos.delete(id)) {
        nuevos.add(id);
      }

      return nuevos;
    });
  }

  protected marcarArea(idArea: string, marcar: boolean): void {
    const equipos = this.grupos().find((grupo) => grupo.id === idArea)?.equipos ?? [];

    this.seleccionados.update((actuales) => {
      const nuevos = new Set(actuales);

      for (const equipo of equipos) {
        if (marcar) {
          nuevos.add(equipo.id);
        } else {
          nuevos.delete(equipo.id);
        }
      }

      return nuevos;
    });
  }

  /**
   * Suma los seleccionados, uno por llamada.
   *
   * <p>En serie y no en paralelo: cada alta abre el borrador de su equipo (V50) y elige su número entre
   * los libres, y mandarlas a la vez sería pedirle que resuelva carreras que nadie necesita.
   */
  protected async agregar(): Promise<void> {
    const elegidos = [...this.seleccionados()];

    if (!elegidos.length) {
      return;
    }

    this.enviando.set(true);
    this.fallo.set(null);
    let entraron = 0;

    for (const idEquipoCliente of elegidos) {
      try {
        await this.alta.mutateAsync({ id: this.id(), idEquipoCliente });
        entraron += 1;
      } catch (fallo) {
        // Se guarda el primer fallo y se sigue: que uno no entre no es motivo para abandonar el resto.
        if (this.fallo() === null) {
          this.fallo.set(fallo);
        }
      }
    }

    this.enviando.set(false);
    this.seleccionados.set(new Set());
    this.resultado.set({ entraron, fallaron: elegidos.length - entraron });

    if (entraron === elegidos.length) {
      void this.router.navigate(['/ordenes', this.id()]);
    }
  }
}

/** Sin distinguir tildes ni mayúsculas: «Área de partos» va con la A, no después de la Z. */
const ALFABETICO = new Intl.Collator('es', { sensitivity: 'base', numeric: true });

/**
 * Los equipos de varias áreas, en una sola consulta de caché.
 *
 * <p>Vive aquí y no en {@code EquipoApi} porque es una necesidad de esta pantalla: el resto de la
 * aplicación pide los equipos de <b>un</b> área. Emite una petición por área, que es lo que el API ofrece.
 */
@Injectable({ providedIn: 'root' })
class EquiposDeAreas {
  private readonly http = inject(HttpClient);

  de(idsDeAreas: Signal<readonly string[]>) {
    return injectQuery(() => ({
      queryKey: ['equipos-de-areas', [...idsDeAreas()].sort().join(',')],
      queryFn: async () =>
        Promise.all(
          idsDeAreas().map(async (idArea) => ({
            idArea,
            equipos: await firstValueFrom(
              this.http.get<EquipoDeCliente[]>(
                `${environment.api}/v1/api/service-areas/${idArea}/equipments`,
              ),
            ),
          })),
        ),
      enabled: idsDeAreas().length > 0,
    }));
  }
}
