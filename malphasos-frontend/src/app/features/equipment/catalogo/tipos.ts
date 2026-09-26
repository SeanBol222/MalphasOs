import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CatalogoApi } from '../catalogo-api';
import { Sesion } from '../../../core/auth/sesion';
import {
  ETIQUETA_DE_MODALIDAD,
  ModalidadDeVerificacion,
  MODALIDADES_DE_VERIFICACION,
} from '../../../core/api/tipos';
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
 */
@Component({
  selector: 'app-tipos-de-equipo',
  imports: [ReactiveFormsModule, RouterLink],
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

  protected readonly modalidades = MODALIDADES_DE_VERIFICACION;
  protected readonly etiquetas = ETIQUETA_DE_MODALIDAD;

  /** El tipo cuya modalidad se esta cambiando, si hay alguno. */
  protected readonly cambiando = signal<string | null>(null);
  protected readonly modalidadElegida = inject(FormBuilder).nonNullable.control(
    'PATRON_CONSTANTE' as ModalidadDeVerificacion,
    Validators.required,
  );

  protected readonly hayError = computed(
    () => this.tipos.isError() || this.baja.isError() || this.cambioDeModalidad.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(this.tipos.error() ?? this.baja.error() ?? this.cambioDeModalidad.error()),
  );

  protected etiquetaDeModalidad(modalidad: ModalidadDeVerificacion | undefined): string {
    return modalidad ? ETIQUETA_DE_MODALIDAD[modalidad] : 'Sin definir';
  }

  protected empezarACambiar(id: string, modalidad: ModalidadDeVerificacion | undefined): void {
    this.modalidadElegida.setValue(modalidad ?? 'PATRON_CONSTANTE');
    this.cambiando.set(id);
  }

  protected cambiarModalidad(id: string): void {
    this.cambioDeModalidad.mutate(
      { id, modalidad: this.modalidadElegida.value },
      { onSuccess: () => this.cambiando.set(null) },
    );
  }

  protected retirar(id: string): void {
    this.baja.mutate(id);
  }
}
