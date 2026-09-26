import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CatalogoApi } from '../catalogo-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';

/**
 * Los equipos del catalogo: la combinacion de un <b>tipo</b> con una <b>marca</b>.
 *
 * <p>Es el eslabon que mas cuesta entender, porque «equipo» aqui no es una maquina: es «tensiometro
 * Welch Allyn» como categoria. La maquina concreta —con su serie— es el equipo de un cliente, y se
 * registra en un area. Esta pantalla lo dice en voz alta para que nadie busque aqui su tensiometro.
 *
 * <p>La respuesta solo trae identificadores, de modo que cada fila se compone cruzando las listas de
 * tipos y marcas. Es la misma ausencia que en los encargados: el API no devuelve nombres.
 */
@Component({
  selector: 'app-equipos-de-catalogo',
  imports: [ReactiveFormsModule],
  templateUrl: './equipos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquiposDeCatalogo {
  private readonly api = inject(CatalogoApi);
  private readonly sesion = inject(Sesion);

  private readonly equipos = this.api.listarEquipos();
  protected readonly tipos = this.api.listarTipos();
  protected readonly marcas = this.api.listarMarcas();
  protected readonly alta = this.api.crearEquipo();
  protected readonly baja = this.api.retirarEquipo();

  protected readonly puedeEscribir = computed(() => this.sesion.puede('equipment.write'));

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    idTipoEquipo: ['', Validators.required],
    idMarca: ['', Validators.required],
  });

  protected readonly cargando = computed(() => this.equipos.isPending());

  /** Cada equipo con su tipo y su marca en palabras, que es lo unico legible de esta lista. */
  protected readonly filas = computed(() => {
    const tipos = new Map((this.tipos.data() ?? []).map((t) => [t.id, t.nombre]));
    const marcas = new Map((this.marcas.data() ?? []).map((m) => [m.id, m.nombre]));

    return (this.equipos.data() ?? []).map((equipo) => ({
      id: equipo.id!,
      estadoActivo: equipo.estadoActivo,
      // Si la pieza no esta en la lista, se dice: un UUID en pantalla no sirve de nada.
      tipo: tipos.get(equipo.idTipoEquipo!) ?? 'Tipo no disponible',
      marca: marcas.get(equipo.idMarca!) ?? 'Marca no disponible',
    }));
  });

  /** Solo se ofrecen piezas activas: el backend rechaza una referencia retirada. */
  protected readonly tiposActivos = computed(() =>
    (this.tipos.data() ?? []).filter((t) => t.estadoActivo),
  );
  protected readonly marcasActivas = computed(() =>
    (this.marcas.data() ?? []).filter((m) => m.estadoActivo),
  );

  protected readonly hayError = computed(
    () => this.equipos.isError() || this.alta.isError() || this.baja.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(this.equipos.error() ?? this.alta.error() ?? this.baja.error()),
  );

  protected agregar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    this.alta.mutate(this.formulario.getRawValue(), {
      onSuccess: () => this.formulario.reset(),
    });
  }

  protected retirar(id: string): void {
    this.baja.mutate(id);
  }
}
