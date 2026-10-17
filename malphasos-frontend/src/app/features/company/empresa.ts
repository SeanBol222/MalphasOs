import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Sesion } from '../../core/auth/sesion';
import { traducirError } from '../../core/errores/traducir';
import { Empresa as DatosGuardados } from '../../core/api/tipos';
import { EmpresaApi } from './empresa-api';

/** Lo que se escribe en un campo opcional: recortado, y en blanco es que falta. */
const opcional = (valor: string) => valor.trim() || undefined;

/** Los teléfonos fijos se escriben en un campo, separados por comas: casi nunca son más de dos. */
export const MAXIMO_TELEFONOS = 5;

export function telefonosDe(texto: string): string[] {
  return texto
    .split(',')
    .map((telefono) => telefono.trim())
    .filter(Boolean);
}

/**
 * La empresa que presta el servicio (2026-10-17): el membrete de la hoja de vida y del informe, que un
 * administrador edita aquí, logo incluido.
 *
 * <p>Decidido por el usuario: hasta ese día los datos vivían en un archivo del servidor y solo cambiaban
 * reiniciando. El archivo siembra ahora el primer arranque y desde ahí manda lo que se guarde aquí. Un
 * informe ya cerrado no cambia: copia el membrete al cerrarse.
 *
 * <p>Sin empresa configurada —una instalación nueva sin archivo— el formulario sale vacío y lo dice: los
 * documentos se imprimen sin membrete hasta que alguien la guarde. El logo se pone después de guardar
 * los datos, porque el servidor lo cuelga de la empresa.
 */
@Component({
  selector: 'app-empresa',
  imports: [ReactiveFormsModule],
  templateUrl: './empresa.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Empresa {
  private readonly api = inject(EmpresaApi);
  private readonly formBuilder = inject(FormBuilder);
  private readonly sesion = inject(Sesion);

  protected readonly empresa = this.api.consultar();
  protected readonly guardado = this.api.guardar();
  protected readonly subida = this.api.subirLogo();
  protected readonly retiroDelLogo = this.api.quitarLogo();

  protected readonly puedeEscribir = computed(() => this.sesion.puede('company.write'));
  protected readonly configurada = computed(() => !!this.empresa.data());

  /** Se avisa al guardar con éxito; se borra en cuanto se vuelve a escribir. */
  protected readonly guardadoConExito = signal(false);

  protected readonly formulario = this.formBuilder.nonNullable.group({
    // Con required solo, un nombre de espacios pasaba: el servidor lo rechazaría con un 400.
    nombre: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(160)]],
    nit: ['', [Validators.maxLength(30)]],
    direccion: ['', [Validators.maxLength(200)]],
    ciudad: ['', [Validators.maxLength(80)]],
    telefonos: [''],
    movil: ['', [Validators.maxLength(30)]],
    correo: ['', [Validators.maxLength(120)]],
    garantia: ['', [Validators.maxLength(2000)]],
    aclaracion: ['', [Validators.maxLength(2000)]],
    codigoHojaDeVida: ['', [Validators.maxLength(40)]],
    codigoInforme: ['', [Validators.maxLength(40)]],
  });

  protected readonly hayError = computed(
    () =>
      this.empresa.isError() ||
      this.guardado.isError() ||
      this.subida.isError() ||
      this.retiroDelLogo.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.empresa.error() ??
        this.guardado.error() ??
        this.subida.error() ??
        this.retiroDelLogo.error(),
    ),
  );

  constructor() {
    // Lo guardado llena el formulario cada vez que llega, salvo si se está escribiendo encima: una
    // recarga en segundo plano no puede borrar lo que alguien tiene a medio corregir.
    effect(() => {
      const datos = this.empresa.data();
      if (datos && !this.formulario.dirty) {
        this.llenar(datos);
      }
    });
    effect(() => {
      if (this.puedeEscribir()) {
        this.formulario.enable();
      } else {
        this.formulario.disable();
      }
    });
    this.formulario.valueChanges.subscribe(() => this.guardadoConExito.set(false));
  }

  protected demasiadosTelefonos(): boolean {
    return telefonosDe(this.formulario.controls.telefonos.value).length > MAXIMO_TELEFONOS;
  }

  protected guardar(): void {
    if (this.formulario.invalid || this.demasiadosTelefonos()) {
      this.formulario.markAllAsTouched();

      return;
    }

    const valores = this.formulario.getRawValue();
    this.guardado.mutate(
      {
        nombre: valores.nombre.trim(),
        nit: opcional(valores.nit),
        direccion: opcional(valores.direccion),
        ciudad: opcional(valores.ciudad),
        telefonos: telefonosDe(valores.telefonos),
        movil: opcional(valores.movil),
        correo: opcional(valores.correo),
        garantia: opcional(valores.garantia),
        aclaracion: opcional(valores.aclaracion),
        codigoHojaDeVida: opcional(valores.codigoHojaDeVida),
        codigoInforme: opcional(valores.codigoInforme),
      },
      {
        onSuccess: (guardada) => {
          this.llenar(guardada);
          this.guardadoConExito.set(true);
        },
      },
    );
  }

  protected elegirLogo(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const archivo = campo.files?.[0];
    if (archivo) {
      this.subida.mutate(archivo, { onSettled: () => (campo.value = '') });
    }
  }

  /**
   * Sin emitir: el aviso de «guardado» lo borra lo que alguien escribe, no la recarga que sigue a guardar
   * —que rellena el formulario con lo mismo que se acaba de guardar—. Emitiendo, el aviso duraba lo que
   * tardaba esa recarga, y en el navegador no llegaba a verse.
   */
  private llenar(datos: DatosGuardados): void {
    this.formulario.reset(
      {
        nombre: datos.nombre ?? '',
        nit: datos.nit ?? '',
        direccion: datos.direccion ?? '',
        ciudad: datos.ciudad ?? '',
        telefonos: (datos.telefonos ?? []).join(', '),
        movil: datos.movil ?? '',
        correo: datos.correo ?? '',
        garantia: datos.garantia ?? '',
        aclaracion: datos.aclaracion ?? '',
        codigoHojaDeVida: datos.codigoHojaDeVida ?? '',
        codigoInforme: datos.codigoInforme ?? '',
      },
      { emitEvent: false },
    );
  }
}
