import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PersonaApi } from '../persona-api';
import { ClienteApi } from '../../client/cliente-api';
import { Sesion } from '../../../core/auth/sesion';
import { Buscador, Opcion } from '../../../shared/buscador/buscador';
import { LARGO_MINIMO_DE_CONTRASENA } from '../contrasena';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/** Las tres altas que el API ofrece, cada una con su puerta y su autoridad. */
export type ClaseDeAlta = 'ingeniero' | 'administrador' | 'supervisor';

interface Receta {
  readonly titulo: string;
  readonly explicacion: string;
  /** El segmento de la ruta del API. */
  readonly oficio: 'admins' | 'engineers' | 'supervisors';
  readonly autoridad: string;
}

/**
 * El alta de una persona, que son tres altas distintas en una sola pantalla.
 *
 * <p><b>El API tiene tres puertas y eso no es ruido.</b> Las tres crean la persona <i>y su cuenta</i>
 * en Keycloak —ingeniero, administrador, supervisor del cliente—, y en las tres <b>el tipo lo dice la
 * ruta, no el cuerpo</b>, así que no hay forma de pedir «un ingeniero» y mandar «administrador» en un
 * campo. La regla es del backend; esta pantalla la refleja. Hasta V53 (2026-11-01) eran cuatro: la del
 * representante del cliente y una sin cuenta para los encargados, que se juntaron en el supervisor.
 *
 * <p><b>Por eso no hay un selector de tipo.</b> Sería el campo que invita a lo que el servidor va a
 * rechazar. Lo que hay son tres entradas desde el listado, cada una a lo suyo.
 *
 * <p><b>Un alta toca dos sistemas sin transacción que los envuelva</b>, y se nota en los errores: «ese
 * nombre de usuario ya existe» se arregla cambiándolo, y «no se pudo contactar con el sistema de
 * acceso» no se arregla tocando el formulario. El catálogo de errores los distingue, así que aquí basta
 * con enseñar lo que traduce.
 *
 * <p><b>Un supervisor se registra para un cliente.</b> Entrando desde la ficha del cliente, la ruta
 * trae {@code ?cliente=}; desde el listado se elige, sin obligar (2026-10-26, pedido por el usuario).
 * Creada la persona, se la nombra supervisora de ese cliente con el cargo escrito: sin eso la cuenta
 * entra y no ve nada, porque el filtrado por dueño acota sus lecturas a los clientes que supervisa. Son
 * dos llamadas sin transacción, y si la segunda falla la persona ya existe: se dice así y se enlaza la
 * ficha del cliente, en lugar de dejar el formulario listo para crearla otra vez.
 */
