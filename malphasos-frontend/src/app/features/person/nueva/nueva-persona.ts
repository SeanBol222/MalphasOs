import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { distinctUntilChanged } from 'rxjs';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PersonaApi } from '../persona-api';
import { AreaApi } from '../../client/area-api';
import { ClienteApi } from '../../client/cliente-api';
import { EncargadoApi } from '../../client/encargado-api';
import { SedeApi } from '../../client/sede-api';
import { Sesion } from '../../../core/auth/sesion';
import { Buscador, Opcion } from '../../../shared/buscador/buscador';
import { TipoDeAsignacion } from '../../../core/api/tipos';
import { LARGO_MINIMO_DE_CONTRASENA } from '../contrasena';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/** El valor de «De qué se encarga» cuando es la sede entera y no una de sus áreas. */
export const TODA_LA_SEDE = 'TODA_LA_SEDE';

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
 *
 * <p><b>Un representante se registra para un cliente.</b> Entrando desde la ficha del cliente, la
 * ruta trae {@code ?cliente=} y, creada la persona, se la nombra representante de ese cliente: sin
 * eso la cuenta entra y no ve nada, porque el filtrado por dueño acota sus lecturas a los clientes
 * que representa. Son dos llamadas sin transacción, y si la segunda falla la persona ya existe: se
 * dice así y se enlaza la ficha, en lugar de dejar el formulario listo para crearla otra vez.
 *
 * <p><b>Y desde el listado se elige el cliente, sin obligar</b> (2026-10-26, pedido por el usuario).
 * El representante elige el cliente del que será representante. El encargado elige cliente, sede y,
 * si quiere, un área: un encargado no cuelga del cliente sino de una sede o de un área suya, y con
 * sede elegida el alta va por {@code POST /managers}, que crea a la persona y la asigna en una sola
 * operación —por eso entonces pide un correo, como esa puerta exige—. Cada selector se ofrece solo a
 * quien puede usar lo que hace: nombrar exige {@code client.write}, y asignar {@code engineer.assign}.
 *
 * <p><b>Un representante puede ser además encargado</b>, y se dice eligiendo, no marcando: con el
 * cliente elegido aparece una sede opcional, y elegida la sede hay que decir si responde por toda o
 * por un área. Sustituye al check «También es encargado», que solo ponía la segunda función en la
 * ficha sin poner a nadie al frente de nada. Son tres llamadas sin transacción —alta, nombramiento y
 * asignación—, y si la última falla la persona ya está registrada y nombrada: se dice así y se ofrece
 * reintentar solo la asignación.
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

  /** El cliente del que será representante, si se entró desde su ficha. Llega por `?cliente=`. */
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
  protected readonly largoMinimo = LARGO_MINIMO_DE_CONTRASENA;
  protected readonly todaLaSede = TODA_LA_SEDE;

  /** Si esta alta crea cuenta en Keycloak, que es lo que decide la mitad del formulario. */
  protected readonly conCuenta = computed(() => this.receta().oficio !== null);

  protected readonly altaSinCuenta = this.api.registrarSinAcceso();
  protected readonly altaDeIngeniero = this.api.registrarConCuenta('engineers');
  protected readonly altaDeAdministrador = this.api.registrarConCuenta('admins');
  protected readonly altaDeRepresentante = this.api.registrarConCuenta('ceo-clients');
  private readonly encargados = inject(EncargadoApi);
  protected readonly altaDeEncargado = this.encargados.registrar();
  protected readonly asignacion = this.encargados.asignar();
  protected readonly nombramiento = this.clientes.nombrarRepresentante();

  /** El cliente de la ruta, que solo cuenta en el alta de un representante. */
  private readonly clienteDeLaRuta = computed(() =>
    this.clase() === 'representante' ? (this.cliente() ?? '') : '',
  );
  protected readonly clienteDelAlta = this.clientes.detalle(this.clienteDeLaRuta);

  /** Un representante elige su cliente si no lo trae la ruta y quien lo registra puede nombrarlo. */
  protected readonly eligeCliente = computed(
    () =>
      this.clase() === 'representante' &&
      !this.clienteDeLaRuta() &&
      this.sesion.puede('client.read') &&
      this.sesion.puede('client.write'),
  );

  /** Un encargado elige cliente, sede y área si quien lo registra puede asignar encargados. */
  protected readonly eligeSede = computed(
    () =>
      this.clase() === 'encargado' &&
      this.sesion.puede('client.read') &&
      this.sesion.puede('engineer.assign'),
  );

  /**
   * El paso que falló después de crear a la persona. Ya existe, así que el formulario no debe volver
   * a enviarse: se nombra desde la ficha del cliente o se reintenta la asignación.
   */
  protected readonly pendiente = signal<'nombrar' | 'asignar' | null>(null);
  private idCreada = '';

  protected readonly formulario = this.fb.nonNullable.group({
    cedula: ['', [Validators.required, Validators.maxLength(10)]],
    primerNombre: ['', Validators.required],
    segundoNombre: '',
    primerApellido: ['', Validators.required],
    segundoApellido: '',
    nombreUsuario: '',
    password: '',
    // Tambien encargado: el modelo admite una segunda funcion y el contrato solo acepta MANAGER ahi.
    // Solo en el ingeniero y el administrador: el representante lo dice eligiendo sede.
    tambienEncargado: false,
    // Vacios, el alta es la de siempre.
    clienteElegido: '',
    sede: '',
    // Elegida la sede es obligatoria: TODA_LA_SEDE o el identificador de un area.
    area: '',
    correos: this.fb.array([this.correoVacio()]),
    telefonos: this.fb.array([] as ReturnType<NuevaPersona['telefonoVacio']>[]),
  });

  /** Lo escrito, como señal: un formulario reactivo no lo es. */
  private readonly valores = toSignal(this.formulario.valueChanges, {
    initialValue: this.formulario.getRawValue(),
  });

  /** Para qué cliente es el alta de un representante: el de la ruta, o el elegido. */
  protected readonly idCliente = computed(
    () =>
      this.clienteDeLaRuta() || (this.eligeCliente() ? (this.valores().clienteElegido ?? '') : ''),
  );

  private readonly listaDeClientes = this.clientes.listar(
    computed(() => this.eligeCliente() || this.eligeSede()),
  );
  /**
   * El cliente en cuyas sedes se puede poner a la persona al frente, o vacío si no se ofrece: el
   * elegido para un encargado, y el del alta para un representante si quien registra puede asignar.
   */
  protected readonly clienteDeLaAsignacion = computed(() => {
    if (this.eligeSede()) {
      return this.valores().clienteElegido ?? '';
    }

    return this.clase() === 'representante' &&
      this.sesion.puede('client.read') &&
      this.sesion.puede('engineer.assign')
      ? this.idCliente()
      : '';
  });

  private readonly sedes = inject(SedeApi).listarDe(this.clienteDeLaAsignacion);
  private readonly areas = inject(AreaApi).listarDe(
    computed(() => (this.clienteDeLaAsignacion() ? (this.valores().sede ?? '') : '')),
  );

  /**
   * Solo lo vigente: un cliente, una sede o un área cerrados no admiten a nadie nuevo. Los clientes van
   * al buscador, que predice sobre la razón social y no admite un cliente que no exista.
   */
  protected readonly opcionesDeCliente = computed<readonly Opcion[]>(() =>
    (this.listaDeClientes.data() ?? [])
      .filter((c) => c.estadoActivo !== false && !!c.id && !!c.razonSocial)
      .map((c) => ({ id: c.id!, etiqueta: c.razonSocial! })),
  );
  protected readonly sedesVigentes = computed(() =>
    (this.sedes.data() ?? []).filter((s) => s.estadoActivo !== false),
  );
  protected readonly areasVigentes = computed(() =>
    (this.areas.data() ?? []).filter((a) => a.estadoActivo !== false),
  );

  protected readonly hayClienteElegido = computed(() => !!this.valores().clienteElegido);
  protected readonly haySedeElegida = computed(
    () => !!this.clienteDeLaAsignacion() && !!this.valores().sede,
  );

  /** Al frente de qué queda la persona, o nulo si no se eligió sede. */
  private readonly asignacionElegida = computed(
    (): { tipo: TipoDeAsignacion; idAsignacion: string } | null => {
      const { sede, area } = this.valores();

      if (!this.haySedeElegida() || !sede || !area) {
        return null;
      }

      return area === TODA_LA_SEDE
        ? { tipo: 'HEADQUARTER', idAsignacion: sede }
        : { tipo: 'SERVICE_AREA', idAsignacion: area };
    },
  );

  /** Con cuenta, el correo crea el usuario; con sede, lo exige la puerta de los encargados. */
  protected readonly exigeCorreo = computed(() => this.conCuenta() || this.haySedeElegida());

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
      this.altaDeRepresentante.isPending() ||
      this.altaDeEncargado.isPending() ||
      this.nombramiento.isPending() ||
      this.asignacion.isPending() ||
      this.pendiente() !== null,
  );

  protected readonly hayError = computed(
    () =>
      this.altaSinCuenta.isError() ||
      this.altaDeIngeniero.isError() ||
      this.altaDeAdministrador.isError() ||
      this.altaDeRepresentante.isError() ||
      this.altaDeEncargado.isError(),
  );
  protected readonly mensajeDeError = computed(() => traducirError(this.fallo()));
  protected readonly detalles = computed(() => detallesDe(this.fallo()));
  protected readonly mensajeDeAsignacion = computed(() => traducirError(this.asignacion.error()));

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
      const exigeCorreo = this.exigeCorreo();
      const exigeSede = this.eligeSede() && this.hayClienteElegido();
      const usuario = this.formulario.controls.nombreUsuario;
      const clave = this.formulario.controls.password;
      const exigeArea = this.haySedeElegida();
      const sede = this.formulario.controls.sede;
      const area = this.formulario.controls.area;
      const primerCorreo = this.correos.at(0)?.get('correoPersona');

      usuario.clearValidators();
      clave.clearValidators();
      sede.clearValidators();
      area.clearValidators();
      primerCorreo?.clearValidators();

      if (conCuenta) {
        usuario.addValidators(Validators.required);
        clave.addValidators([
          Validators.required,
          Validators.minLength(LARGO_MINIMO_DE_CONTRASENA),
        ]);
      }
      // Elegido el cliente de un encargado, falta decir de qué sede: el cliente solo no lo asigna.
      if (exigeSede) {
        sede.addValidators(Validators.required);
      }
      // Elegida la sede, hay que decir si responde por toda o por un área: no se supone.
      if (exigeArea) {
        area.addValidators(Validators.required);
      }
      primerCorreo?.addValidators(
        exigeCorreo ? [Validators.required, Validators.email] : Validators.email,
      );

      usuario.updateValueAndValidity({ emitEvent: false });
      clave.updateValueAndValidity({ emitEvent: false });
      sede.updateValueAndValidity({ emitEvent: false });
      area.updateValueAndValidity({ emitEvent: false });
      primerCorreo?.updateValueAndValidity({ emitEvent: false });
    });

    // Otro cliente deja sin sentido la sede y el área elegidas, y otra sede, el área.
    const { clienteElegido, sede, area } = this.formulario.controls;
    // El buscador avisa en cada tecla, aunque el cliente no cambie: solo cuenta un cambio de verdad.
    clienteElegido.valueChanges.pipe(distinctUntilChanged(), takeUntilDestroyed()).subscribe(() => {
      sede.setValue('');
      area.setValue('');
    });
    sede.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => area.setValue(''));
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
      segundoTipoPersona: this.esTambienEncargado(v.tambienEncargado)
        ? ('MANAGER' as const)
        : undefined,
    };

    const alVolver = { onSuccess: () => void this.router.navigate(['/personas']) };

    if (!this.conCuenta()) {
      const destino = this.asignacionElegida();

      if (destino) {
        // Una sola operación crea a la persona y la pone al frente: sin transacción partida.
        this.altaDeEncargado.mutate(
          {
            cedula: comunes.cedula,
            primerNombre: comunes.primerNombre,
            segundoNombre: comunes.segundoNombre,
            primerApellido: comunes.primerApellido,
            segundoApellido: comunes.segundoApellido,
            correos: comunes.emailPersonList.map((c) => ({ valor: c.correoPersona })),
            telefonos: comunes.phonePersonList.map((t) => ({ valor: t.telefonoPersona })),
            ...destino,
          },
          alVolver,
        );

        return;
      }

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
        this.altaDeRepresentante.mutate(conCuenta, {
          onSuccess: (persona) => this.nombrarEnSuCliente(persona.identificador),
        });
        break;
    }
  }

  /**
   * La segunda función: en el ingeniero y el administrador la dice el check; en el representante,
   * haber elegido sede, que es lo que de verdad lo hace encargado.
   */
  private esTambienEncargado(marcado: boolean): boolean {
    return this.clase() === 'representante' ? this.asignacionElegida() !== null : marcado;
  }

  /**
   * Nombra a la persona recién creada representante del cliente de la ruta o del elegido, y sigue
   * con la asignación. Sin cliente, vuelve al listado de personas como las demás altas.
   */
  private nombrarEnSuCliente(idPersona: string | undefined): void {
    const idCliente = this.idCliente();

    if (!idCliente || !idPersona) {
      void this.router.navigate(['/personas']);

      return;
    }

    this.idCreada = idPersona;
    this.nombramiento.mutate(
      { id: idCliente, idPersona },
      {
        onSuccess: () => this.ponerAlFrente(),
        onError: () => this.pendiente.set('nombrar'),
      },
    );
  }

  /**
   * Pone al representante recién nombrado al frente de la sede o del área elegidas, y vuelve a la
   * ficha del cliente. Es también el reintento cuando esta asignación falló.
   */
  protected ponerAlFrente(): void {
    const idCliente = this.idCliente();
    const destino = this.asignacionElegida();

    if (!destino) {
      void this.router.navigate(['/clientes', idCliente]);

      return;
    }

    this.pendiente.set(null);
    this.asignacion.mutate(
      { idPersona: this.idCreada, ...destino },
      {
        onSuccess: () => void this.router.navigate(['/clientes', idCliente]),
        onError: () => this.pendiente.set('asignar'),
      },
    );
  }

  private fallo(): unknown {
    return (
      this.altaSinCuenta.error() ??
      this.altaDeIngeniero.error() ??
      this.altaDeAdministrador.error() ??
      this.altaDeRepresentante.error() ??
      this.altaDeEncargado.error()
    );
  }

  private correoVacio() {
    return this.fb.nonNullable.group({ correoPersona: ['', Validators.email] });
  }

  private telefonoVacio() {
    return this.fb.nonNullable.group({ telefonoPersona: '' });
  }
}
