import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CatalogoApi } from '../catalogo-api';
import { Sesion } from '../../../core/auth/sesion';
import { traducirError } from '../../../core/errores/traducir';
import { normalizar } from '../../../core/texto';
import { CabeceraDePieza } from './cabecera-de-pieza';
import { ColumnaOrdenable, Sentido, compararEn, invertido } from './columna-ordenable';

/**
 * Los modelos: un equipo del catalogo mas un fabricante, con su registro INVIMA.
 *
 * <p><b>Es el ultimo eslabon, y el unico que se puede instalar en un area.</b> Si no hay modelos, no
 * se puede registrar ningun equipo de cliente, y esa es la causa mas probable de que el formulario de
 * alta aparezca sin opciones.
 *
 * <p><b>El alta se recorre como se piensa, no como se guarda</b> (2026-11-19, pedido por el usuario):
 * primero el tipo, despues la marca —las que ya fabrican ese tipo arriba, el resto debajo— y al final
 * el modelo. La combinacion de tipo y marca, el equipo del catalogo, se busca y si no existe se crea al
 * guardar, igual que en el alta de un equipo: elegirla de una lista con todas las combinaciones no
 * servia en cuanto hubo cientos.
 *
 * <p>Cada fila se compone cruzando cuatro listas —modelos, equipos, tipos y marcas—, porque la
 * respuesta solo trae identificadores y un modelo legible es «tipo · marca · fabricante».
 */
@Component({
  selector: 'app-modelos',
  imports: [ReactiveFormsModule, RouterLink, CabeceraDePieza, ColumnaOrdenable],
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
  private readonly altaDeEquipo = this.api.crearEquipo();
  protected readonly baja = this.api.retirarModelo();

  protected readonly puedeEscribir = computed(() => this.sesion.puede('equipment.write'));

  /** El formulario del alta se abre con «Nuevo modelo» y se cierra al crear o al cancelar. */
  protected readonly abierto = signal(false);

  /** Mientras se encadenan las dos altas: la combinacion, si falta, y el modelo. */
  protected readonly creando = signal(false);
  private readonly fallo = signal<unknown>(null);

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    // El nombre es obligatorio y el INVIMA no, y la asimetria es del backend: un modelo sin registro
    // sanitario es un estado normal mientras se tramita, y un modelo sin nombre no es nada.
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    idTipo: ['', Validators.required],
    // Desactivada hasta que haya tipo: sin el, no se sabe cuales son las asociadas.
    idMarca: [{ value: '', disabled: true }, Validators.required],
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

  /** Lo escrito en la lupa de la cabecera. */
  protected readonly busqueda = signal('');

  /** El sentido del orden por nombre: de la A a la Z por defecto, y la cabecera lo invierte. */
  protected readonly sentido = signal<Sentido>('asc');

  protected invertirOrden(): void {
    this.sentido.update(invertido);
  }

  /**
   * Los modelos que casan con lo buscado, sin tildes ni mayusculas, en orden alfabetico. Cada palabra
   * puede caer en el nombre, el tipo, la marca, el fabricante o el INVIMA: «tensiometro omron»
   * encuentra los tensiometros de Omron aunque ninguno lo diga en el nombre.
   */
  protected readonly visibles = computed(() => {
    const palabras = normalizar(this.busqueda()).split(' ').filter(Boolean);

    return this.filas()
      .filter((fila) => {
        const texto = normalizar(
          [fila.nombre, fila.equipo, fila.fabricante, fila.invima].filter(Boolean).join(' '),
        );

        return palabras.every((palabra) => texto.includes(palabra));
      })
      .sort((a, b) => compararEn(this.sentido(), a.nombre, b.nombre));
  });

  private readonly idTipo = toSignal(this.formulario.controls.idTipo.valueChanges, {
    initialValue: '',
  });

  /** Solo piezas activas: el backend rechaza una referencia retirada. */
  protected readonly tiposActivos = computed(() =>
    (this.tipos.data() ?? [])
      .filter((t) => t.estadoActivo)
      .sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? '', 'es')),
  );

  /**
   * Las marcas activas partidas en dos: las que ya tienen una combinacion vigente con el tipo elegido
   * y las demas. Elegir una de las demas crea la combinacion al guardar.
   */
  protected readonly marcasDelTipo = computed(() => {
    const idTipo = this.idTipo();
    const asociadas = new Set(
      (this.equipos.data() ?? [])
        .filter((equipo) => equipo.estadoActivo && equipo.idTipoEquipo === idTipo)
        .map((equipo) => equipo.idMarca),
    );
    const activas = (this.marcas.data() ?? [])
      .filter((m) => m.estadoActivo)
      .sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? '', 'es'));

    return {
      asociadas: activas.filter((m) => asociadas.has(m.id)),
      otras: activas.filter((m) => !asociadas.has(m.id)),
    };
  });
  protected readonly fabricantesActivos = computed(() =>
    (this.fabricantes.data() ?? []).filter((f) => f.estadoActivo),
  );

  protected readonly hayError = computed(
    () => this.modelos.isError() || !!this.fallo() || this.baja.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(this.modelos.error() ?? this.fallo() ?? this.baja.error()),
  );

  constructor() {
    // Otra marca puede no fabricar el tipo nuevo: la elegida se suelta al cambiar de tipo, y sin tipo
    // la marca no se puede elegir.
    this.formulario.controls.idTipo.valueChanges.pipe(takeUntilDestroyed()).subscribe((idTipo) => {
      const marca = this.formulario.controls.idMarca;
      marca.reset();
      if (idTipo) {
        marca.enable({ emitEvent: false });
      } else {
        marca.disable({ emitEvent: false });
      }
    });
  }

  protected abrir(): void {
    this.formulario.reset();
    this.fallo.set(null);
    this.abierto.set(true);
  }

  protected cancelar(): void {
    this.formulario.reset();
    this.abierto.set(false);
  }

  /**
   * Busca la combinacion de tipo y marca y la crea si no existe, y despues el modelo. Van en serie
   * porque el modelo necesita el identificador de la combinacion.
   */
  protected async agregar(): Promise<void> {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    const { nombre, idTipo, idMarca, idFabricante, invima } = this.formulario.getRawValue();

    this.creando.set(true);
    this.fallo.set(null);

    try {
      // La combinacion existente se reutiliza: otra igual dejaria dos entradas identicas en el
      // catalogo y ningun modo de saber cual usar.
      const existente = (this.equipos.data() ?? []).find(
        (equipo) =>
          equipo.idTipoEquipo === idTipo && equipo.idMarca === idMarca && equipo.estadoActivo,
      );
      const idEquipo =
        existente?.id ??
        (await this.altaDeEquipo.mutateAsync({ idTipoEquipo: idTipo, idMarca })).id!;

      // El INVIMA es opcional y una cadena vacia no es un registro sanitario: no se manda.
      await this.alta.mutateAsync(
        invima ? { nombre, idEquipo, idFabricante, invima } : { nombre, idEquipo, idFabricante },
      );

      this.cancelar();
    } catch (fallo) {
      this.fallo.set(fallo);
    } finally {
      this.creando.set(false);
    }
  }

  protected retirar(id: string): void {
    this.baja.mutate(id);
  }
}
