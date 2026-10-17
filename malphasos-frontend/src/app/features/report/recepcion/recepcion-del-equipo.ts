import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReporteApi } from '../reporte-api';
import {
  AccesorioDeReporte,
  ESTADOS_AL_INGRESO,
  ETIQUETA_DE_ESTADO_AL_INGRESO,
  EstadoAlIngreso,
  RegistroDeIngreso,
  VerificacionAlIngreso,
} from '../../../core/api/tipos';
import { traducirError } from '../../../core/errores/traducir';

/** Las dos filas fijas del informe, decididas por el usuario el 2026-10-16. */
export const VERIFICACIONES_AL_INGRESO = [
  { clave: 'fisico', texto: 'Estado de la estructura física del equipo' },
  { clave: 'operativo', texto: 'Estado operativo del equipo' },
] as const;

type Clave = (typeof VERIFICACIONES_AL_INGRESO)[number]['clave'];

/**
 * Cómo llegó el equipo (V31, 2026-10-16): las dos verificaciones al ingreso y los accesorios, que es
 * lo primero que dice el informe impreso después de los datos del cliente y del equipo.
 *
 * <p>Tiene su propio formulario y su botón, como la tabla de verificación, porque se registra en otro
 * momento —al recibir el equipo, antes de tocarlo— y por su propia ruta del API. Los accesorios se
 * mandan todos juntos y sustituyen a los anteriores.
 *
 * <p><b>«No conforme» exige observación</b>, aquí y en el servidor: sin ella el reporte dice que algo
 * estaba mal y no qué.
 */
@Component({
  selector: 'app-recepcion-del-equipo',
  imports: [ReactiveFormsModule],
  templateUrl: './recepcion-del-equipo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecepcionDelEquipo {
  readonly idReporte = input.required<string>();
  readonly ingresoFisico = input<VerificacionAlIngreso | null | undefined>(null);
  readonly ingresoOperativo = input<VerificacionAlIngreso | null | undefined>(null);
  readonly accesorios = input<readonly AccesorioDeReporte[]>([]);
  readonly editable = input(false);

  private readonly fb = inject(FormBuilder);
  protected readonly registro = inject(ReporteApi).registrarIngreso();

  protected readonly VERIFICACIONES = VERIFICACIONES_AL_INGRESO;
  protected readonly ESTADOS = ESTADOS_AL_INGRESO;
  protected readonly etiqueta = ETIQUETA_DE_ESTADO_AL_INGRESO;

  protected readonly formulario = this.fb.group({
    fisico: this.verificacion(),
    operativo: this.verificacion(),
    accesorios: this.fb.array([] as FormGroup[]),
  });

  protected get filasDeAccesorios(): FormArray<FormGroup> {
    return this.formulario.controls.accesorios as FormArray<FormGroup>;
  }

  private readonly escrito = toSignal(this.formulario.valueChanges);

  /** Lo que impide guardar, en palabras: el servidor lo rechazaría igual, pero con un 400. */
  protected readonly problemas = computed(() => {
    this.escrito();
    const valor = this.formulario.getRawValue();
    const problemas: string[] = [];

    for (const { clave, texto } of VERIFICACIONES_AL_INGRESO) {
      const { estado, observacion } = valor[clave];
      if (!estado) {
        problemas.push(`Falta decir el ${texto.toLowerCase()}.`);
      } else if (estado === 'NO_CONFORME' && !observacion?.trim()) {
        problemas.push(`${texto}: si no es conforme, diga qué estaba mal.`);
      }
    }
    valor.accesorios.forEach((accesorio, indice) => {
      if (!accesorio['nombre']?.trim()) {
        problemas.push(`El accesorio ${indice + 1} no tiene nombre.`);
      } else if (!accesorio['conforme'] && !accesorio['observacion']?.trim()) {
        problemas.push(`${accesorio['nombre'].trim()}: si no es conforme, diga por qué.`);
      }
    });

    return problemas;
  });

  protected readonly hayError = computed(() => this.registro.isError());
  protected readonly mensajeDeError = computed(() => traducirError(this.registro.error()));

  constructor() {
    // Se rehace con lo guardado mientras nadie haya escrito, como la ficha y la tabla de verificacion:
    // sin la guarda, la respuesta de un guardado pisaria lo que se esta tecleando.
    effect(() => {
      const fisico = this.ingresoFisico();
      const operativo = this.ingresoOperativo();
      const accesorios = this.accesorios();

      if (this.formulario.dirty) {
        return;
      }

      this.formulario.controls.fisico.setValue(this.valorDe(fisico), { emitEvent: false });
      this.formulario.controls.operativo.setValue(this.valorDe(operativo), { emitEvent: false });
      this.filasDeAccesorios.clear({ emitEvent: false });
      for (const accesorio of accesorios) {
        this.filasDeAccesorios.push(this.accesorio(accesorio), { emitEvent: false });
      }
      this.aplicarEstado();
      this.formulario.updateValueAndValidity();
    });

    effect(() => {
      this.editable();
      this.aplicarEstado();
    });
  }

  protected anadirAccesorio(): void {
    this.filasDeAccesorios.push(this.accesorio());
    this.formulario.markAsDirty();
  }

  protected quitarAccesorio(indice: number): void {
    this.filasDeAccesorios.removeAt(indice);
    this.formulario.markAsDirty();
  }

  protected guardar(): void {
    if (this.problemas().length) {
      this.formulario.markAllAsTouched();

      return;
    }

    const valor = this.formulario.getRawValue();
    const texto = (t: string | null | undefined) => t?.trim() || undefined;
    const ingreso: RegistroDeIngreso = {
      fisico: valor.fisico.estado as EstadoAlIngreso,
      observacionFisico: texto(valor.fisico.observacion),
      operativo: valor.operativo.estado as EstadoAlIngreso,
      observacionOperativo: texto(valor.operativo.observacion),
      accesorios: valor.accesorios.map((accesorio) => ({
        nombre: accesorio['nombre'].trim(),
        conforme: !!accesorio['conforme'],
        observacion: texto(accesorio['observacion']),
      })),
    };

    this.registro.mutate(
      { id: this.idReporte(), ingreso },
      { onSuccess: () => this.formulario.markAsPristine() },
    );
  }

  protected idDe(clave: Clave, estado: EstadoAlIngreso): string {
    return `ingreso-${clave}-${estado}`;
  }

  private verificacion() {
    return this.fb.group({
      estado: this.fb.control<EstadoAlIngreso | null>(null),
      observacion: this.fb.control<string>(''),
    });
  }

  private valorDe(verificacion: VerificacionAlIngreso | null | undefined) {
    return {
      estado: (verificacion?.estado as EstadoAlIngreso | undefined) ?? null,
      observacion: verificacion?.observacion ?? '',
    };
  }

  private accesorio(accesorio?: AccesorioDeReporte): FormGroup {
    return this.fb.group({
      nombre: this.fb.nonNullable.control(accesorio?.nombre ?? ''),
      conforme: this.fb.nonNullable.control(accesorio?.conforme ?? true),
      observacion: this.fb.nonNullable.control(accesorio?.observacion ?? ''),
    });
  }

  /** Un control añadido a un formulario desactivado nace activo: se reaplica en cada rehacer. */
  private aplicarEstado(): void {
    if (this.editable()) {
      this.formulario.enable({ emitEvent: false });
    } else {
      this.formulario.disable({ emitEvent: false });
    }
  }
}
