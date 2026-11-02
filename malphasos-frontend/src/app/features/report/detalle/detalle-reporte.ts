import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClienteApi } from '../../client/cliente-api';
import { SedeApi } from '../../client/sede-api';
import { EquipoApi } from '../../equipment/equipo-api';
import { OrdenApi } from '../../workOrder/orden-api';
import { ReporteApi } from '../reporte-api';
import { TablaDeVerificacion } from '../verificacion/tabla-de-verificacion';
import { RecepcionDelEquipo } from '../recepcion/recepcion-del-equipo';
import { EntregaDelEquipo, SupervisorQueRecibe } from '../entrega/entrega-del-equipo';
import { Sesion } from '../../../core/auth/sesion';
import {
  EstadoDePaso,
  ETIQUETA_DE_ESTADO_DE_REPORTE,
  ETIQUETA_DE_PERIODICIDAD,
  ETIQUETA_DE_RESULTADO,
  ETIQUETA_DE_TIPO_DE_SERVICIO,
  PasoDeReporte,
  algunPatronVencido,
  verificacionesSinPatron,
  RESULTADOS_DE_SERVICIO,
  sePuedeCerrar,
} from '../../../core/api/tipos';
import { fechaIso, HOY } from '../../../core/fechas';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/**
 * Un reporte de servicio: qué se encontró, qué se hizo y cómo quedó el equipo.
 *
 * <p><b>Ningún campo es obligatorio para guardar, y dos lo son para cerrar.</b> Es deliberado y es lo
 * que hace el backend: el reporte se abre vacío al llegar al equipo y se llena a trozos mientras se
 * trabaja. Procedimientos y resultado se exigen al cerrarlo, no al escribir.
 *
 * <p><b>Cerrar es una operación, no un campo.</b> No hay desplegable de estado: hay un botón que
 * aparece cuando se puede usar, igual que iniciar y ejecutar en una orden. Y va con confirmación,
 * porque desde ahí el reporte ya no cambia — corregirlo es sustituirlo, que es justo lo que dice el
 * aviso. Desde V50 «Sustituir» lo retira y abre el borrador del mismo equipo en un solo paso, y retirar
 * un borrador es quitar su equipo de la orden.
 *
 * <p><b>Los datos que vienen de la orden no se escriben: se muestran.</b> Cliente, sede, responsables y
 * tipo de servicio salen de la orden de trabajo, que es literalmente lo que RF-11 pide —«autocompletar
 * a partir de la información registrada en la orden»— y lo que RNF-07 exige al decir que lo
 * autocompletado no se pueda editar: aquí no hay control que editar, hay una lista de datos. La única
 * fuente sigue siendo la orden, de modo que no pueden discrepar.
 *
 * <p><b>La verificación metrológica va en su propio bloque</b> y no entre los cinco campos, porque no es
 * un campo: es una tabla que el tipo del equipo dicta y que se guarda por separado, con su propia
 * llamada. Ver {@link TablaDeVerificacion}.
 *
 * <p><b>Un campo que se deja vacío se borra</b>, y eso también es del backend: manda lo que el
 * formulario muestra, de modo que vaciar una casilla en pantalla vacía el dato. La alternativa
 * —distinguir «no lo toqué» de «lo borré»— exigiría comparar contra el original en cada tecla para
 * mandar solo lo cambiado, y en un formulario que muestra todo lo que hay no tiene sentido.
 */
