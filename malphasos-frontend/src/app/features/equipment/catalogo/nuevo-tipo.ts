import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CatalogoApi } from '../catalogo-api';
import { detallesDe, traducirError } from '../../../core/errores/traducir';
import { Buscador } from '../../../shared/buscador/buscador';
import { ConfiguracionDeVerificacion, Verificacion } from './verificacion';
import { ProtocolosDelTipo } from './protocolos-del-tipo';
import { AsignacionDeProtocolos } from '../../../core/api/tipos';
import { tecnologiasAutorizadas } from './tecnologias';
import { ConfiguracionDeModelos, MarcasYModelos, PiezaElegida } from './marcas-y-modelos';
import { normalizar } from '../../../core/texto';

/**
 * Alta de un tipo de equipo, la unica pieza del catalogo con ficha tecnica.
 *
 * <p>Cuatro campos son obligatorios porque el contrato los exige: nombre, definicion tecnica,
 * recomendaciones de cuidado y tecnologia predominante. Los demas —uso, limpieza cotidiana, valor de
 * mantenimiento y verificaciones— son opcionales, y se dice cuales lo son en vez de dejar que se
 * descubra al guardar. (Voltaje y amperaje estuvieron aqui hasta el 2026-10-05: bajaron al modelo.)
 *
 * <p>Las cotas salen del contrato: 50 caracteres para nombre y tecnologia, 250 para los dos textos
 * largos. Si el backend las cambia, se regeneran los tipos y esto queda desalineado a la vista.
 */
