import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { nombreCompleto, PersonaApi } from '../persona-api';
import { Sesion } from '../../../core/auth/sesion';
import {
  ETIQUETA_DE_TIPO_DE_PERSONA,
  exigeEscalonDeArriba,
  TipoDePersona,
} from '../../../core/api/tipos';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/**
 * La ficha de una persona: sus datos, sus contactos, su edición y su baja.
 *
 * <p><b>Se edita aquí mismo y no en una pantalla aparte</b>, al contrario que un cliente o una sede, y
 * la razón no es de gusto: <b>el permiso depende de la fila</b>. Tocar a alguien de la casa —ingeniero o
 * administrador— exige {@code super.person.write}, y a alguien del cliente, {@code person.write}; lo
 * decide el tipo de esa persona concreta. Una ruta aparte tendría que declarar una autoridad fija
 * <i>antes</i> de saber a quién va a cargar, y adivinaría. Aquí la persona ya está cargada cuando hay
 * que decidir, que es la misma razón por la que el backend resuelve esto en un bean y no en la
 * anotación.
 *
 * <p><b>El tipo se ve y no se cambia</b>, y es una decisión consciente: cambiarlo <b>no mueve al
 * usuario de grupo en Keycloak</b>, de modo que quien deja de ser ingeniero conserva sus permisos. Es
 * una deuda conocida y heredada; ofrecer el campo sería ofrecer una acción cuya consecuencia el
 * servidor no completa. La ficha lo dice en voz alta en lugar de esconderlo.
 *
 * <p><b>Los correos y los teléfonos se gestionan aquí mismo</b>, y cada uno es una llamada suelta a su
 * sub-recurso: el API no admite mandar la lista completa. Retirar uno <b>no lo borra</b>, apaga su
 * estado; la ficha solo pinta los vigentes.
 *
 * <p><b>La edición es un `PUT` que manda la persona entera</b> —{@code person} se migró antes de que la
 * convención de solo `PATCH` se fijara—, así que el formulario envía también lo que no se tocó. Los
 * correos y los teléfonos no viajan en ese cuerpo: son sub-recursos con sus propias rutas.
 */
