import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Sesion } from '../../../core/auth/sesion';
import { HOY } from '../../../core/fechas';
import { CatalogoApi } from '../../equipment/catalogo-api';
import { OrdenApi } from '../../workOrder/orden-api';
import { ClienteApi } from '../../client/cliente-api';
import { Aviso, avisosDeOrdenes, avisosDePatrones, TipoDeAviso } from './avisos';
import { guardarLeidos, leidosDe } from './leidos';

type Filtro = 'todos' | TipoDeAviso;

/**
 * La campanita y su cuadro de avisos (2026-11-21): la propuesta 2 de tres, elegida por el usuario sobre
 * un prototipo interactivo, en escritorio junto al nombre al pie de la barra lateral y en un teléfono en
 * la barra de arriba. En los dos sale un cuadro, no una pantalla entera: en el teléfono lo pidió así.
 *
 * <p><b>Cada sesión ve lo que puede resolver.</b> Los patrones, quien administra el catálogo
 * ({@code equipment.write}, la misma autoridad que la entrada «Catálogo»): un supervisor del cliente
 * tiene {@code equipment.read} y los patrones de la empresa no son asunto suyo. Las órdenes por aprobar,
 * quien aprueba ({@code work-order.approve}): el servidor ya las acota a lo que la sesión ve. Sin ninguna
 * de las dos, la campanita no se pinta.
 *
 * <p>El número cuenta los <b>no leídos</b>. Tocar un aviso lo marca y lleva a su pantalla; leído sigue en
 * la lista, en blanco, hasta que se resuelve lo que lo causa.
 */
@Component({
  selector: 'app-campana-de-avisos',
  imports: [CdkTrapFocus],
  templateUrl: './campana-de-avisos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents', '(keydown.escape)': 'cerrar()' },
})
export class CampanaDeAvisos {
  private readonly sesion = inject(Sesion);
  private readonly router = inject(Router);
  private readonly hoy = inject(HOY);

  protected readonly vePatrones = computed(() => this.sesion.puede('equipment.write'));
  protected readonly veOrdenes = computed(() => this.sesion.puede('work-order.approve'));
  protected readonly visible = computed(() => this.vePatrones() || this.veOrdenes());

  private readonly patrones = inject(CatalogoApi).listarPatrones(this.vePatrones);
  private readonly ordenes = inject(OrdenApi).porAprobar(this.veOrdenes);
  private readonly clientes = inject(ClienteApi).listar(this.veOrdenes);

  private readonly usuario = computed(() => String(this.sesion.usuario() ?? ''));

  protected readonly avisos = computed((): Aviso[] => {
    const nombres = new Map(
      (this.clientes.data() ?? []).map((c) => [c.id ?? '', c.razonSocial ?? '']),
    );

    return [
      ...(this.vePatrones() ? avisosDePatrones(this.patrones.data() ?? [], this.hoy()) : []),
      ...(this.veOrdenes() ? avisosDeOrdenes(this.ordenes.data() ?? [], nombres) : []),
    ];
  });

  protected readonly leidos = signal<ReadonlySet<string>>(new Set());

  protected readonly noLeidos = computed(
    () => this.avisos().filter((aviso) => !this.leidos().has(aviso.clave)).length,
  );

  protected readonly abierto = signal(false);
  protected readonly filtro = signal<Filtro>('todos');

  /** Los filtros solo si hay de los dos tipos que elegir: con uno solo, «Todos» y ese dicen lo mismo. */
  protected readonly filtros = computed(() => {
    if (!this.vePatrones() || !this.veOrdenes()) {
      return [];
    }

    const cuantos = (tipo: Filtro) =>
      this.avisos().filter((aviso) => tipo === 'todos' || aviso.tipo === tipo).length;

    return (
      [
        ['todos', 'Todos'],
        ['patron', 'Patrones'],
        ['orden', 'Órdenes'],
      ] as const
    ).map(([clave, texto]) => ({ clave, texto: `${texto} · ${cuantos(clave)}` }));
  });

  protected readonly visibles = computed(() =>
    this.avisos().filter((aviso) => this.filtro() === 'todos' || aviso.tipo === this.filtro()),
  );

  protected readonly cargando = computed(
    () =>
      (this.vePatrones() && this.patrones.isPending()) ||
      (this.veOrdenes() && this.ordenes.isPending()),
  );

  protected readonly fallo = computed(
    () =>
      (this.vePatrones() && this.patrones.isError()) ||
      (this.veOrdenes() && this.ordenes.isError()),
  );

  protected readonly etiqueta = computed(() =>
    this.noLeidos() > 0 ? `Avisos, ${this.noLeidos()} sin leer` : 'Avisos',
  );

  private readonly boton = viewChild<ElementRef<HTMLElement>>('boton');

  constructor() {
    effect(() => this.leidos.set(leidosDe(this.usuario())));
  }

  protected alternar(): void {
    if (this.abierto()) {
      this.cerrar();
    } else {
      this.abierto.set(true);
    }
  }

  protected cerrar(): void {
    if (!this.abierto()) {
      return;
    }

    this.abierto.set(false);
    this.boton()?.nativeElement.focus();
  }

  protected abrir(aviso: Aviso): void {
    this.marcar([aviso.clave]);
    this.abierto.set(false);
    void this.router.navigate([...aviso.ruta]);
  }

  protected marcarTodo(): void {
    this.marcar(this.avisos().map((aviso) => aviso.clave));
  }

  protected leido(aviso: Aviso): boolean {
    return this.leidos().has(aviso.clave);
  }

  private marcar(claves: readonly string[]): void {
    const leidos = new Set([...this.leidos(), ...claves]);

    this.leidos.set(leidos);
    // Solo se recorta con todo cargado: con una consulta a medias, lo leído de la otra se perdería.
    const completo =
      (!this.vePatrones() || this.patrones.isSuccess()) &&
      (!this.veOrdenes() || this.ordenes.isSuccess());
    guardarLeidos(
      this.usuario(),
      leidos,
      completo ? this.avisos().map((aviso) => aviso.clave) : [...leidos],
    );
  }
}
