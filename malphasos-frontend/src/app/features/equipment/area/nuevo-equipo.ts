import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AreaApi } from '../../client/area-api';
import { CatalogoApi } from '../catalogo-api';
import { EquipoApi } from '../equipo-api';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/**
 * Registro de un equipo en un area de servicio: el final de la cadena del catalogo.
 *
 * <p>Aqui un modelo deja de ser una entrada de catalogo y pasa a ser <b>una maquina concreta</b>. Lo
 * unico obligatorio es la serie y el modelo: el inventario, la fecha y el valor de compra pueden
 * faltar, porque un equipo se registra cuando llega y esos datos aparecen despues.
 *
 * <p><b>Si no hay modelos no se puede registrar nada</b>, y eso no es un fallo de esta pantalla sino la
 * cadena del catalogo: hace falta una marca, un tipo, su combinacion y un fabricante. Se dice aqui con
 * un enlace al catalogo, en vez de dejar un desplegable vacio sin explicacion.
 */
@Component({
  selector: 'app-nuevo-equipo',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './nuevo-equipo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuevoEquipo {
  /** El area en la que se instala. Viene de la ruta. */
  readonly id = input.required<string>();

  private readonly router = inject(Router);
  private readonly catalogo = inject(CatalogoApi);

  protected readonly area = inject(AreaApi).detalle(this.id);
  protected readonly alta = inject(EquipoApi).registrar();

  private readonly modelos = this.catalogo.listarModelos();
  private readonly equipos = this.catalogo.listarEquipos();
  private readonly tipos = this.catalogo.listarTipos();
  private readonly marcas = this.catalogo.listarMarcas();
  private readonly fabricantes = this.catalogo.listarFabricantes();

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    idModelo: ['', Validators.required],
    serie: ['', [Validators.required, Validators.maxLength(50)]],
    numeroInventario: ['', Validators.maxLength(50)],
    fechaCompra: [''],
    valorCompra: [null as number | null],
  });

  /**
   * Los modelos activos, cada uno legible: «tipo · marca · fabricante».
   *
   * <p>Cuatro listas para una etiqueta. El API devuelve identificadores, y un desplegable de UUID no se
   * puede usar.
   */
  protected readonly modelosOfrecidos = computed(() => {
    const tipos = new Map((this.tipos.data() ?? []).map((t) => [t.id, t.nombre]));
    const marcas = new Map((this.marcas.data() ?? []).map((m) => [m.id, m.nombre]));
    const fabricantes = new Map((this.fabricantes.data() ?? []).map((f) => [f.id, f.nombre]));
    const equipos = new Map(
      (this.equipos.data() ?? []).map((equipo) => [
        equipo.id,
        `${tipos.get(equipo.idTipoEquipo!) ?? 'Tipo no disponible'} · ${
          marcas.get(equipo.idMarca!) ?? 'Marca no disponible'
        }`,
      ]),
    );

    return (this.modelos.data() ?? [])
      .filter((modelo) => modelo.estadoActivo)
      .map((modelo) => ({
        id: modelo.id!,
        etiqueta: `${equipos.get(modelo.idEquipo!) ?? 'Equipo no disponible'} · ${
          fabricantes.get(modelo.idFabricante!) ?? 'Fabricante no disponible'
        }`,
      }));
  });

  /** Que no haya modelos es distinto de que no hayan llegado todavia. */
  protected readonly sinModelos = computed(
    () => this.modelos.isSuccess() && this.modelosOfrecidos().length === 0,
  );

  protected readonly enviando = computed(() => this.alta.isPending());
  protected readonly mensajeDeError = computed(() =>
    this.alta.isError() ? traducirError(this.alta.error()) : '',
  );
  protected readonly detalles = computed(() => detallesDe(this.alta.error()));

  protected malo(campo: string): boolean {
    const control = this.formulario.get(campo);

    return !!control && control.invalid && control.touched;
  }

  protected enviar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    const datos = this.formulario.getRawValue();

    // Los opcionales vacios no viajan: una cadena vacia no es un numero de inventario y una fecha
    // vacia no es una fecha de compra.
    this.alta.mutate(
      {
        idArea: this.id(),
        equipo: {
          idModelo: datos.idModelo,
          serie: datos.serie,
          ...(datos.numeroInventario ? { numeroInventario: datos.numeroInventario } : {}),
          ...(datos.fechaCompra ? { fechaCompra: datos.fechaCompra } : {}),
          ...(datos.valorCompra === null ? {} : { valorCompra: datos.valorCompra }),
        },
      },
      { onSuccess: () => void this.router.navigate(['/areas', this.id()]) },
    );
  }
}
