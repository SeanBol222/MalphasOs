import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PersonaApi } from '../persona-api';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/** Las cuatro altas que el API ofrece, cada una con su puerta y su autoridad. */
export type ClaseDeAlta = 'ingeniero' | 'administrador' | 'representante' | 'encargado';

interface Receta {
  readonly titulo: string;
  readonly explicacion: string;
  /** El segmento de la ruta del API, o nulo para el alta que no crea cuenta. */
  readonly oficio: 'admins' | 'engineers' | 'ceo-clients' | null;
  readonly autoridad: string;
}

/**
 * El alta de una persona, que son cuatro altas distintas en una sola pantalla.
 *
 * <p><b>El API tiene cuatro puertas y eso no es ruido.</b> Tres crean la persona <i>y su cuenta</i> en
 * Keycloak —ingeniero, administrador, representante del cliente— y una crea solo la fila. En las tres
 * primeras <b>el tipo lo dice la ruta, no el cuerpo</b>, así que no hay forma de pedir «un ingeniero» y
 * mandar «administrador» en un campo; y la cuarta <b>solo admite encargados</b>, porque dejarla
 * aceptar cualquier tipo permitía escribir una fila que dice ser administrador sin serlo y saltarse la
 * escalera de permisos. Las dos reglas son del backend; esta pantalla las refleja.
 *
 * <p><b>Por eso no hay un selector de tipo.</b> Sería el campo que invita a lo que el servidor va a
 * rechazar. Lo que hay son cuatro entradas desde el listado, cada una a lo suyo.
 *
 * <p><b>Un alta con cuenta toca dos sistemas sin transacción que los envuelva</b>, y se nota en los
 * errores: «ese nombre de usuario ya existe» se arregla cambiándolo, y «no se pudo contactar con el
 * sistema de acceso» no se arregla tocando el formulario. El catálogo de errores los distingue, así que
 * aquí basta con enseñar lo que traduce.
 */
