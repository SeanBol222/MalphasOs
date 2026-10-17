import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { RouterLink } from '@angular/router';
import QRCode from 'qrcode';
import { ReporteApi } from '../reporte-api';
import { EquipoApi } from '../../equipment/equipo-api';
import { OrdenApi } from '../../workOrder/orden-api';
import { nombreCompleto, PersonaApi } from '../../person/persona-api';
import { tipoDelEquipo } from '../tipo-del-equipo';
import {
  ETIQUETA_DE_ESTADO_AL_INGRESO,
  ETIQUETA_DE_RIESGO,
  EstadoAlIngreso,
  PatronUsado,
  Periodicidad,
  ResultadoDeServicio,
  TipoDeServicio,
  VerificacionAlIngreso,
} from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';
import { fechaCorta } from '../../../core/fechas';
import { conSigno, graficaDeError, LIENZO } from './grafica-de-error';

const NUMERO = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 4 });

/** Lo que dice el documento cuando un dato no está registrado. */
const SIN_DATO = '—';

/** Cuántos meses hay hasta el próximo servicio, según la periodicidad de la orden. */
const MESES: Readonly<Record<Periodicidad, number>> = {
  MENSUAL: 1,
  TRIMESTRAL: 3,
  SEMESTRAL: 6,
  ANUAL: 12,
};

/**
 * El resultado como lo dice un informe entregado: apto o no apto para el servicio (2026-10-16). Se
 * guardan tres valores y se imprimen dos; la restricción de un equipo que queda operativo con ella va
 * en las observaciones, que es donde el reporte la pide.
 */
const APTITUD: Readonly<Record<ResultadoDeServicio, string>> = {
  OPERATIVO: 'Apto para el servicio',
  OPERATIVO_CON_RESTRICCIONES: 'Apto para el servicio',
  FUERA_DE_SERVICIO: 'No apto para el servicio',
};

/**
 * El día del próximo servicio: el del servicio más la periodicidad de la orden, en meses de calendario.
 * El 31 de agosto más seis meses es el último día de febrero, no el 3 de marzo: se recorta al fin de mes.
 * Se calcula con las tres cifras de la fecha y no con {@code Date} en UTC, por la trampa de la zona
 * horaria que ya mordió a la hoja de vida.
 */
export function proximoServicio(fecha: string | undefined, periodicidad: Periodicidad | undefined) {
  if (!fecha || !periodicidad) {
    return undefined;
  }

  const [anio, mes, dia] = fecha.slice(0, 10).split('-').map(Number);
  const total = anio * 12 + (mes - 1) + MESES[periodicidad];
  const nuevoAnio = Math.floor(total / 12);
  const nuevoMes = (total % 12) + 1;
  const ultimoDia = new Date(nuevoAnio, nuevoMes, 0).getDate();
  const dos = (n: number) => String(n).padStart(2, '0');

  return `${nuevoAnio}-${dos(nuevoMes)}-${dos(Math.min(dia, ultimoDia))}`;
}

/**
 * El reporte de servicio como documento (2026-10-16): el formato «C · tablero con escudo» que el
 * usuario eligió, sobre lo que los informes reales de la empresa llevan. Se lee en pantalla y se
 * imprime en hojas carta; el PDF sale del navegador al imprimir —decidido: el PDF del servidor espera
 * a la firma digital—.
 *
 * <p><b>Es un documento, no un formulario</b>: no tiene un solo control. Lo compone el navegador con
 * lo que ya existe —el reporte, su orden y la hoja de vida del equipo—, sin un recurso nuevo en el
 * servidor. La hoja de vida trae ya compilados el cliente, el equipo, su ficha técnica, el responsable
 * del área y el membrete de la empresa.
 *
 * <p><b>Lo que no se guarda, se deriva</b>, decidido por el usuario: el supervisor responsable es el
 * encargado del área, como en la hoja de vida; el próximo servicio, la fecha más la periodicidad de la
 * orden; y la casilla de verificación metrológica se marca sola si el reporte tiene lecturas.
 *
 * <p><b>Es un solo informe</b>, decidido por el usuario el 2026-10-16: los formatos MP0, MP1 y MP2
 * eran del sistema antiguo. El título es siempre «Informe de mantenimiento», el tipo de servicio lo
 * dicen las casillas, y cada sección sale solo si tiene datos: la página 2 —la tabla de lecturas, su
 * gráfica de error y la trazabilidad de los patrones— existe si el reporte tiene lecturas.
 */