@Component({
  selector: 'app-detalle-persona',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './detalle-persona.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetallePersona {
  readonly id = input.required<string>();

  private readonly api = inject(PersonaApi);
  private readonly sesion = inject(Sesion);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly persona = this.api.detalle(this.id);

  protected readonly edicion = this.api.editar();
  protected readonly baja = this.api.retirar();

  protected readonly editando = signal(false);
  protected readonly confirmandoBaja = signal(false);

  /**
   * Si esta sesión puede escribir sobre <b>esta</b> persona.
   *
   * <p>Es la escalera del backend, con la misma tabla: la gente de la casa un escalón por encima de la
   * del cliente. Y el administrador la cruza entera por la expansión de {@code admin.full}… salvo el
   * escalón de arriba, que es justo lo que el prefijo {@code super.} marca.
   */
  protected readonly puedeEscribir = computed(() => {
    const tipo = this.persona.data()?.tipoPersona;

    if (!tipo) {
      return false;
    }

    return this.sesion.puede(
      exigeEscalonDeArriba(tipo) ? 'super.person.write' : 'person.write',
    );
  });

  protected readonly nombre = computed(() => {
    const datos = this.persona.data();

    return datos ? nombreCompleto(datos) : '';
  });

  protected readonly funciones = computed(() =>
    [this.persona.data()?.tipoPersona, this.persona.data()?.segundoTipoPersona]
      .filter((tipo): tipo is TipoDePersona => !!tipo)
      .map((tipo) => ETIQUETA_DE_TIPO_DE_PERSONA[tipo])
      .join(' · '),
  );

  /** Solo los activos: un correo retirado se queda en la base y no se pinta como vigente. */
  protected readonly correos = computed(() =>
    (this.persona.data()?.emailPersonList ?? []).filter((correo) => correo.estadoActivo),
  );
  protected readonly telefonos = computed(() =>
    (this.persona.data()?.phonePersonList ?? []).filter((telefono) => telefono.estadoActivo),
  );

  // --- Contactos ---------------------------------------------------------------

  protected readonly altaDeCorreo = this.api.anadirCorreo();
  protected readonly bajaDeCorreo = this.api.retirarCorreo();
  protected readonly altaDeTelefono = this.api.anadirTelefono();
  protected readonly bajaDeTelefono = this.api.retirarTelefono();

  /** Cuál de los dos formularios de contacto está abierto, si hay alguno. */
  protected readonly anadiendo = signal<'correo' | 'telefono' | null>(null);

  protected readonly contactoNuevo = this.fb.nonNullable.group({
    correo: ['', Validators.email],
    telefono: '',
  });

  protected readonly formulario = this.fb.nonNullable.group({
    cedula: ['', [Validators.required, Validators.maxLength(10)]],
    primerNombre: ['', Validators.required],
    segundoNombre: '',
    primerApellido: ['', Validators.required],
    segundoApellido: '',
  });

  protected readonly hayError = computed(
    () =>
      this.persona.isError() ||
      this.edicion.isError() ||
      this.baja.isError() ||
      this.altaDeCorreo.isError() ||
      this.bajaDeCorreo.isError() ||
      this.altaDeTelefono.isError() ||
      this.bajaDeTelefono.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.persona.error() ??
        this.edicion.error() ??
        this.baja.error() ??
        this.altaDeCorreo.error() ??
        this.bajaDeCorreo.error() ??
        this.altaDeTelefono.error() ??
        this.bajaDeTelefono.error(),
    ),
  );
  protected readonly detalles = computed(() => detallesDe(this.edicion.error()));

  constructor() {
    // Vuelca lo que el servidor tiene, y solo mientras nadie haya escrito: cada recarga de la cache
    // -y hay una tras cada guardado- pisaria lo que la persona esta tecleando.
    effect(() => {
      const datos = this.persona.data();

      if (!datos || this.formulario.dirty) {
        return;
      }

      this.formulario.patchValue(
        {
          cedula: datos.cedula ?? '',
          primerNombre: datos.primerNombre ?? '',
          segundoNombre: datos.segundoNombre ?? '',
          primerApellido: datos.primerApellido ?? '',
          segundoApellido: datos.segundoApellido ?? '',
        },
        { emitEvent: false },
      );
    });
  }

  protected guardar(): void {
    const datos = this.persona.data();

    if (this.formulario.invalid || !datos) {
      this.formulario.markAllAsTouched();

      return;
    }

    const v = this.formulario.getRawValue();

    this.edicion.mutate(
      {
        id: this.id(),
        cambio: {
          cedula: v.cedula.trim(),
          primerNombre: v.primerNombre.trim(),
          segundoNombre: v.segundoNombre.trim() || undefined,
          primerApellido: v.primerApellido.trim(),
          segundoApellido: v.segundoApellido.trim() || undefined,
          // El tipo viaja TAL CUAL esta: el contrato lo exige en el cuerpo y esta pantalla no lo
          // ofrece para cambiar, porque cambiarlo no mueve al usuario de grupo en Keycloak.
          tipoPersona: datos.tipoPersona!,
          segundoTipoPersona: datos.segundoTipoPersona,
        },
      },
      {
        onSuccess: () => {
          this.formulario.markAsPristine();
          this.editando.set(false);
        },
      },
    );
  }

  protected anadirCorreo(): void {
    const correo = this.contactoNuevo.controls.correo.value.trim();

    if (!correo || this.contactoNuevo.controls.correo.invalid) {
      this.contactoNuevo.controls.correo.markAsTouched();

      return;
    }

    this.altaDeCorreo.mutate(
      { idPersona: this.id(), contacto: { correoPersona: correo } },
      { onSuccess: () => this.cerrarContacto() },
    );
  }

  protected anadirTelefono(): void {
    const telefono = this.contactoNuevo.controls.telefono.value.trim();

    if (!telefono) {
      return;
    }

    this.altaDeTelefono.mutate(
      { idPersona: this.id(), contacto: { telefonoPersona: telefono } },
      { onSuccess: () => this.cerrarContacto() },
    );
  }

  protected retirarCorreo(idContacto: string): void {
    this.bajaDeCorreo.mutate({ idPersona: this.id(), idContacto });
  }

  protected retirarTelefono(idContacto: string): void {
    this.bajaDeTelefono.mutate({ idPersona: this.id(), idContacto });
  }

  private cerrarContacto(): void {
    this.contactoNuevo.reset();
    this.anadiendo.set(null);
  }

  protected retirar(): void {
    this.baja.mutate(this.id(), {
      // Se vuelve al listado: quedarse en la ficha de alguien retirado invita a seguir tocandola.
      onSuccess: () => void this.router.navigate(['/personas']),
    });
  }
}