@Component({
  selector: 'app-nuevo-tipo',
  imports: [ReactiveFormsModule, RouterLink, Buscador, Verificacion, ProtocolosDelTipo, MarcasYModelos],
  templateUrl: './nuevo-tipo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuevoTipo {
  private readonly router = inject(Router);

  private readonly api = inject(CatalogoApi);

  protected readonly alta = this.api.crearTipo();
  protected readonly asignacion = this.api.asignarProtocolos();
  private readonly altaDeMarca = this.api.crearMarca();
  private readonly altaDeFabricante = this.api.crearFabricante();
  private readonly altaDeEquipo = this.api.crearEquipo();
  private readonly altaDeModelo = this.api.crearModelo();

  /**
   * Las marcas y los modelos que el tipo trae desde su alta, plegados por defecto (2026-11-19). El
   * bloque no se crea hasta que se abre —no pide marcas ni fabricantes si nadie lo usa— y una vez
   * creado se conserva al plegarlo, para no perder lo escrito.
   */
  protected readonly modelosAbiertos = signal(false);
  protected readonly modelosAbiertosAlgunaVez = signal(false);
  protected readonly modelos = signal<ConfiguracionDeModelos>({ modelos: [], valida: true });
  private readonly bloqueDeModelos = viewChild(MarcasYModelos);

  /** Mientras dura la cadena entera: el tipo, sus protocolos y sus modelos. */
  private readonly guardando = signal(false);

  /** Si un modelo falla, el tipo ya existe: se dice cuantos quedaron, como con los protocolos. */
  private readonly falloDeModelos = signal('');

  /** Los protocolos del preventivo, en orden. Se guardan despues del tipo, con el mismo boton. */
  protected readonly protocolos = signal<AsignacionDeProtocolos>({ protocolos: [] });

  /**
   * Las tecnologias autorizadas: las que esta empresa ya usa, y despues las de partida.
   *
   * <p><b>Lista cerrada</b>: no se admite una que no este. Sin eso, la misma tecnologia acaba escrita
   * de cuatro maneras y agrupar por ella deja de servir. Ver {@code tecnologias.ts}.
   */
  protected readonly tecnologias = computed(() => tecnologiasAutorizadas(this.tipos.data()));

  private readonly tipos = this.api.listarTipos();

  /**
   * Que se le verifica, tal como lo tiene el bloque de verificacion ahora mismo.
   *
   * <p>Fuera del formulario reactivo a proposito: sus campos se condicionan entre si -la cantidad y los
   * puntos solo existen con una modalidad constante, y la unidad depende de la magnitud- y expresarlo
   * con validadores dinamicos habria sido mas codigo y menos legible que un componente que se valida
   * solo. Desde el 2026-10-03 son varias verificaciones y no una, lo que refuerza la decision.
   */
  protected readonly verificacion = signal<ConfiguracionDeVerificacion>({
    verificaciones: [],
    valida: true,
  });

  private readonly bloqueDeVerificacion = viewChild(Verificacion);

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    tecnologiaPredominante: ['', [Validators.required, Validators.maxLength(50)]],
    definicionTecnica: ['', [Validators.required, Validators.maxLength(250)]],
    recomendacionesCuidado: ['', [Validators.required, Validators.maxLength(250)]],
    uso: ['', Validators.maxLength(250)],
    limpiezaCotidiana: ['', Validators.maxLength(250)],
    valorUnitarioMantenimiento: [null as number | null],
  });

  protected readonly enviando = computed(
    () => this.guardando() || this.alta.isPending() || this.asignacion.isPending(),
  );

  /**
   * Si fallan los protocolos el tipo ya existe: se dice asi, para que no se intente crearlo otra vez y
   * choque con su propio nombre. Son dos llamadas porque el API las separa, y no hay transaccion entre
   * ellas.
   */
  protected readonly mensajeDeError = computed(() => {
    if (this.alta.isError()) {
      return traducirError(this.alta.error());
    }
    if (this.asignacion.isError()) {
      return `El tipo se creó, pero sus protocolos no se guardaron: ${traducirError(this.asignacion.error())} Asígnelos desde su edición.`;
    }
    if (this.falloDeModelos()) {
      return this.falloDeModelos();
    }

    return '';
  });
  protected readonly detalles = computed(() => detallesDe(this.alta.error()));

  protected malo(campo: string): boolean {
    const control = this.formulario.get(campo);

    return !!control && control.invalid && control.touched;
  }

  protected alternarModelos(): void {
    this.modelosAbiertos.update((abiertos) => !abiertos);
    this.modelosAbiertosAlgunaVez.set(true);
  }

  protected async enviar(): Promise<void> {
    const verificacion = this.verificacion();

    if (this.formulario.invalid || !verificacion.valida || !this.modelos().valida) {
      this.formulario.markAllAsTouched();
      // Que los bloques pinten sus propios errores: hasta que se intenta guardar no se les reprocha nada.
      this.bloqueDeVerificacion()?.marcarIntento();
      this.bloqueDeModelos()?.marcarIntento();
      // Una fila a medias en una seccion plegada no se veria.
      if (!this.modelos().valida) {
        this.modelosAbiertos.set(true);
      }

      return;
    }

    const datos = this.formulario.getRawValue();

    this.guardando.set(true);
    this.falloDeModelos.set('');

    try {
      const creado = await this.alta.mutateAsync(this.cuerpo(datos, verificacion));

      if (!(await this.guardarProtocolos(creado.id!))) {
        return;
      }
      if (!(await this.guardarModelos(creado.id!))) {
        return;
      }

      void this.router.navigate(['/catalogo']);
    } catch {
      // El alta del tipo fallo: lo dice su propio estado de error, y no hay nada mas que deshacer.
    } finally {
      this.guardando.set(false);
    }
  }

  private cuerpo(
    datos: ReturnType<NuevoTipo['formulario']['getRawValue']>,
    verificacion: ConfiguracionDeVerificacion,
  ) {
    // Los opcionales que nadie relleno no viajan. Un cero no es lo mismo que "no se sabe", y mandar
    // null o cadena vacia haria que el backend guardara un dato inventado.
    return {
      nombre: datos.nombre,
      tecnologiaPredominante: datos.tecnologiaPredominante,
      definicionTecnica: datos.definicionTecnica,
      recomendacionesCuidado: datos.recomendacionesCuidado,
      // La lista entera o nada: una lista vacia significa que a este tipo no se le verifica nada,
      // y entonces ni se manda el campo.
      ...(verificacion.verificaciones.length
        ? { verificaciones: [...verificacion.verificaciones] }
        : {}),
      ...(datos.uso.trim() ? { uso: datos.uso.trim() } : {}),
      ...(datos.limpiezaCotidiana.trim()
        ? { limpiezaCotidiana: datos.limpiezaCotidiana.trim() }
        : {}),
      ...(datos.valorUnitarioMantenimiento === null
        ? {}
        : { valorUnitarioMantenimiento: datos.valorUnitarioMantenimiento }),
    };
  }

  /** Sin protocolos no hay nada que mandar: un tipo nuevo ya nace sin ninguno. */
  private async guardarProtocolos(id: string): Promise<boolean> {
    const asignacion = this.protocolos();

    if (!asignacion.protocolos?.length) {
      return true;
    }

    try {
      await this.asignacion.mutateAsync({ id, asignacion });

      return true;
    } catch {
      return false;
    }
  }

  /**
   * Cada modelo con su marca, su fabricante y su combinacion con el tipo, en serie: cada paso necesita
   * el identificador del anterior. Una marca o un fabricante nuevos que se repiten en dos filas se
   * crean una vez, y uno escrito con el nombre de uno que ya existe se reutiliza en vez de duplicarlo.
   */
  private async guardarModelos(idTipo: string): Promise<boolean> {
    const modelos = this.modelos().modelos;
    const marcas = new Map<string, string>();
    const fabricantes = new Map<string, string>();
    const combinaciones = new Map<string, string>();
    let creados = 0;

    try {
      for (const modelo of modelos) {
        const idMarca = await this.resolver(modelo.marca, marcas, (nombre) =>
          this.altaDeMarca.mutateAsync({ nombre }),
        );
        const idFabricante = await this.resolver(modelo.fabricante, fabricantes, (nombre) =>
          this.altaDeFabricante.mutateAsync({ nombre }),
        );
        // El tipo es nuevo: ninguna combinacion suya existe todavia, salvo la de una fila anterior.
        const idEquipo =
          combinaciones.get(idMarca) ??
          (await this.altaDeEquipo.mutateAsync({ idTipoEquipo: idTipo, idMarca })).id!;
        combinaciones.set(idMarca, idEquipo);

        await this.altaDeModelo.mutateAsync({
          nombre: modelo.nombre,
          idEquipo,
          idFabricante,
          ...(modelo.invima ? { invima: modelo.invima } : {}),
        });
        creados++;
      }

      return true;
    } catch (fallo) {
      this.falloDeModelos.set(
        `El tipo se creó, y ${creados} de ${modelos.length} modelos, pero el siguiente no se guardó: ${traducirError(fallo)} Agréguelo desde Catálogo › Modelos.`,
      );

      return false;
    }
  }

  /** El identificador de una pieza elegida, o de la creada con ese nombre si no existia. */
  private async resolver(
    pieza: PiezaElegida,
    creadas: Map<string, string>,
    crear: (nombre: string) => Promise<{ id?: string }>,
  ): Promise<string> {
    if ('id' in pieza) {
      return pieza.id;
    }

    const clave = normalizar(pieza.nombre);
    const existente = creadas.get(clave);
    if (existente) {
      return existente;
    }

    const id = (await crear(pieza.nombre)).id!;
    creadas.set(clave, id);

    return id;
  }
}
