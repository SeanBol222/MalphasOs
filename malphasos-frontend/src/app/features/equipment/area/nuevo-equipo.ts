import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { AreaApi } from '../../client/area-api';
import { ClienteApi } from '../../client/cliente-api';
import { SedeApi } from '../../client/sede-api';
import { CatalogoApi } from '../catalogo-api';
import { EquipoApi } from '../equipo-api';
import { detallesDe, traducirError } from '../../../core/errores/traducir';
import { PanelDeModelo } from './panel-de-modelo';

/**
 * Registro de un equipo en un area de servicio: el final de la cadena del catalogo.
 *
 * <p>Aqui un modelo deja de ser una entrada de catalogo y pasa a ser <b>una maquina concreta</b>. Lo
 * unico obligatorio es la serie y el modelo: el inventario, la fecha y el valor de compra pueden
 * faltar, porque un equipo se registra cuando llega y esos datos aparecen despues.
 *
 * <p><b>Se entra por dos caminos y la pantalla es la misma</b>, decidido el 2026-09-26:
 *
 * <ul>
 *   <li>Desde un area —{@code /areas/:id/equipos/nuevo}—, y entonces el area ya esta fijada.
 *   <li>Desde el listado de equipos —{@code /equipos/nuevo}—, y entonces se elige cliente, sede y area
 *       en tres desplegables encadenados, porque quien llega ahi no viene navegando desde un cliente.
 * </ul>
 *
 * <p>Duplicar el formulario habria sido la via directa a que uno de los dos se quedara sin un arreglo.
 *
 * <p><b>El modelo se puede crear aqui mismo</b>, con lo que le falte al catalogo. Obligar a salir,
 * recorrer cuatro pantallas y volver a empezar era la forma segura de que alguien registrara el equipo
 * con un modelo parecido pero equivocado. Ver {@link PanelDeModelo}.
 */
@Component({
  selector: 'app-nuevo-equipo',
  imports: [ReactiveFormsModule, RouterLink, PanelDeModelo],
  templateUrl: './nuevo-equipo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuevoEquipo {
  /**
   * El area en la que se instala, cuando se entra desde ella.
   *
   * <p>Vacio al entrar desde el listado de equipos: se elige en el formulario. El nombre es {@code id}
   * porque es el parametro de la ruta y {@code withComponentInputBinding} los empareja por nombre.
   */
  readonly id = input<string>('');

  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);
  private readonly catalogo = inject(CatalogoApi);

  protected readonly formulario = this.formBuilder.nonNullable.group({
    idCliente: [''],
    idSede: [''],
    idAreaElegida: [''],
    idModelo: ['', Validators.required],
    serie: ['', [Validators.required, Validators.maxLength(50)]],
    numeroInventario: ['', Validators.maxLength(50)],
    fechaCompra: [''],
    valorCompra: [null as number | null],
  });

  /** Los formularios reactivos no son senales; sin esto, ningun calculado volveria a evaluarse. */
  private readonly valores = toSignal(
    this.formulario.valueChanges.pipe(map(() => this.formulario.getRawValue())),
    { initialValue: this.formulario.getRawValue() },
  );

  /** Si el area viene dada, no se pregunta por ella ni por su cliente. */
  protected readonly areaFijada = computed(() => !!this.id());

  /** El area de destino: la de la ruta, o la elegida en los desplegables. */
  private readonly idArea = computed(() => this.id() || this.valores().idAreaElegida);

  protected readonly area = inject(AreaApi).detalle(this.idArea);
  protected readonly alta = inject(EquipoApi).registrar();

  // Los tres desplegables encadenados. Cada consulta espera a que la anterior tenga eleccion: sin el
  // `enabled` de los servicios se pediria /clients//headquarters con un identificador vacio.
  protected readonly clientes = inject(ClienteApi).listar(computed(() => !this.areaFijada()));
  protected readonly sedes = inject(SedeApi).listarDe(computed(() => this.valores().idCliente));
  protected readonly areas = inject(AreaApi).listarDe(computed(() => this.valores().idSede));

  private readonly modelos = this.catalogo.listarModelos();
  private readonly equipos = this.catalogo.listarEquipos();
  private readonly tipos = this.catalogo.listarTipos();
  private readonly marcas = this.catalogo.listarMarcas();
  private readonly fabricantes = this.catalogo.listarFabricantes();

  /** Si el panel de creacion de modelo esta abierto. */
  protected readonly creandoModelo = signal(false);

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

  /** Solo se ofrecen sedes y areas abiertas: el backend rechaza una referencia cerrada. */
  protected readonly sedesAbiertas = computed(() =>
    (this.sedes.data() ?? []).filter((sede) => sede.estadoActivo),
  );
  protected readonly areasAbiertas = computed(() =>
    (this.areas.data() ?? []).filter((area) => area.estadoActivo),
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

  /** Falta el destino cuando se entro por el listado y no se ha elegido area. */
  protected readonly sinDestino = computed(() => !this.areaFijada() && !this.valores().idAreaElegida);

  /** Al cambiar de cliente o de sede, lo elegido debajo deja de valer. */
  protected clienteCambio(): void {
    this.formulario.patchValue({ idSede: '', idAreaElegida: '' });
  }

  protected sedeCambio(): void {
    this.formulario.patchValue({ idAreaElegida: '' });
  }

  /** El modelo recien creado en el panel queda elegido: es lo que se vino a hacer. */
  protected usarModelo(idModelo: string): void {
    this.formulario.patchValue({ idModelo });
    this.creandoModelo.set(false);
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.sinDestino()) {
      this.formulario.markAllAsTouched();

      return;
    }

    const datos = this.formulario.getRawValue();

    // Los opcionales vacios no viajan: una cadena vacia no es un numero de inventario y una fecha
    // vacia no es una fecha de compra.
    this.alta.mutate(
      {
        idArea: this.idArea(),
        equipo: {
          idModelo: datos.idModelo,
          serie: datos.serie,
          ...(datos.numeroInventario ? { numeroInventario: datos.numeroInventario } : {}),
          ...(datos.fechaCompra ? { fechaCompra: datos.fechaCompra } : {}),
          ...(datos.valorCompra === null ? {} : { valorCompra: datos.valorCompra }),
        },
      },
      { onSuccess: () => void this.router.navigate(['/areas', this.idArea()]) },
    );
  }
}