@Component({
  selector: 'app-detalle-reporte',
  imports: [
    EntregaDelEquipo,
    ReactiveFormsModule,
    RecepcionDelEquipo,
    RouterLink,
    TablaDeVerificacion,
  ],
  templateUrl: './detalle-reporte.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleReporte {
  readonly id = input.required<string>();

  private readonly api = inject(ReporteApi);
  private readonly sesion = inject(Sesion);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly reporte = this.api.detalle(this.id);

  protected readonly guardado = this.api.llenar();
  protected readonly cierre = this.api.cerrar();
  protected readonly retiro = this.api.retirar();
  protected readonly sustitucion = this.api.sustituir();
  protected readonly marca = this.api.marcarPaso();

  // --- Los pasos del protocolo (RF-13, desde el 2026-10-10) ---------------------

  /** Los pasos del protocolo, en su orden. Vacia en un correctivo o en un reporte anterior a ellos. */
  protected readonly pasos = computed(() => this.reporte.data()?.pasos ?? []);

  /**
   * El paso al que se le esta escribiendo la nota de «no aplica», por su protocolo, y lo escrito. Desde V51
   * un paso se nombra por su protocolo: su posicion es la del catalogo, y un paso que el tipo ya no tiene
   * no la tiene.
   */
  protected readonly notaEnCurso = signal<{ idProtocolo: string; texto: string } | null>(null);

  /** Hecho y pendiente se alternan con la casilla; un «no aplica» se deshace con ella tambien. */
  protected alternarHecho(paso: PasoDeReporte): void {
    this.marcar(paso.idProtocolo!, paso.estado === 'HECHO' ? 'PENDIENTE' : 'HECHO');
  }

  protected empezarNoAplica(paso: PasoDeReporte): void {
    this.notaEnCurso.set({ idProtocolo: paso.idProtocolo!, texto: paso.nota ?? '' });
  }

  /**
   * «No aplica» sin nota no llega al servidor, que tambien lo rechazaria: saltarse un paso del
   * protocolo queda justificado, decidido por el usuario el 2026-10-10.
   */
  protected confirmarNoAplica(): void {
    const enCurso = this.notaEnCurso();

    if (!enCurso || !enCurso.texto.trim()) {
      return;
    }

    this.marcar(enCurso.idProtocolo, 'NO_APLICA', enCurso.texto.trim(), () =>
      this.notaEnCurso.set(null),
    );
  }

  private marcar(
    idProtocolo: string,
    estado: EstadoDePaso,
    nota?: string,
    alTerminar?: () => void,
  ): void {
    this.marca.mutate(
      { id: this.id(), idProtocolo, marca: { estado, ...(nota ? { nota } : {}) } },
      { onSuccess: () => alTerminar?.() },
    );
  }

  private readonly equipos = inject(EquipoApi).listarTodos();

  // --- Lo que se autocompleta desde la orden (RF-11) ---------------------------

  private readonly orden = inject(OrdenApi).detalle(
    computed(() => this.reporte.data()?.idOrdenTrabajo ?? ''),
  );
  private readonly clientes = inject(ClienteApi).listar();
  private readonly sedes = inject(SedeApi).variasPorId(
    computed(() => {
      const sede = this.orden.data()?.idSede;

      return sede ? [sede] : [];
    }),
  );

  protected readonly puedeEscribir = computed(() => this.sesion.puede('report.write'));

  protected readonly confirmandoCierre = signal(false);
  protected readonly confirmandoRetiro = signal(false);
  protected readonly confirmandoSustitucion = signal(false);

  protected readonly RESULTADOS = RESULTADOS_DE_SERVICIO;
  protected readonly etiquetaDeResultado = ETIQUETA_DE_RESULTADO;

  protected readonly formulario = this.fb.nonNullable.group({
    fallaReportada: '',
    diagnostico: '',
    procedimientos: '',
    observaciones: '',
    causa: '',
    repuestos: '',
    resultado: '',
  });

  /**
   * Si la orden todavía no ha empezado (V50): el borrador nace al añadir el equipo, y el servidor no deja
   * escribir en él hasta que la orden empiece, porque un reporte cuenta lo que se hizo.
   */
  protected readonly ordenSinEmpezar = computed(
    () => this.orden.data()?.estadoEjecucion === 'CREADA',
  );

  /** Mientras la orden no se ha ejecutado, un borrador se puede quitar de ella con su equipo. */
  protected readonly seQuitaDeLaOrden = computed(
    () => this.orden.data()?.estadoEjecucion !== 'EJECUTADA' && !!this.orden.data()?.estadoActivo,
  );

  /**
   * Un reporte cerrado o retirado no se toca, y el formulario lo refleja desactivándose. Tampoco uno de
   * una orden sin empezar o cancelada, que es lo que el servidor rechaza en cada escritura (V50).
   *
   * <p>Se desactiva en vez de esconderse porque lo escrito sigue siendo lo que hay que leer: un reporte
   * finalizado es el documento que se entregó.
   */
  protected readonly editable = computed(
    () =>
      this.puedeEscribir() &&
      this.reporte.data()?.estado === 'BORRADOR' &&
      !!this.reporte.data()?.estadoActivo &&
      !!this.orden.data()?.estadoActivo &&
      !this.ordenSinEmpezar(),
  );

  /**
   * Si es un correctivo, que lo dice la orden y no el reporte (2026-10-14). Cambia qué se pregunta:
   * aparece la causa, y los textos de ayuda hablan de la falla. La falla y el diagnóstico fueron
   * obligatorios para cerrar hasta el 2026-10-27, cuando el usuario los quitó.
   */
  protected readonly esCorrectivo = computed(
    () => this.orden.data()?.tipoServicio === 'CORRECTIVO',
  );

  private readonly hoy = fechaIso(inject(HOY)());

  protected readonly cerrable = computed(() => sePuedeCerrar(this.reporte.data(), this.hoy));

  /** Las lecturas guardadas que se salen de su tolerancia. Las que no tienen tolerancia no cuentan. */
  protected readonly noConformes = computed(
    () =>
      (this.reporte.data()?.lecturas ?? []).filter((lectura) => lectura.conforme === false).length,
  );

  /**
   * El requisito de los patrones, solo si hay lecturas guardadas: sin ellas no hay nada que comparar
   * contra ellos, y el servidor tampoco lo pide (2026-10-14). Desde V30, uno por verificación medida y
   * ninguno vencido.
   */
  private readonly requisitoDelPatron = computed(() => {
    const datos = this.reporte.data();

    if (!datos?.lecturas?.length) {
      return [];
    }

    return [
      {
        texto: 'Patrón vigente en cada verificación medida',
        hecho: !verificacionesSinPatron(datos).length && !algunPatronVencido(datos, this.hoy),
      },
    ];
  });

  /** Las dos verificaciones al ingreso: todo reporte las exige para cerrar desde el 2026-10-16. */
  private readonly requisitoDelIngreso = computed(() => {
    const datos = this.reporte.data();

    return {
      texto: 'Verificaciones al ingreso',
      hecho: !!datos?.ingresoFisico && !!datos?.ingresoOperativo,
    };
  });

  /**
   * Lo que el servidor exige para cerrar, con lo que ya esta guardado: es lo que pinta el panel de la
   * derecha. Se mira lo guardado y no lo escrito, igual que {@link cerrable}, porque cerrar opera
   * sobre lo que el servidor tiene; lo escrito sin guardar lo avisa el formulario aparte.
   *
   * <p>La verificacion no esta en la lista: si el tipo del equipo la tiene, la exige el servidor, y lo
   * que falta lo dice la propia tabla. Contarla aqui obligaria a duplicar su regla.
   */
  protected readonly requisitos = computed(() => {
    const datos = this.reporte.data();

    const pasos = datos?.pasos ?? [];

    // Con pasos, lo que se hizo lo dicen ellos y el texto es opcional; sin pasos, el texto. Es la misma
    // regla que sePuedeCerrar y que el servidor.
    if (pasos.length) {
      const marcados = pasos.filter((paso) => paso.estado !== 'PENDIENTE').length;

      return [
        this.requisitoDelIngreso(),
        {
          texto: `Protocolo: ${marcados} de ${pasos.length} pasos`,
          hecho: marcados === pasos.length,
        },
        { texto: 'Resultado', hecho: !!datos?.resultado },
        ...this.requisitoDelPatron(),
      ];
    }

    if (this.esCorrectivo()) {
      return [
        this.requisitoDelIngreso(),
        { texto: 'Trabajo realizado', hecho: !!datos?.procedimientos?.trim() },
        { texto: 'Resultado', hecho: !!datos?.resultado },
        ...this.requisitoDelPatron(),
      ];
    }

    return [
      this.requisitoDelIngreso(),
      { texto: 'Procedimientos', hecho: !!datos?.procedimientos?.trim() },
      { texto: 'Resultado', hecho: !!datos?.resultado },
      ...this.requisitoDelPatron(),
    ];
  });

  /**
   * Cuanto falta, en porcentaje. Con pasos, el protocolo cuenta por lo que va marcado y no solo cuando
   * esta completo: con dos de tres pasos marcados el panel decia 0 %, y eso no es lo que se ha hecho.
   */
  protected readonly avance = computed(() => {
    const pasos = this.pasos();
    const resultado = this.reporte.data()?.resultado ? 1 : 0;

    if (pasos.length) {
      const marcados = pasos.filter((paso) => paso.estado !== 'PENDIENTE').length;
      // El patron, si hace falta, cuenta como una parte mas: sin el, el panel diria 100 % de algo
      // que el servidor no deja cerrar.
      const partes = [
        this.requisitoDelIngreso().hecho ? 1 : 0,
        marcados / pasos.length,
        resultado,
        ...this.requisitoDelPatron().map((requisito) => (requisito.hecho ? 1 : 0)),
      ];

      return Math.round((partes.reduce((suma, parte) => suma + parte, 0) / partes.length) * 100);
    }

    const requisitos = this.requisitos();

    return Math.round((requisitos.filter((r) => r.hecho).length / requisitos.length) * 100);
  });

  /** Una linea que dice que significa cada resultado, debajo de su nombre. */
  protected readonly ayudaDeResultado: Readonly<Record<string, string>> = {
    OPERATIVO: 'Funciona sin limitaciones',
    OPERATIVO_CON_RESTRICCIONES: 'Funciona, con salvedades que van en observaciones',
    FUERA_DE_SERVICIO: 'No debe usarse',
  };

  protected readonly estado = computed(() => {
    const datos = this.reporte.data();

    if (!datos) {
      return '';
    }
    if (!datos.estadoActivo) {
      return 'Retirado';
    }

    return datos.estado ? ETIQUETA_DE_ESTADO_DE_REPORTE[datos.estado] : '';
  });

  /** La serie del equipo del que habla el reporte. El API devuelve el identificador y nada más. */
  protected readonly equipo = computed(() => {
    const id = this.reporte.data()?.idEquipoCliente;

    return (
      (this.equipos.data() ?? []).find((equipo) => equipo.id === id)?.serie ??
      'Equipo no disponible'
    );
  });

  /**
   * Los cinco datos que la orden aporta, resueltos a palabras.
   *
   * <p>Se resuelven aquí y no se copian en el reporte: la orden los congeló al crearse y este bloque los
   * lee. Cada nombre cuesta un cruce contra una lista, que es la ausencia ya anotada — ninguna respuesta
   * del API trae nombres.
   */
  protected readonly cliente = computed(() => {
    const id = this.orden.data()?.idCliente;

    return (
      (this.clientes.data() ?? []).find((cliente) => cliente.id === id)?.razonSocial ??
      'No disponible'
    );
  });

  protected readonly sede = computed(() => (this.sedes.data() ?? [])[0]?.nombre ?? 'No disponible');

  protected readonly servicio = computed(() => {
    const datos = this.orden.data();
    const tipo = datos?.tipoServicio ? ETIQUETA_DE_TIPO_DE_SERVICIO[datos.tipoServicio] : '';
    const cada = datos?.periodicidad ? ETIQUETA_DE_PERIODICIDAD[datos.periodicidad] : '';

    return cada ? `${tipo} · ${cada}` : tipo;
  });

  protected readonly fechaProgramada = computed(() => this.orden.data()?.fechaMantenimiento ?? '');

  /** El ingeniero que ejecuta. Sin él la orden está programada y no repartida, y el reporte lo dice. */
  protected readonly ingeniero = computed(() => {
    if (!this.orden.data()?.idIngeniero) {
      return '';
    }

    return this.orden.data()?.nombreIngeniero ?? 'No disponible';
  });

  /**
   * Quienes supervisan al cliente de la orden, por nombre: quien revisa y aprueba este servicio (V53,
   * 2026-11-01). Hasta entonces era el encargado de la sede, que salió con los encargados.
   */
  protected readonly supervisores = computed(() => {
    const idCliente = this.orden.data()?.idCliente;
    const cliente = (this.clientes.data() ?? []).find((c) => c.id === idCliente);

    return (cliente?.supervisores ?? [])
      .filter((supervisor) => !!supervisor.idPersona)
      .map((supervisor) => supervisor.nombre ?? 'No disponible')
      .join(' · ');
  });

  /** Los mismos, con su identificador y su cargo, para elegir quién recibe en la entrega (V55). */
  protected readonly supervisoresQueReciben = computed<SupervisorQueRecibe[]>(() => {
    const idCliente = this.orden.data()?.idCliente;
    const cliente = (this.clientes.data() ?? []).find((c) => c.id === idCliente);

    return (cliente?.supervisores ?? [])
      .filter((supervisor) => !!supervisor.idPersona)
      .map((supervisor) => ({
        id: supervisor.idPersona!,
        nombre: supervisor.nombre ?? 'No disponible',
        cargo: supervisor.cargo ?? null,
      }));
  });

  protected readonly hayError = computed(
    () =>
      this.reporte.isError() ||
      this.guardado.isError() ||
      this.cierre.isError() ||
      this.retiro.isError() ||
      this.sustitucion.isError() ||
      this.marca.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.reporte.error() ??
        this.guardado.error() ??
        this.cierre.error() ??
        this.retiro.error() ??
        this.sustitucion.error() ??
        this.marca.error(),
    ),
  );
  protected readonly detalles = computed(() =>
    detallesDe(this.guardado.error() ?? this.cierre.error()),
  );

  constructor() {
    /**
     * Vuelca en el formulario lo que el servidor tiene.
     *
     * <p>Solo mientras nadie haya escrito: sin la guarda, cada recarga de la caché —y hay una después de
     * cada guardado— pisaría lo que la persona está tecleando. Es el mismo problema que la edición de un
     * cliente, resuelto igual.
     */
    effect(() => {
      const datos = this.reporte.data();

      if (!datos || this.formulario.dirty) {
        return;
      }

      this.formulario.patchValue(
        {
          fallaReportada: datos.fallaReportada ?? '',
          diagnostico: datos.diagnostico ?? '',
          procedimientos: datos.procedimientos ?? '',
          observaciones: datos.observaciones ?? '',
          causa: datos.causa ?? '',
          repuestos: datos.repuestos ?? '',
          resultado: datos.resultado ?? '',
        },
        { emitEvent: false },
      );
    });

    // El formulario se desactiva cuando el reporte deja de ser un borrador. Un control desactivado no
    // se puede tocar ni con el teclado, que es lo que hace falta: esconderlo perderia el contenido.
    effect(() => {
      if (this.editable()) {
        this.formulario.enable({ emitEvent: false });
      } else {
        this.formulario.disable({ emitEvent: false });
      }
    });
  }

  protected guardar(): void {
    const valores = this.formulario.getRawValue();

    this.guardado.mutate(
      {
        id: this.id(),
        datos: {
          fallaReportada: valores.fallaReportada,
          diagnostico: valores.diagnostico,
          procedimientos: valores.procedimientos,
          observaciones: valores.observaciones,
          causa: valores.causa,
          repuestos: valores.repuestos,
          // Sin resultado se manda ausente, no vacio: la cadena vacia no es un valor del catalogo y el
          // servidor la rechazaria. Un resultado puesto por error se sustituye, no se quita.
          resultado: valores.resultado
            ? (valores.resultado as NonNullable<(typeof RESULTADOS_DE_SERVICIO)[number]>)
            : undefined,
        },
      },
      { onSuccess: () => this.formulario.markAsPristine() },
    );
  }

  protected cerrar(): void {
    this.cierre.mutate(this.id(), { onSuccess: () => this.confirmandoCierre.set(false) });
  }

  protected retirar(): void {
    const idOrden = this.reporte.data()?.idOrdenTrabajo;

    this.retiro.mutate(this.id(), {
      // Se vuelve a la orden: con el borrador, el equipo salio de ella (V50).
      onSuccess: () => void this.router.navigate(idOrden ? ['/ordenes', idOrden] : ['/ordenes']),
    });
  }

  /** Sustituye el reporte cerrado y lleva al borrador nuevo: lo que sigue es llenarlo. */
  protected sustituir(): void {
    this.sustitucion.mutate(this.id(), {
      onSuccess: (borrador) => {
        this.confirmandoSustitucion.set(false);
        void this.router.navigate(['/reportes', borrador.id]);
      },
    });
  }
}
