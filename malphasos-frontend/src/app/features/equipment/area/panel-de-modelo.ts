import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { map } from 'rxjs';
import { CatalogoApi } from '../catalogo-api';
import { UbicacionApi } from '../../location/ubicacion-api';
import { traducirError } from '../../../core/errores/traducir';
import { Buscador, opcionesDe } from '../../../shared/buscador/buscador';

/**
 * Crea un modelo sin salir del formulario de alta de un equipo, con sus piezas si hacen falta.
 *
 * <p><b>Existe porque la cadena del catalogo tiene cinco eslabones y el formulario de un equipo solo
 * ofrece el ultimo.</b> Quien registra un equipo que llego hoy no deberia tener que abandonar lo que
 * lleva escrito, ir al catalogo, crear una marca, un tipo, su combinacion y un fabricante, y volver a
 * empezar. Aqui se crea lo que falte y el modelo queda elegido solo.
 *
 * <p><b>El equipo del catalogo no se pregunta: se deduce.</b> Si ya existe la combinacion del tipo con
 * la marca, se reutiliza; si no, se crea. Preguntarlo obligaria a explicar un concepto intermedio que a
 * quien rellena esto no le dice nada, y duplicar combinaciones seria peor.
 *
 * <p><b>Lo que se crea, queda creado.</b> Son cinco llamadas y el backend no las envuelve en ninguna
 * transaccion: si falla la ultima, la marca nueva ya existe. Se dice en pantalla en vez de dejar que
 * alguien lo descubra creando la misma marca tres veces.
 */
