import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CatalogoApi } from '../catalogo-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';

/**
 * Los modelos: un equipo del catalogo mas un fabricante, con su registro INVIMA.
 *
 * <p><b>Es el ultimo eslabon, y el unico que se puede instalar en un area.</b> Si no hay modelos, no
 * se puede registrar ningun equipo de cliente, y esa es la causa mas probable de que el formulario de
 * alta aparezca sin opciones.
 *
 * <p>Cada fila se compone cruzando cuatro listas —modelos, equipos, tipos y marcas—, porque la
 * respuesta solo trae identificadores y un modelo legible es «tipo · marca · fabricante».
 */
@Component({
  selector: 'app-modelos',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './modelos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Modelos {
  private readonly api = inject(CatalogoApi);
  private readonly sesion = inject(Sesion);

  private readonly modelos = this.api.listarModelos();
  private readonly equipos = this.api.listarEquipos();
  private readonly tipos = this.api.listarTipos();
  private readonly marcas = this.api.listarMarcas();
  protected readonly fabricantes = this.api.listarFabricantes();
  protected readonly alta = this.api.crearModelo();
  protected readonly baja = this.api.retirarModelo();

  protected readonly puedeEscribir = computed(() => this.sesion.puede('equipment.write'));

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    // El nombre es obligatorio y el INVIMA no, y la asimetria es del backend: un modelo sin registro
    // sanitario es un estado normal mientras se tramita, y un modelo sin nombre no es nada.
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    idEquipo: ['', Validators.required],
    idFabricante: ['', Validators.required],
    invima: ['', Validators.maxLength(50)],
  });

  protected readonly cargando = computed(() => this.modelos.isPending());

  /** Un equipo del catalogo en palabras: «tipo · marca». Se usa en la lista y en el desplegable. */
  private readonly equiposLegibles = computed(() => {
    const tipos = new Map((this.tipos.data() ?? []).map((t) => [t.id, t.nombre]));
    const marcas = new Map((this.marcas.data() ?? []).map((m) => [m.id, m.nombre]));

    return new Map(
      (this.equipos.data() ?? []).map((equipo) => [
        equipo.id,
        {
          etiqueta: `${tipos.get(equipo.idTipoEquipo!) ?? 'Tipo no disponible'} · ${
            marcas.get(equipo.idMarca!) ?? 'Marca no disponible'
          }`,
          estadoActivo: equipo.estadoActivo,
        },
      ]),
    );
  });

  protected readonly filas = computed(() => {
    const equipos = this.equiposLegibles();
    const fabricantes = new Map((this.fabricantes.data() ?? []).map((f) => [f.id, f.nombre]));

    return (this.modelos.data() ?? []).map((modelo) => ({
      id: modelo.id!,
      estadoActivo: modelo.estadoActivo,
      nombre: modelo.nombre,
      equipo: equipos.get(modelo.idEquipo!)?.etiqueta ?? 'Equipo no disponible',
      fabricante: fabricantes.get(modelo.idFabricante!) ?? 'Fabricante no disponible',
      invima: modelo.invima,
    }));
  });

  /** Solo piezas activas: el backend rechaza una referencia retirada. */
  protected readonly equiposActivos = computed(() =>
    [...this.equiposLegibles().entries()]
      .filter(([, equipo]) => equipo.estadoActivo)
      .map(([id, equipo]) => ({ id: id!, etiqueta: equipo.etiqueta })),
  );
  protected readonly fabricantesActivos = computed(() =>
    (this.fabricantes.data() ?? []).filter((f) => f.estadoActivo),
  );

  protected readonly hayError = computed(
    () => this.modelos.isError() || this.alta.isError() || this.baja.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(this.modelos.error() ?? this.alta.error() ?? this.baja.error()),
  );

  protected agregar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    const { nombre, idEquipo, idFabricante, invima } = this.formulario.getRawValue();

    // El INVIMA es opcional y una cadena vacia no es un registro sanitario: no se manda.
    this.alta.mutate(
      invima ? { nombre, idEquipo, idFabricante, invima } : { nombre, idEquipo, idFabricante },
      { onSuccess: () => this.formulario.reset() },
    );
  }

  protected retirar(id: string): void {
    this.baja.mutate(id);
  }
}
