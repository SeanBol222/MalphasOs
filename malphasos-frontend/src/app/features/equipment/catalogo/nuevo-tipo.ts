import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CatalogoApi } from '../catalogo-api';
import {
  ETIQUETA_DE_MODALIDAD,
  ModalidadDeVerificacion,
  MODALIDADES_DE_VERIFICACION,
} from '../../../core/api/tipos';
import { detallesDe, traducirError } from '../../../core/errores/traducir';
import { Buscador } from '../../../shared/buscador/buscador';
import { tecnologiasAutorizadas } from './tecnologias';

/**
 * Alta de un tipo de equipo, la unica pieza del catalogo con ficha tecnica.
 *
 * <p>Cuatro campos son obligatorios porque el contrato los exige: nombre, definicion tecnica,
 * recomendaciones de cuidado y tecnologia predominante. Los cuatro restantes —voltaje, amperaje, valor
 * de mantenimiento y modalidad de verificacion— son opcionales, y se dice cuales lo son en vez de
 * dejar que se descubra al guardar.
 *
 * <p>Las cotas salen del contrato: 50 caracteres para nombre y tecnologia, 250 para los dos textos
 * largos. Si el backend las cambia, se regeneran los tipos y esto queda desalineado a la vista.
 */
@Component({
  selector: 'app-nuevo-tipo',
  imports: [ReactiveFormsModule, RouterLink, Buscador],
  templateUrl: './nuevo-tipo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuevoTipo {
  private readonly router = inject(Router);

  private readonly api = inject(CatalogoApi);

  protected readonly alta = this.api.crearTipo();

  /**
   * Las tecnologias autorizadas: las que esta empresa ya usa, y despues las de partida.
   *
   * <p><b>Lista cerrada</b>: no se admite una que no este. Sin eso, la misma tecnologia acaba escrita
   * de cuatro maneras y agrupar por ella deja de servir. Ver {@code tecnologias.ts}.
   */
  protected readonly tecnologias = computed(() => tecnologiasAutorizadas(this.tipos.data()));

  private readonly tipos = this.api.listarTipos();

  protected readonly modalidades = MODALIDADES_DE_VERIFICACION;
  protected readonly etiquetas = ETIQUETA_DE_MODALIDAD;

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    tecnologiaPredominante: ['', [Validators.required, Validators.maxLength(50)]],
    definicionTecnica: ['', [Validators.required, Validators.maxLength(250)]],
    recomendacionesCuidado: ['', [Validators.required, Validators.maxLength(250)]],
    modalidadVerificacion: ['' as ModalidadDeVerificacion | ''],
    voltaje: [null as number | null],
    amperaje: [null as number | null],
    valorUnitarioMantenimiento: [null as number | null],
  });

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

    // Los opcionales que nadie relleno no viajan. Un cero no es lo mismo que "no se sabe", y mandar
    // null o cadena vacia haria que el backend guardara un dato inventado.
    this.alta.mutate(
      {
        nombre: datos.nombre,
        tecnologiaPredominante: datos.tecnologiaPredominante,
        definicionTecnica: datos.definicionTecnica,
        recomendacionesCuidado: datos.recomendacionesCuidado,
        ...(datos.modalidadVerificacion
          ? { modalidadVerificacion: datos.modalidadVerificacion }
          : {}),
        ...(datos.voltaje === null ? {} : { voltaje: datos.voltaje }),
        ...(datos.amperaje === null ? {} : { amperaje: datos.amperaje }),
        ...(datos.valorUnitarioMantenimiento === null
          ? {}
          : { valorUnitarioMantenimiento: datos.valorUnitarioMantenimiento }),
      },
      { onSuccess: () => void this.router.navigate(['/catalogo']) },
    );
  }
}