@Component({
  selector: 'app-panel-de-modelo',
  imports: [ReactiveFormsModule, Buscador],
  templateUrl: './panel-de-modelo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelDeModelo {
  /** El identificador del modelo recien creado, para que el formulario padre lo seleccione. */
  readonly creado = output<string>();

  /** Se pide cerrar el panel sin crear nada. */
  readonly cancelado = output<void>();

  private readonly api = inject(CatalogoApi);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly tipos = this.api.listarTipos();
  protected readonly marcas = this.api.listarMarcas();
  protected readonly fabricantes = this.api.listarFabricantes();
  private readonly paises = inject(UbicacionApi).listarPaises();

  protected readonly opcionesDePais = computed(() => opcionesDe(this.paises.data()));
  private readonly equipos = this.api.listarEquipos();

  private readonly altaDeMarca = this.api.crearMarca();
  private readonly altaDeTipo = this.api.crearTipo();
  private readonly altaDeFabricante = this.api.crearFabricante();
  private readonly altaDeEquipo = this.api.crearEquipo();
  private readonly altaDeModelo = this.api.crearModelo();

  /** El valor que marca «voy a crear una pieza nueva» en cada desplegable. */
  protected static readonly NUEVO = 'nuevo';
  protected readonly NUEVO = PanelDeModelo.NUEVO;

  protected readonly formulario = this.formBuilder.nonNullable.group({
    idTipo: ['', Validators.required],
    nombreDeTipo: [''],
    tecnologiaDeTipo: [''],
    definicionDeTipo: [''],
    cuidadoDeTipo: [''],
    idMarca: ['', Validators.required],
    nombreDeMarca: [''],
    idFabricante: ['', Validators.required],
    nombreDeFabricante: [''],
    paisDeFabricante: [''],
    // Obligatorio, al contrario que el INVIMA: un modelo sin nombre no es nada, y uno sin registro
    // sanitario es un estado normal mientras se tramita.
    nombreDeModelo: ['', [Validators.required, Validators.maxLength(50)]],
    invima: ['', Validators.maxLength(50)],
  });

  /**
   * Los valores del formulario, como senal.
   *
   * <p><b>Hace falta y no es ceremonia:</b> los formularios reactivos no son senales, de modo que un
   * {@code computed} que lea {@code getRawValue()} no vuelve a calcularse nunca —no tiene de que
   * depender— y con {@code OnPush} se queda con el primer valor para siempre. Los metodos llamados
   * desde la plantilla si se reevaluan en cada ciclo; los calculados, no.
   */
  private readonly valores = toSignal(
    this.formulario.valueChanges.pipe(map(() => this.formulario.getRawValue())),
    { initialValue: this.formulario.getRawValue() },
  );

  protected readonly creando = signal(false);
  protected readonly fallo = signal<unknown>(null);
  protected readonly mensajeDeError = computed(() =>
    this.fallo() === null ? '' : traducirError(this.fallo()),
  );

  /** Que piezas se van a crear, para avisar de que quedan creadas aunque el modelo falle. */
  protected readonly piezasNuevas = computed(() => {
    const valores = this.valores();

    return [
      valores.idTipo === PanelDeModelo.NUEVO ? 'un tipo' : '',
      valores.idMarca === PanelDeModelo.NUEVO ? 'una marca' : '',
      valores.idFabricante === PanelDeModelo.NUEVO ? 'un fabricante' : '',
    ].filter(Boolean);
  });

  protected readonly tiposActivos = computed(() =>
    (this.tipos.data() ?? []).filter((t) => t.estadoActivo),
  );
  protected readonly marcasActivas = computed(() =>
    (this.marcas.data() ?? []).filter((m) => m.estadoActivo),
  );
  protected readonly fabricantesActivos = computed(() =>
    (this.fabricantes.data() ?? []).filter((f) => f.estadoActivo),
  );

  protected creandoTipo(): boolean {
    return this.formulario.getRawValue().idTipo === PanelDeModelo.NUEVO;
  }

  protected creandoMarca(): boolean {
    return this.formulario.getRawValue().idMarca === PanelDeModelo.NUEVO;
  }

  protected creandoFabricante(): boolean {
    return this.formulario.getRawValue().idFabricante === PanelDeModelo.NUEVO;
  }

  /** Lo que falta por rellenar segun lo que se este creando, dicho antes de intentar guardar. */
  protected readonly incompleto = computed(() => {
    const v = this.valores();

    if (!v.idTipo || !v.idMarca || !v.idFabricante) {
      return true;
    }

    if (
      v.idTipo === PanelDeModelo.NUEVO &&
      !(v.nombreDeTipo && v.tecnologiaDeTipo && v.definicionDeTipo && v.cuidadoDeTipo)
    ) {
      return true;
    }

    if (v.idMarca === PanelDeModelo.NUEVO && !v.nombreDeMarca) {
      return true;
    }

    return v.idFabricante === PanelDeModelo.NUEVO && !v.nombreDeFabricante;
  });

  /**
   * Crea lo que falte y devuelve el modelo.
   *
   * <p>El orden no es casual: primero las piezas sueltas —tipo, marca, fabricante—, despues la
   * combinacion de tipo y marca, y el modelo al final. Cada paso necesita el identificador del
   * anterior, asi que van en serie y no en paralelo.
   */
  protected async crear(): Promise<void> {
    this.formulario.markAllAsTouched();

    if (this.incompleto()) {
      return;
    }

    const v = this.formulario.getRawValue();

    this.creando.set(true);
    this.fallo.set(null);

    try {
      const idTipo = this.creandoTipo()
        ? (
            await this.altaDeTipo.mutateAsync({
              nombre: v.nombreDeTipo,
              tecnologiaPredominante: v.tecnologiaDeTipo,
              definicionTecnica: v.definicionDeTipo,
              recomendacionesCuidado: v.cuidadoDeTipo,
            })
          ).id!
        : v.idTipo;

      const idMarca = this.creandoMarca()
        ? (await this.altaDeMarca.mutateAsync({ nombre: v.nombreDeMarca })).id!
        : v.idMarca;

      const idFabricante = this.creandoFabricante()
        ? (
            await this.altaDeFabricante.mutateAsync(
              v.paisDeFabricante
                ? { nombre: v.nombreDeFabricante, idPais: v.paisDeFabricante }
                : { nombre: v.nombreDeFabricante },
            )
          ).id!
        : v.idFabricante;

      // La combinacion existente se reutiliza. Crear otra igual dejaria dos entradas identicas en el
      // catalogo y ningun modo de saber cual usar.
      const existente = (this.equipos.data() ?? []).find(
        (equipo) =>
          equipo.idTipoEquipo === idTipo && equipo.idMarca === idMarca && equipo.estadoActivo,
      );
      const idEquipo =
        existente?.id ?? (await this.altaDeEquipo.mutateAsync({ idTipoEquipo: idTipo, idMarca })).id!;

      const modelo = await this.altaDeModelo.mutateAsync(
        v.invima
          ? { nombre: v.nombreDeModelo, idEquipo, idFabricante, invima: v.invima }
          : { nombre: v.nombreDeModelo, idEquipo, idFabricante },
      );

      this.creado.emit(modelo.id!);
    } catch (fallo) {
      this.fallo.set(fallo);
    } finally {
      this.creando.set(false);
    }
  }
}
