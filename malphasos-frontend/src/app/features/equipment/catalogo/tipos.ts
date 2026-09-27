import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogoApi } from '../catalogo-api';
import { Sesion } from '../../../core/auth/sesion';
import { ETIQUETA_DE_MODALIDAD, ModalidadDeVerificacion, TipoDeEquipo } from '../../../core/api/tipos';
import { ConfiguracionDeVerificacion, Verificacion } from './verificacion';
import { traducirError } from '../../../core/errores/traducir';

/**
 * Los tipos de equipo: que clase de aparato es, con su ficha tecnica.
 *
 * <p>Es la unica pieza del catalogo que no cabe en una linea —definicion tecnica, recomendaciones de
 * cuidado, tecnologia, voltaje, amperaje, valor de mantenimiento—, asi que su alta y su edicion tienen
 * pagina propia y aqui solo se listan.
 *
 * <p><b>La modalidad de verificacion si se cambia desde aqui</b>, porque el backend le dio ruta propia
 * y no la incluyo en la edicion general: decide como se verifica el equipo, y cambiarla es una
 * decision, no rellenar un campo.
 *
 * <p>Y con ella viajan <b>cuantas lecturas se toman y en que valores</b>, desde el 2026-09-27: son los
 * datos que hacen falta para llenar el reporte, y el backend los recibe juntos porque por separado
 * existiria el instante en que un tipo dice verificarse contra un patron constante sin decir contra
 * que valor.
 */
@Component({
  selector: 'app-tipos-de-equipo',
  imports: [RouterLink, Verificacion],
  templateUrl: './tipos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TiposDeEquipo {
  private readonly api = inject(CatalogoApi);
  private readonly sesion = inject(Sesion);

  protected readonly tipos = this.api.listarTipos();
  protected readonly baja = this.api.retirarTipo();
  protected readonly cambioDeModalidad = this.api.cambiarModalidad();

  protected readonly puedeEscribir = computed(() => this.sesion.puede('equipment.write'));

  protected readonly etiquetas = ETIQUETA_DE_MODALIDAD;

  /** El tipo cuya verificacion se esta configurando, si hay alguno. */
  protected readonly cambiando = signal<string | null>(null);

  /** Lo que el bloque de verificacion tiene puesto ahora mismo. */
  protected readonly configuracion = signal<ConfiguracionDeVerificacion>({
    modalidad: null,
    cantidadDatos: null,
    puntos: [],
    valida: true,
  });

  /** Lo que ya estaba guardado, para que el bloque abra con ello puesto. */
  protected readonly configuracionGuardada = signal<ConfiguracionDeVerificacion | null>(null);

  private readonly bloqueDeVerificacion = viewChild(Verificacion);

  protected readonly hayError = computed(
    () => this.tipos.isError() || this.baja.isError() || this.cambioDeModalidad.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(this.tipos.error() ?? this.baja.error() ?? this.cambioDeModalidad.error()),
  );

  protected etiquetaDeModalidad(modalidad: ModalidadDeVerificacion | undefined): string {
    return modalidad ? ETIQUETA_DE_MODALIDAD[modalidad] : 'Sin definir';
  }

  protected empezarACambiar(tipo: TipoDeEquipo): void {
    // El bloque abre con lo que el tipo ya tiene: reconfigurar no deberia empezar de cero.
    this.configuracionGuardada.set({
      modalidad: tipo.modalidadVerificacion ?? null,
      cantidadDatos: tipo.cantidadDatos ?? null,
      // El contrato declara opcional cada campo de la respuesta -springdoc no marca nada como
      // requerido-, asi que un punto incompleto se descarta en vez de afirmar que trae lo que no trae.
      puntos: (tipo.puntosVerificacion ?? [])
        .filter((punto) => punto.valor !== undefined && !!punto.unidad)
        .map((punto) => ({ valor: punto.valor!, unidad: punto.unidad! })),
      valida: true,
    });
    this.cambiando.set(tipo.id!);
  }

  protected cambiarModalidad(id: string): void {
    const configuracion = this.configuracion();

    if (!configuracion.valida) {
      this.bloqueDeVerificacion()?.marcarIntento();

      return;
    }

    this.cambioDeModalidad.mutate(
      {
        id,
        modalidad: configuracion.modalidad,
        cantidadDatos: configuracion.cantidadDatos,
        puntos: [...configuracion.puntos],
      },
      { onSuccess: () => this.cambiando.set(null) },
    );
  }

  protected retirar(id: string): void {
    this.baja.mutate(id);
  }
}