@Component({
  selector: 'app-nueva-persona',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './nueva-persona.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuevaPersona {
  /** Lo pone la ruta, no el usuario: cada clase de alta tiene su propia dirección. */
  readonly clase = input.required<ClaseDeAlta>();

  private readonly api = inject(PersonaApi);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  private static readonly RECETAS: Readonly<Record<ClaseDeAlta, Receta>> = {
    ingeniero: {
      titulo: 'Registrar ingeniero',
      explicacion:
        'Alguien de BolívarBioingeniería que ejecuta mantenimientos. Se le crea cuenta de acceso.',
      oficio: 'engineers',
      autoridad: 'super.person.write',
    },
    administrador: {
      titulo: 'Registrar administrador',
      explicacion:
        'Administra el sistema entero. Se le crea cuenta de acceso con todos los permisos de recurso.',
      oficio: 'admins',
      autoridad: 'super.person.write',
    },
    representante: {
      titulo: 'Registrar representante del cliente',
      explicacion:
        'El representante legal de un cliente. Se le crea cuenta de acceso para consultar lo suyo.',
      oficio: 'ceo-clients',
      autoridad: 'person.write',
    },
    encargado: {
      titulo: 'Registrar encargado',
      explicacion:
        'El responsable de una sede o un área por parte del cliente. Existe como contacto y no entra al sistema: no se le crea cuenta.',
      oficio: null,
      autoridad: 'person.write',
    },
  };

  protected readonly receta = computed(() => NuevaPersona.RECETAS[this.clase()]);

  /** Si esta alta crea cuenta en Keycloak, que es lo que decide la mitad del formulario. */
  protected readonly conCuenta = computed(() => this.receta().oficio !== null);

  protected readonly altaSinCuenta = this.api.registrarSinAcceso();
  protected readonly altaDeIngeniero = this.api.registrarConCuenta('engineers');
  protected readonly altaDeAdministrador = this.api.registrarConCuenta('admins');
  protected readonly altaDeRepresentante = this.api.registrarConCuenta('ceo-clients');

  protected readonly formulario = this.fb.nonNullable.group({
    cedula: ['', [Validators.required, Validators.maxLength(10)]],
    primerNombre: ['', Validators.required],
    segundoNombre: '',
    primerApellido: ['', Validators.required],
    segundoApellido: '',
    nombreUsuario: '',
    password: '',
    // Tambien encargado: el modelo admite una segunda funcion y el contrato solo acepta MANAGER ahi.
    tambienEncargado: false,
    correos: this.fb.array([this.correoVacio()]),
    telefonos: this.fb.array([] as ReturnType<NuevaPersona['telefonoVacio']>[]),
  });

  protected get correos(): FormArray {
    return this.formulario.get('correos') as FormArray;
  }

  protected get telefonos(): FormArray {
    return this.formulario.get('telefonos') as FormArray;
  }

  protected readonly enCurso = computed(
    () =>
      this.altaSinCuenta.isPending() ||
      this.altaDeIngeniero.isPending() ||
      this.altaDeAdministrador.isPending() ||
      this.altaDeRepresentante.isPending(),
  );

  protected readonly hayError = computed(
    () =>
      this.altaSinCuenta.isError() ||
      this.altaDeIngeniero.isError() ||
      this.altaDeAdministrador.isError() ||
      this.altaDeRepresentante.isError(),
  );
  protected readonly mensajeDeError = computed(() => traducirError(this.fallo()));
  protected readonly detalles = computed(() => detallesDe(this.fallo()));

  constructor() {
    /**
     * Las obligaciones que solo existen si hay cuenta que crear: nombre de usuario, contrasena y al
     * menos un correo —con el se crea el usuario, lo exige el contrato—.
     *
     * <p>Va en un efecto y NO en el constructor, y la razon costo una tanda de pruebas rojas: un
     * {@code input.required()} no se puede leer mientras el componente se construye —«Input is
     * required but no value is available yet»—, porque el valor llega despues. Dentro de un efecto se
     * lee cuando ya esta.
     *
     * <p>Se ponen y se quitan en la misma pasada en vez de solo ponerse: asi el efecto vale aunque el
     * router reutilice el componente al cambiar de una clase de alta a otra.
     */
    effect(() => {
      const conCuenta = this.conCuenta();
      const usuario = this.formulario.controls.nombreUsuario;
      const clave = this.formulario.controls.password;
      const primerCorreo = this.correos.at(0)?.get('correoPersona');

      usuario.clearValidators();
      clave.clearValidators();
      primerCorreo?.clearValidators();

      if (conCuenta) {
        usuario.addValidators(Validators.required);
        clave.addValidators([Validators.required, Validators.minLength(8)]);
        primerCorreo?.addValidators([Validators.required, Validators.email]);
      } else {
        primerCorreo?.addValidators(Validators.email);
      }

      usuario.updateValueAndValidity({ emitEvent: false });
      clave.updateValueAndValidity({ emitEvent: false });
      primerCorreo?.updateValueAndValidity({ emitEvent: false });
    });
  }

  protected anadirCorreo(): void {
    this.correos.push(this.correoVacio());
  }

  protected anadirTelefono(): void {
    this.telefonos.push(this.telefonoVacio());
  }

  protected quitar(cual: 'correos' | 'telefonos', indice: number): void {
    (cual === 'correos' ? this.correos : this.telefonos).removeAt(indice);
  }

  protected registrar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();

      return;
    }

    const v = this.formulario.getRawValue();
    const comunes = {
      cedula: v.cedula.trim(),
      primerNombre: v.primerNombre.trim(),
      // Los opcionales se mandan ausentes y no vacios: una cadena vacia no es «no tiene segundo
      // nombre», es un segundo nombre en blanco.
      segundoNombre: v.segundoNombre.trim() || undefined,
      primerApellido: v.primerApellido.trim(),
      segundoApellido: v.segundoApellido.trim() || undefined,
      emailPersonList: v.correos
        .map((c) => (c.correoPersona ?? '').trim())
        .filter(Boolean)
        .map((correoPersona) => ({ correoPersona })),
      phonePersonList: v.telefonos
        .map((t) => (t.telefonoPersona ?? '').trim())
        .filter(Boolean)
        .map((telefonoPersona) => ({ telefonoPersona })),
      segundoTipoPersona: v.tambienEncargado ? ('MANAGER' as const) : undefined,
    };

    const alVolver = { onSuccess: () => void this.router.navigate(['/personas']) };

    if (!this.conCuenta()) {
      // El tipo no se elige: esta puerta solo admite encargados, y lo impone el servidor.
      this.altaSinCuenta.mutate({ ...comunes, tipoPersona: 'MANAGER' }, alVolver);

      return;
    }

    const conCuenta = {
      ...comunes,
      nombreUsuario: v.nombreUsuario.trim(),
      password: v.password,
    };

    switch (this.clase()) {
      case 'ingeniero':
        this.altaDeIngeniero.mutate(conCuenta, alVolver);
        break;
      case 'administrador':
        this.altaDeAdministrador.mutate(conCuenta, alVolver);
        break;
      case 'representante':
        this.altaDeRepresentante.mutate(conCuenta, alVolver);
        break;
    }
  }

  private fallo(): unknown {
    return (
      this.altaSinCuenta.error() ??
      this.altaDeIngeniero.error() ??
      this.altaDeAdministrador.error() ??
      this.altaDeRepresentante.error()
    );
  }

  private correoVacio() {
    return this.fb.nonNullable.group({ correoPersona: ['', Validators.email] });
  }

  private telefonoVacio() {
    return this.fb.nonNullable.group({ telefonoPersona: '' });
  }
}