@Component({
  selector: 'app-documento-reporte',
  imports: [RouterLink],
  templateUrl: './documento-reporte.html',
  styleUrl: './documento-reporte.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentoReporte {
  readonly id = input.required<string>();

  private readonly documento = inject(DOCUMENT);

  protected readonly sinDato = SIN_DATO;

  protected readonly reporte = inject(ReporteApi).detalle(this.id);
  private readonly orden = inject(OrdenApi).detalle(
    computed(() => this.reporte.data()?.idOrdenTrabajo ?? ''),
  );
  protected readonly hoja = inject(EquipoApi).hojaDeVida(
    computed(() => this.reporte.data()?.idEquipoCliente ?? ''),
  );
  private readonly personas = inject(PersonaApi).listar();
  private readonly tipo = tipoDelEquipo(() => this.reporte.data()?.idEquipoCliente).tipo;

  protected readonly titulo = 'Informe de mantenimiento';

  protected readonly cargando = computed(
    () => this.reporte.isPending() || this.orden.isPending() || this.hoja.isPending(),
  );
  protected readonly hayError = computed(
    () => this.reporte.isError() || this.orden.isError() || this.hoja.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(this.reporte.error() ?? this.orden.error() ?? this.hoja.error()),
  );

  protected readonly datos = computed(() => this.reporte.data());
  protected readonly identificacion = computed(() => this.hoja.data()?.identificacion);
  protected readonly tecnica = computed(() => this.hoja.data()?.tecnica);
  protected readonly empresa = computed(() => this.hoja.data()?.empresa);

  protected readonly esBorrador = computed(() => this.datos()?.estado !== 'FINALIZADO');

  /** Sin lecturas no hay nada que verificar en la página 2, y el informe cabe en una hoja. */
  protected readonly paginas = computed(() => (this.datos()?.lecturas?.length ? 2 : 1));

  protected readonly tipoDeServicio = computed(
    () => this.orden.data()?.tipoServicio as TipoDeServicio | undefined,
  );
  /**
   * Las cinco del informe: los tres tipos de orden, la verificación, que se marca sola, y la garantía,
   * que será una marca del correctivo en la orden y hoy no se registra todavía.
   */
  protected readonly casillas = computed(() => {
    const tipo = this.tipoDeServicio();

    return [
      { texto: 'Preventivo', marcada: tipo === 'PREVENTIVO' },
      { texto: 'Correctivo', marcada: tipo === 'CORRECTIVO' },
      { texto: 'Calibración', marcada: tipo === 'CALIBRACION' },
      { texto: 'Verificación metrológica', marcada: !!this.datos()?.lecturas?.length },
      { texto: 'Garantía', marcada: false },
    ];
  });

  /** «Termohigrómetro · Genérico Digital · Serie T-9 · HV-CDN-0014». */
  protected readonly lineaDelEquipo = computed(() => {
    const tecnica = this.tecnica();
    const identificacion = this.identificacion();

    return [
      tecnica?.tipoEquipo,
      [tecnica?.marca, tecnica?.modelo].filter(Boolean).join(' '),
      identificacion?.serie ? `Serie ${identificacion.serie}` : '',
      identificacion?.numeroHojaVida,
    ]
      .filter(Boolean)
      .join(' · ');
  });

  /**
   * La fecha del servicio es la del cierre, que es cuando se prestó de verdad. Un borrador no tiene
   * cierre todavía: lleva la fecha programada de la orden.
   */
  protected readonly fechaDelServicio = computed(
    () => this.datos()?.finalizado?.slice(0, 10) ?? this.orden.data()?.fechaMantenimiento,
  );
  protected readonly proximo = computed(() =>
    proximoServicio(
      this.fechaDelServicio(),
      this.orden.data()?.periodicidad as Periodicidad | undefined,
    ),
  );
  protected readonly fechaCorta = fechaCorta;

  protected readonly resultado = computed(() => {
    const resultado = this.datos()?.resultado as ResultadoDeServicio | undefined;

    return resultado ? APTITUD[resultado] : 'Sin registrar';
  });

  /**
   * «4 de 5 conformes». Solo cuentan las lecturas con tolerancia: sin ella no se sabe, y no se
   * inventa. Sin lecturas, la casilla dice que no hubo verificación.
   */
  protected readonly verificacion = computed(() => {
    const lecturas = this.datos()?.lecturas ?? [];
    const juzgadas = lecturas.filter((lectura) => lectura.conforme != null);

    if (!lecturas.length) {
      return 'Sin verificación';
    }
    if (!juzgadas.length) {
      return `${lecturas.length} ${lecturas.length === 1 ? 'lectura' : 'lecturas'}`;
    }

    return `${juzgadas.filter((lectura) => lectura.conforme).length} de ${juzgadas.length} conformes`;
  });

  protected readonly supervisor = computed(() => unidos(this.identificacion()?.responsables));
  protected readonly telefonosDelCliente = computed(() =>
    unidos(this.identificacion()?.telefonosCliente),
  );
  protected readonly correosDelCliente = computed(() =>
    unidos(this.identificacion()?.correosCliente),
  );

  protected readonly riesgo = computed(() => {
    const clase = this.tecnica()?.fichaTecnica?.riesgo;

    return clase ? ETIQUETA_DE_RIESGO[clase] : SIN_DATO;
  });

  /** «AC · 110 V · 60 Hz»: lo que la ficha técnica del modelo diga, en ese orden. */
  protected readonly alimentacion = computed(() => {
    const ficha = this.tecnica()?.fichaTecnica;
    const partes = [
      ficha?.alimentacion,
      ficha?.voltaje != null ? `${ficha.voltaje} V` : '',
      ficha?.potencia != null ? `${ficha.potencia} W` : '',
      ficha?.amperaje != null ? `${ficha.amperaje.toLocaleString('es-CO')} A` : '',
      ficha?.frecuencia != null ? `${ficha.frecuencia} Hz` : '',
    ].filter(Boolean);

    return partes.length ? partes.join(' · ') : SIN_DATO;
  });

  /** Las dos filas fijas y los accesorios, con las palabras del informe impreso. */
  protected readonly ingreso = computed(() => {
    const datos = this.datos();
    const fila = (texto: string, verificacion: VerificacionAlIngreso | undefined) => ({
      texto,
      estado: verificacion?.estado
        ? ETIQUETA_DE_ESTADO_AL_INGRESO[verificacion.estado as EstadoAlIngreso]
        : 'Sin registrar',
      observacion: verificacion?.observacion || SIN_DATO,
    });

    return [
      fila('Estado de la estructura física del equipo', datos?.ingresoFisico),
      fila('Estado operativo del equipo', datos?.ingresoOperativo),
    ];
  });

  protected readonly accesorios = computed(() =>
    (this.datos()?.accesorios ?? []).map((accesorio) => ({
      texto: accesorio.nombre,
      estado: accesorio.conforme ? 'Conforme' : 'No conforme',
      observacion: accesorio.observacion || SIN_DATO,
    })),
  );

  /** Los pasos del protocolo: ✓ hecho, «N/A» con su nota si no aplicó, en blanco si quedó pendiente. */
  protected readonly pasos = computed(() =>
    (this.datos()?.pasos ?? []).map((paso) => ({
      marca: paso.estado === 'HECHO' ? '✓' : paso.estado === 'NO_APLICA' ? 'N/A' : '',
      nombre: paso.nombre,
      nota: paso.estado === 'NO_APLICA' ? paso.nota : undefined,
    })),
  );

  /** Lo que se escribió a mano, en el orden en que pasó. Solo lo que tiene texto. */
  protected readonly relato = computed(() => {
    const datos = this.datos();

    return [
      { titulo: 'Falla reportada', texto: datos?.fallaReportada },
      { titulo: 'Diagnóstico', texto: datos?.diagnostico },
      { titulo: 'Causa', texto: datos?.causa },
      {
        titulo: this.pasos().length ? 'Trabajo adicional' : 'Procedimientos',
        texto: datos?.procedimientos,
      },
      { titulo: 'Repuestos', texto: datos?.repuestos },
    ].filter((parte) => !!parte.texto);
  });

  /**
   * El ingeniero de la orden. Un reporte cerrado lo tiene congelado en su línea de la hoja de vida;
   * uno en borrador, no todavía, y se busca entre las personas.
   */
  protected readonly ingeniero = computed(() => {
    const linea = (this.hoja.data()?.servicioTecnico ?? []).find(
      (intervencion) => intervencion.idReporteServicio === this.id(),
    );
    if (linea?.responsable) {
      return linea.responsable;
    }

    const id = this.orden.data()?.idIngeniero;
    const persona = (this.personas.data() ?? []).find((p) => p.identificador === id);

    return persona ? nombreCompleto(persona) : SIN_DATO;
  });

  protected readonly recibe = computed(() => {
    const recibe = this.datos()?.recibe;

    return {
      nombre: [recibe?.nombre, recibe?.cedula ? `C.C. ${recibe.cedula}` : '']
        .filter(Boolean)
        .join(' · '),
      cargo: recibe?.cargo ? `Recibe · ${recibe.cargo}` : 'Recibe',
    };
  });

  protected readonly pie = computed(() =>
    [
      this.empresa()?.codigoInforme,
      [this.empresa()?.direccion, this.empresa()?.ciudad].filter(Boolean).join(', '),
      this.empresa()?.movil,
    ]
      .filter(Boolean)
      .join(' · '),
  );

  /**
   * El QR lleva a este mismo documento en la aplicación, que pide iniciar sesión y respeta quién ve
   * qué, como el de la hoja de vida. SVG desde la matriz de módulos, sin canvas.
   */
  protected readonly qr = computed(() => {
    const direccion = `${this.documento.location.origin}/reportes/${this.id()}/documento`;
    const { modules } = QRCode.create(direccion, { errorCorrectionLevel: 'M' });
    let trazo = '';

    for (let fila = 0; fila < modules.size; fila++) {
      for (let columna = 0; columna < modules.size; columna++) {
        if (modules.get(fila, columna)) {
          trazo += `M${columna} ${fila}h1v1h-1z`;
        }
      }
    }

    return { direccion, lado: modules.size, trazo };
  });

  // --- La página 2: la verificación metrológica -------------------------------------------------

  protected readonly LIENZO = LIENZO;

  /**
   * Una sección por verificación medida, en el orden en que el tipo las declara: su tabla, su gráfica
   * y sus patrones. Las lecturas se ordenan por punto y número; sin punto, por número —X1, X2—, que es
   * como las nombran los informes de la empresa.
   *
   * <p>El error y la conformidad son los del servidor, y la tolerancia la que se copió en cada lectura:
   * si el tipo la cambió después, el documento no cambia de veredicto.
   */
  protected readonly verificaciones = computed(() => {
    const lecturas = this.datos()?.lecturas ?? [];
    const declaradas = this.tipo()?.verificaciones ?? [];
    const ids = [...new Set(lecturas.map((lectura) => lectura.idVerificacion!))].sort(
      (a, b) =>
        indiceOFinal(declaradas.findIndex((v) => v.id === a)) -
        indiceOFinal(declaradas.findIndex((v) => v.id === b)),
    );

    return ids.map((id) => {
      const declarada = declaradas.find((verificacion) => verificacion.id === id);
      const puntos = new Map(
        (declarada?.puntos ?? []).map((punto) => [punto.id, punto.valor ?? 0]),
      );
      const suyas = lecturas
        .filter((lectura) => lectura.idVerificacion === id)
        .sort(
          (a, b) =>
            (puntos.get(a.idPuntoVerificacion ?? '') ?? 0) -
              (puntos.get(b.idPuntoVerificacion ?? '') ?? 0) ||
            (a.secuencia ?? 0) - (b.secuencia ?? 0),
        );
      const unidad = declarada?.unidad ?? suyas[0]?.unidad ?? '';
      const tolerancia = suyas.find((lectura) => lectura.tolerancia != null)?.tolerancia ?? null;
      const variasPorPunto = (declarada?.cantidadDatos ?? 1) > 1;
      const juzgadas = suyas.filter((lectura) => lectura.conforme != null);

      return {
        id,
        magnitud: declarada?.magnitud ?? 'Verificación',
        unidad,
        tolerancia:
          tolerancia === null
            ? 'sin tolerancia'
            : `tolerancia ±${NUMERO.format(tolerancia)} ${unidad}`,
        filas: suyas.map((lectura) => ({
          registro: lectura.idPuntoVerificacion
            ? `${NUMERO.format(puntos.get(lectura.idPuntoVerificacion) ?? 0)} ${unidad}` +
              (variasPorPunto ? ` · ${lectura.secuencia}` : '')
            : `X${lectura.secuencia}`,
          patron: NUMERO.format(lectura.valorPatron ?? 0),
          equipo: NUMERO.format(lectura.valorEquipo ?? 0),
          error: conSigno(lectura.error ?? 0),
          conforme: lectura.conforme == null ? '—' : lectura.conforme ? 'Sí' : 'No',
          fuera: lectura.conforme === false,
        })),
        resumen: juzgadas.length
          ? `${juzgadas.filter((lectura) => lectura.conforme).length} de ${juzgadas.length} lecturas conformes`
          : `${suyas.length} lecturas, sin tolerancia con que juzgarlas`,
        grafica: graficaDeError(
          suyas.map((lectura) => ({
            error: lectura.error ?? 0,
            conforme: lectura.conforme ?? null,
          })),
          tolerancia,
        ),
        leyenda:
          tolerancia === null
            ? 'Error de cada lectura (equipo − patrón). Sin tolerancia declarada: no hay banda.'
            : `Error de cada lectura (equipo − patrón) · banda gris: tolerancia ±${NUMERO.format(tolerancia)} ${unidad} · en rojo, fuera de ella`,
        patrones: (this.datos()?.patrones ?? [])
          .filter((usado) => usado.idVerificacion === id)
          .map((usado) => usado.nombre)
          .join(' · '),
      };
    });
  });

  /**
   * La trazabilidad: cada patrón una vez, aunque se usara en dos verificaciones, con las magnitudes en
   * que se usó. Con la ficha que se copió al ponerlo en el reporte (V30), no la de hoy.
   */
  protected readonly trazabilidad = computed(() => {
    const declaradas = this.tipo()?.verificaciones ?? [];
    const porPatron = new Map<string, { patron: PatronUsado; magnitudes: string[] }>();

    for (const usado of this.datos()?.patrones ?? []) {
      const magnitud =
        declaradas.find((verificacion) => verificacion.id === usado.idVerificacion)?.magnitud ?? '';
      const ya = porPatron.get(usado.id!);
      if (ya) {
        ya.magnitudes.push(magnitud);
      } else {
        porPatron.set(usado.id!, { patron: usado, magnitudes: [magnitud] });
      }
    }

    return [...porPatron.values()].map(({ patron, magnitudes }) => ({
      instrumento: patron.nombre,
      detalle: [
        [patron.marca, patron.modelo].filter(Boolean).join(' '),
        magnitudes.filter(Boolean).join(' y '),
      ]
        .filter(Boolean)
        .join(' · '),
      serie: patron.serie,
      codigo: patron.codigoInterno ?? '',
      rango: patron.rango ?? SIN_DATO,
      resolucion: patron.resolucion ?? '',
      certificado: patron.certificado,
      calibrado: patron.fechaCalibracion ? fechaCorta(patron.fechaCalibracion) : SIN_DATO,
      vence: `vence ${fechaCorta(patron.vencimiento)}`,
      calibrador: patron.empresaCalibradora ?? SIN_DATO,
    }));
  });

  protected imprimir(): void {
    window.print();
  }
}

/** Lo que el tipo ya no declara va al final, no al principio: findIndex da −1. */
function indiceOFinal(indice: number): number {
  return indice < 0 ? Number.MAX_SAFE_INTEGER : indice;
}

function unidos(valores: readonly string[] | undefined): string {
  return valores?.length ? valores.join(' · ') : SIN_DATO;
}