@Component({
  selector: 'app-nueva-persona',
  imports: [ReactiveFormsModule, RouterLink, Buscador],
  templateUrl: './nueva-persona.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuevaPersona {
  /** Lo pone la ruta, no el usuario: cada clase de alta tiene su propia dirección. */
  readonly clase = input.required<ClaseDeAlta>();

  /** El cliente que supervisará, si se entró desde su ficha. Llega por `?cliente=`. */
  readonly cliente = input<string>();

  private readonly api = inject(PersonaApi);
  private readonly clientes = inject(ClienteApi);
  private readonly sesion = inject(Sesion);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  private static readonly RECETAS: Readonly<Record<ClaseDeAlta, Receta>> = {
    ingeniero: {
      titulo: 'Registrar ingeniero',
      explicacion: 'Alguien de la empresa que ejecuta mantenimientos. Se le crea cuenta de acceso.',
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
    supervisor: {
      titulo: 'Registrar supervisor del cliente',
      explicacion:
        'La persona del cliente que revisa y aprueba el trabajo. Se le crea cuenta de acceso para consultar lo de los clientes que supervise.',
      oficio: 'supervisors',
      autoridad: 'person.write',
    },
  };

  protected readonly receta = computed(() => NuevaPersona.RECETAS[this.clase()]);
  protected readonly largoMinimo = LARGO_MINIMO_DE_CONTRASENA;

  protected readonly altaDeIngeniero = this.api.registrarConCuenta('engineers');
  protected readonly altaDeAdministrador = this.api.registrarConCuenta('admins');
  protected readonly altaDeSupervisor = this.api.registrarConCuenta('supervisors');
  protected readonly nombramiento = this.clientes.nombrarSupervisor();

  /** El cliente de la ruta, que solo cuenta en el alta de un supervisor. */
  private readonly clienteDeLaRuta = computed(() =>
    this.clase() === 'supervisor' ? (this.cliente() ?? '') : '',
  );
  protected readonly clienteDelAlta = this.clientes.detalle(this.clienteDeLaRuta);

  /** Un supervisor elige su cliente si no lo trae la ruta y quien lo registra puede nombrarlo. */
  protected readonly eligeCliente = computed(
    () =>
      this.clase() === 'supervisor' &&
      !this.clienteDeLaRuta() &&
      this.sesion.puede('client.read') &&
      this.sesion.puede('client.write'),
  );

  /** La persona ya existe y falta nombrarla: el formulario no debe volver a enviarse. */
  protected readonly pendiente = signal(false);

  protected readonly formulario = this.fb.nonNullable.group({
    cedula: ['', [Validators.required, Validators.maxLength(10)]],
    primerNombre: ['', Validators.required],
    segundoNombre: '',
    primerApellido: ['', Validators.required],
    segundoApellido: '',
    nombreUsuario: ['', Validators.required],
    password: ['', [Validators.required, Validators.minLength(LARGO_MINIMO_DE_CONTRASENA)]],
    // Vacío, el alta no nombra en ningún cliente.
    clienteElegido: '',
    cargo: ['', Validators.maxLength(80)],
    correos: this.fb.array([this.correoVacio()]),
    telefonos: this.fb.array([] as ReturnType<NuevaPersona['telefonoVacio']>[]),
  });

  /** Lo escrito, como señal: un formulario reactivo no lo es. */
  private readonly valores = toSignal(this.formulario.valueChanges, {
    initialValue: this.formulario.getRawValue(),
  });

  /** Para qué cliente es el alta de un supervisor: el de la ruta, o el elegido. */
  protected readonly idCliente = computed(
    () =>
      this.clienteDeLaRuta() || (this.eligeCliente() ? (this.valores().clienteElegido ?? '') : ''),
  );

  /** El cargo solo tiene sentido si hay en qué cliente nombrarlo. */
  protected readonly pideCargo = computed(
    () => this.clase() === 'supervisor' && !!this.idCliente(),
  );

  private readonly listaDeClientes = this.clientes.listar(this.eligeCliente);

  /** Solo lo vigente: un cliente retirado no admite a nadie nuevo. */
  protected readonly opcionesDeCliente = computed<readonly Opcion[]>(() =>
    (this.listaDeClientes.data() ?? [])
      .filter((c) => c.estadoActivo !== false && !!c.id && !!c.razonSocial)
      .map((c) => ({ id: c.id!, etiqueta: c.razonSocial! })),
  );

  protected get correos(): FormArray {
    return this.formulario.get('correos') as FormArray;
  }

  protected get telefonos(): FormArray {
    return this.formulario.get('telefonos') as FormArray;
  }

  protected readonly enCurso = computed(
    () =>
      this.altaDeIngeniero.isPending() ||
      this.altaDeAdministrador.isPending() ||
      this.altaDeSupervisor.isPending() ||
      this.nombramiento.isPending() ||
      this.pendiente(),
  );

  protected readonly hayError = computed(
    () =>
      this.altaDeIngeniero.isError() ||
      this.altaDeAdministrador.isError() ||
      this.altaDeSupervisor.isError(),
  );
  protected readonly mensajeDeError = computed(() => traducirError(this.fallo()));
  protected readonly detalles = computed(() => detallesDe(this.fallo()));

  constructor() {
    /**
     * El primer correo es obligatorio: con él se crea el usuario, lo exige el contrato.
     *
     * <p>Va en un efecto y no suelto en el constructor por la misma razón que antes de V53, cuando
     * dependía de la clase de alta: un {@code input.required()} no se puede leer mientras el
     * componente se construye. Se pone y se quita en la misma pasada, porque el router reutiliza el
     * componente al cambiar de una clase de alta a otra.
     */
    effect(() => {
      this.clase();
      const primerCorreo = this.correos.at(0)?.get('correoPersona');

      primerCorreo?.setValidators([Validators.required, Validators.email]);
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
    const alta = {
      cedula: v.cedula.trim(),
      primerNombre: v.primerNombre.trim(),
      // Los opcionales se mandan ausentes y no vacios: una cadena vacia no es «no tiene segundo
      // nombre», es un segundo nombre en blanco.
      segundoNombre: v.segundoNombre.trim() || undefined,
      primerApellido: v.primerApellido.trim(),
      segundoApellido: v.segundoApellido.trim() || undefined,
      nombreUsuario: v.nombreUsuario.trim(),
      password: v.password,
      emailPersonList: v.correos
        .map((c) => (c.correoPersona ?? '').trim())
        .filter(Boolean)
        .map((correoPersona) => ({ correoPersona })),
      phonePersonList: v.telefonos
        .map((t) => (t.telefonoPersona ?? '').trim())
        .filter(Boolean)
        .map((telefonoPersona) => ({ telefonoPersona })),
    };

    const alVolver = { onSuccess: () => void this.router.navigate(['/personas']) };

    switch (this.clase()) {
      case 'ingeniero':
        this.altaDeIngeniero.mutate(alta, alVolver);
        break;
      case 'administrador':
        this.altaDeAdministrador.mutate(alta, alVolver);
        break;
      case 'supervisor':
        this.altaDeSupervisor.mutate(alta, {
          onSuccess: (persona) => this.nombrarEnSuCliente(persona.identificador),
        });
        break;
    }
  }

  /**
   * Nombra a la persona recién creada supervisora del cliente de la ruta o del elegido, con su cargo,
   * y vuelve a la ficha de ese cliente. Sin cliente, vuelve al listado de personas como las demás altas.
   */
  private nombrarEnSuCliente(idPersona: string | undefined): void {
    const idCliente = this.idCliente();

    if (!idCliente || !idPersona) {
      void this.router.navigate(['/personas']);

      return;
    }

    this.nombramiento.mutate(
      { id: idCliente, idPersona, cargo: this.formulario.controls.cargo.value },
      {
        onSuccess: () => void this.router.navigate(['/clientes', idCliente]),
        onError: () => this.pendiente.set(true),
      },
    );
  }

  private fallo(): unknown {
    return (
      this.altaDeIngeniero.error() ??
      this.altaDeAdministrador.error() ??
      this.altaDeSupervisor.error()
    );
  }

  private correoVacio() {
    return this.fb.nonNullable.group({ correoPersona: ['', Validators.email] });
  }

  private telefonoVacio() {
    return this.fb.nonNullable.group({ telefonoPersona: '' });
  }
}
