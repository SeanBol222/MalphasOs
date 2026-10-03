import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CatalogoApi } from '../catalogo-api';
import { detallesDe, traducirError } from '../../../core/errores/traducir';
import { Buscador } from '../../../shared/buscador/buscador';
import { ConfiguracionDeVerificacion, Verificacion } from './verificacion';
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
  imports: [ReactiveFormsModule, RouterLink, Buscador, Verificacion],
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
    const verificacion = this.verificacion();

    if (this.formulario.invalid || !verificacion.valida) {
      this.formulario.markAllAsTouched();
      // Que el bloque pinte sus propios errores: hasta que se intenta guardar no se le reprocha nada.
      this.bloqueDeVerificacion()?.marcarIntento();

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
        // La lista entera o nada: una lista vacia significa que a este tipo no se le verifica nada,
        // y entonces ni se manda el campo.
        ...(verificacion.verificaciones.length
          ? { verificaciones: [...verificacion.verificaciones] }
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
