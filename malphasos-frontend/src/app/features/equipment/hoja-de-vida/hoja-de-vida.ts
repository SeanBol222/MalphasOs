import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { RouterLink } from '@angular/router';
import QRCode from 'qrcode';
import {
  ETIQUETA_DE_RESULTADO,
  ETIQUETA_DE_TIPO_DE_SERVICIO,
  ResultadoDeServicio,
  TipoDeServicio,
} from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';
import { EquipoApi } from '../equipo-api';

/** Lo que dice la hoja cuando un dato no esta registrado, o todavia no existe en el sistema. */
const SIN_DATO = '—';

/**
 * La hoja de vida de un equipo instalado, con el formato aprobado el 2026-10-05: el «tablero con
 * escudo», con la marca de Bolivar Bioingenieria. La misma pagina se lee en pantalla y se imprime en
 * dos hojas carta; el plan completo esta en la nota hoja-de-vida-formato-impreso del wiki.
 *
 * <p><b>Es un documento, no un formulario.</b> No tiene un solo control editable, y es la decision
 * tomada sobre RF-24: cada dato vive en su propio agregado y se corrige alli.
 *
 * <p><b>Existe desde que el equipo se registra.</b> Un equipo sin mantenimientos tiene su hoja de vida
 * con el historial vacio, y la pagina lo dice en vez de esconderlo.
 *
 * <p><b>Lo que el diseño pide y el sistema todavia no guarda sale con «—»</b>: el riesgo, el uso, los
 * datos electricos nuevos, el responsable, los contactos del cliente, el numero de hoja. Se iran
 * llenando tanda a tanda sin tocar esta forma. El estado, el ultimo servicio y el numero de
 * intervenciones no esperan a nadie: salen del historial que ya llega.
 */
@Component({
  selector: 'app-hoja-de-vida',
  imports: [RouterLink],
  templateUrl: './hoja-de-vida.html',
  styleUrl: './hoja-de-vida.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HojaDeVida {
  readonly id = input.required<string>();

  private readonly documento = inject(DOCUMENT);

  protected readonly sinDato = SIN_DATO;

  protected readonly hoja = inject(EquipoApi).hojaDeVida(this.id);

  protected readonly identificacion = computed(() => this.hoja.data()?.identificacion);
  protected readonly tecnica = computed(() => this.hoja.data()?.tecnica);
  protected readonly fabricante = computed(() => this.hoja.data()?.fabricante);

  /** «Balanza Beurer GS14»: como se nombra un equipo en voz alta, de lo general a lo concreto. */
  protected readonly nombreDelEquipo = computed(() => {
    const tecnica = this.tecnica();

    return [tecnica?.tipoEquipo, tecnica?.marca, tecnica?.modelo].filter(Boolean).join(' ');
  });

  protected readonly historial = computed(() =>
    (this.hoja.data()?.servicioTecnico ?? []).map((linea) => ({
      id: linea.id!,
      idReporte: linea.idReporteServicio,
      fecha: fechaCorta(linea.fechaServicio),
      servicio: linea.tipoServicio
        ? ETIQUETA_DE_TIPO_DE_SERVICIO[linea.tipoServicio as TipoDeServicio]
        : SIN_DATO,
      resultado: linea.resultado as ResultadoDeServicio | undefined,
      etiquetaDelResultado: linea.resultado
        ? ETIQUETA_DE_RESULTADO[linea.resultado as ResultadoDeServicio]
        : SIN_DATO,
    })),
  );

  /*
   * La fila de estado sale del historial, que el servidor manda de la intervencion mas reciente a la
   * mas antigua: la primera linea es el estado actual. Sin intervenciones, el equipo no tiene un
   * estado que se pueda afirmar, y se dice asi.
   */
  protected readonly ultimaIntervencion = computed(() => this.historial()[0]);
  protected readonly estadoActual = computed(
    () => this.ultimaIntervencion()?.etiquetaDelResultado ?? 'Sin servicios',
  );

  /** Las recomendaciones son un solo texto en el tipo; cada linea es un consejo. */
  protected readonly recomendaciones = computed(() =>
    (this.tecnica()?.recomendacionesCuidado ?? '')
      .split('\n')
      .map((linea) => linea.trim())
      .filter(Boolean),
  );

  protected readonly fechaDeCompra = computed(() => {
    const fecha = this.identificacion()?.fechaCompra;

    return fecha ? fechaCorta(fecha) : SIN_DATO;
  });

  /** El valor de compra en pesos, sin decimales: es lo que se escribe en una hoja de vida impresa. */
  protected readonly valorDeCompra = computed(() => {
    const valor = this.identificacion()?.valorCompra;

    return valor == null
      ? SIN_DATO
      : new Intl.NumberFormat('es-CO', {
          style: 'currency',
          currency: 'COP',
          maximumFractionDigits: 0,
        }).format(valor);
  });

  /** La fecha de actualizacion es la de impresion: decidido el 2026-10-05. */
  protected readonly fechaDeActualizacion = fechaCorta(hoy());

  /*
   * El QR lleva a esta misma hoja en la aplicacion, que pide iniciar sesion y respeta quien ve que:
   * decidido el 2026-10-05, sin enlace publico. Se dibuja como SVG desde la matriz de modulos y no
   * con canvas, para no depender de un API que el corredor de pruebas no tiene.
   */
  protected readonly qr = computed(() => {
    const direccion = `${this.documento.location.origin}/equipos/${this.id()}/hoja-de-vida`;
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

  /** Filas vacias al final del historial, para anotar a mano un servicio hecho fuera del sistema. */
  protected readonly filasEnBlanco = Array.from({ length: 6 });

  protected readonly hayError = computed(() => this.hoja.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.hoja.error()));

  protected imprimir(): void {
    window.print();
  }
}

/**
 * La fecha de hoy en la zona de quien imprime. No toISOString(): esa es la de Greenwich, y en Bogota
 * a partir de las siete de la noche ya es mañana.
 */
function hoy(): string {
  const ahora = new Date();
  const dos = (n: number) => String(n).padStart(2, '0');

  return `${ahora.getFullYear()}-${dos(ahora.getMonth() + 1)}-${dos(ahora.getDate())}`;
}

/** «12 mar 2026»: el dia, sin la hora, y con el mes en letras para que no se confunda dia y mes. */
function fechaCorta(fecha: string | undefined): string {
  if (!fecha) {
    return SIN_DATO;
  }

  // Solo la parte de la fecha: con hora y zona, un servicio cerrado de noche saldria al dia siguiente.
  const [anio, mes, dia] = fecha.slice(0, 10).split('-').map(Number);

  return new Intl.DateTimeFormat('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
    .format(new Date(anio, mes - 1, dia))
    .replace('.', '')
    .replace(/ de /g, ' ');
}
