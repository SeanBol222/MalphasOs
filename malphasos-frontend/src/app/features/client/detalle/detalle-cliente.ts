import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClienteApi } from '../cliente-api';
import { SedeApi } from '../sede-api';
import { UbicacionApi } from '../../location/ubicacion-api';
import { EquipoApi } from '../../equipment/equipo-api';
import { nombreCompleto, PersonaApi } from '../../person/persona-api';
import { Sesion } from '../../../core/auth/sesion';
import { fechaCorta } from '../../../core/fechas';
import { conteoDe, contar } from '../conteos';
import { ETIQUETA_DE_IDENTIFICACION, TipoIdentificacion } from '../../../core/api/tipos';
import { detallesDe, traducirError } from '../../../core/errores/traducir';

/**
 * Un cliente con todo lo suyo: sus datos, sus correos y sus telefonos.
 *
 * <p>Es la pantalla que faltaba para que el listado sirviera de algo: hasta ahora se podia crear un
 * cliente y verlo en una fila, y nada mas. Cierra la otra mitad de RF-08.
 *
 * <p>Y sus sedes, que es por donde se entra a las areas de servicio y a los equipos: la ficha del
 * cliente es la raiz de todo lo que cuelga de el.
 *
 * <p><b>Rehecha el 2026-10-11 con el rediseño</b> —la maqueta «A-Cliente»—: cabecera con la sigla y
 * cuatro cifras, las sedes como tarjetas con sus áreas y sus equipos, y a un lado los representantes y
 * el contacto. Las cifras salen de los dos resúmenes del servidor, sumados en {@link contar}.
 *
 * <p><b>El identificador entra por la ruta como una senal de entrada</b>, no leyendo el snapshot del
 * router. Asi, navegar de un cliente a otro sin destruir el componente vuelve a consultar en vez de
 * quedarse mostrando el anterior.
 */
@Component({
  selector: 'app-detalle-cliente',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './detalle-cliente.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleCliente {
  /** Lo inyecta el router con `withComponentInputBinding`; el nombre debe coincidir con el de la ruta. */
  readonly id = input.required<string>();

  private readonly api = inject(ClienteApi);
  private readonly router = inject(Router);

  protected readonly cliente = this.api.detalle(this.id);
  protected readonly sedes = inject(SedeApi).listarDe(this.id);
  private readonly paises = inject(UbicacionApi).listarPaises();
  private readonly sesion = inject(Sesion);

  /**
   * Quien edita el cliente y quien lo retira, con la autoridad que el servidor exige a cada cosa. El
   * representante de un cliente solo lee: no se le ofrece lo que el servidor le rechazaría
   * (2026-10-26, pedido por el usuario).
   */
  protected readonly editaCliente = computed(() => this.sesion.puede('client.write'));
  protected readonly retiraCliente = computed(() => this.sesion.puede('client.delete'));
  protected readonly veEquipos = computed(() => this.sesion.puede('equipment.read'));
  protected readonly vePersonas = computed(() => this.sesion.puede('person.read'));
  protected readonly registraPersonas = computed(() => this.sesion.puede('person.write'));
  private readonly resumenDeSedes = inject(SedeApi).resumen(this.id);
  private readonly equipos = inject(EquipoApi).conteoPorArea(this.id, this.veEquipos);
  private readonly ciudades = inject(UbicacionApi).listarCiudades(
    computed(() => this.sesion.puede('location.read')),
  );
  private readonly personas = inject(PersonaApi).listar(this.vePersonas);

  /** Las cuatro cifras de la cabecera. */
  protected readonly cifras = computed(() => {
    return conteoDe(
      contar(this.resumenDeSedes.data() ?? [], this.equipos.data() ?? [], 'idCliente'),
      this.id(),
    );
  });

  /** Las sedes como tarjetas: dirección, ciudad, áreas abiertas y equipos en servicio. */
  protected readonly tarjetas = computed(() => {
    const porSede = contar(this.resumenDeSedes.data() ?? [], this.equipos.data() ?? [], 'id');
    const ciudades = new Map((this.ciudades.data() ?? []).map((c) => [c.id, c.nombre]));
    const resumen = new Map((this.resumenDeSedes.data() ?? []).map((s) => [s.id, s]));

    return (this.sedes.data() ?? [])
      .slice()
      .sort((a, b) => Number(b.estadoActivo) - Number(a.estadoActivo))
      .map((sede) => {
        const conteo = conteoDe(porSede, sede.id);

        return {
          id: sede.id!,
          nombre: sede.nombre ?? '',
          abierta: !!sede.estadoActivo,
          direccion: `Calle ${sede.calle} · Carrera ${sede.carrera} · N.º ${sede.numero}`,
          ciudad: ciudades.get(sede.idCiudad ?? '') ?? '',
          // Una sede cerrada no cuenta en las cifras, pero su tarjeta sí dice cuántas áreas tenía.
          areas: sede.estadoActivo
            ? conteo.areas
            : (resumen.get(sede.id)?.areas ?? []).filter((a) => a.abierta).length,
          equipos: conteo.equipos,
          ultimoServicio: fechaCorta(conteo.ultimoServicio),
        };
      });
  });

  /** Los representantes con nombre; sin {@code person.read}, solo cuántos son. */
  protected readonly representantes = computed(() => {
    const ids = this.cliente.data()?.representantes ?? [];
    const personas = new Map((this.personas.data() ?? []).map((p) => [p.identificador, p]));

    return ids.map((id) => {
      const persona = personas.get(id);

      return { id, nombre: persona ? nombreCompleto(persona) : 'Representante' };
    });
  });

  /**
   * Quién puede cambiar los representantes: hace falta escribir sobre el cliente y poder leer
   * personas, porque nombrar es elegir a alguien de una lista.
   */
  protected readonly nombraRepresentantes = computed(
    () => this.sesion.puede('client.write') && this.vePersonas(),
  );

  /**
   * A quién se puede nombrar: los representantes de cliente activos que aún no lo son de este.
   *
   * <p><b>Solo {@code CEO_CLIENT}</b>, aunque el servidor admite a cualquier persona: el filtrado por
   * dueño solo acota a ese tipo, así que nombrar a un ingeniero no le daría ni le quitaría nada y
   * dejaría en la ficha un dato que no significa lo que parece.
   */
  protected readonly candidatos = computed(() => {
    const ya = new Set(this.cliente.data()?.representantes ?? []);

    return (this.personas.data() ?? [])
      .filter(
        (p) =>
          p.tipoPersona === 'CEO_CLIENT' &&
          p.estadoActivo !== false &&
          !!p.identificador &&
          !ya.has(p.identificador),
      )
      .map((p) => ({ id: p.identificador!, nombre: nombreCompleto(p) }))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  });

  protected readonly nombramiento = this.api.nombrarRepresentante();
  protected readonly retiroDeRepresentante = this.api.retirarRepresentante();
  protected readonly representanteElegido = inject(FormBuilder).nonNullable.control('', [
    Validators.required,
  ]);

  protected readonly baja = this.api.retirar();
  protected readonly altaDeCorreo = this.api.agregarCorreo();
  protected readonly bajaDeCorreo = this.api.quitarCorreo();
  protected readonly altaDeTelefono = this.api.agregarTelefono();
  protected readonly bajaDeTelefono = this.api.quitarTelefono();

  /** El formulario de un contacto nuevo. Uno por tipo: escribir un correo no pisa un telefono. */
  protected readonly correoNuevo = inject(FormBuilder).nonNullable.control('', [
    Validators.required,
    Validators.email,
  ]);
  protected readonly telefonoNuevo = inject(FormBuilder).nonNullable.control('', [
    Validators.required,
  ]);

  /**
   * Si se esta pidiendo confirmacion para retirar.
   *
   * <p>No se usa {@code confirm()} del navegador: bloquea el hilo, no se puede dar estilo, no
   * respeta el manual de marca y no hay forma de probarlo. La confirmacion es parte de la pantalla.
   */
  protected readonly confirmandoBaja = signal(false);

  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.cliente.error() ??
        this.baja.error() ??
        this.nombramiento.error() ??
        this.retiroDeRepresentante.error() ??
        this.altaDeCorreo.error() ??
        this.bajaDeCorreo.error() ??
        this.altaDeTelefono.error() ??
        this.bajaDeTelefono.error(),
    ),
  );

  /** Hay algo que decir solo si algo fallo de verdad. */
  protected readonly hayError = computed(
    () =>
      this.cliente.isError() ||
      this.baja.isError() ||
      this.nombramiento.isError() ||
      this.retiroDeRepresentante.isError() ||
      this.altaDeCorreo.isError() ||
      this.bajaDeCorreo.isError() ||
      this.altaDeTelefono.isError() ||
      this.bajaDeTelefono.isError(),
  );

  protected readonly detalles = computed(() =>
    detallesDe(this.altaDeCorreo.error() ?? this.altaDeTelefono.error() ?? this.cliente.error()),
  );

  /**
   * El pais en palabras, distinguiendo <b>«no tiene» de «no se pudo resolver»</b>.
   *
   * <p>Las dos cosas se veian igual y no son lo mismo: el grupo {@code clients} no tiene
   * {@code location.read}, de modo que para esos usuarios el catalogo responde 403 y decir «sin
   * especificar» seria afirmar que el cliente no tiene pais cuando si lo tiene.
   */
  protected readonly pais = computed(() => {
    const id = this.cliente.data()?.idPais;

    if (!id) {
      return 'Sin especificar';
    }

    return this.paises.data()?.find((p) => p.id === id)?.nombre ?? 'No disponible';
  });

  protected etiqueta(tipo: TipoIdentificacion | undefined): string {
    return tipo ? ETIQUETA_DE_IDENTIFICACION[tipo] : '';
  }

  protected agregarCorreo(): void {
    if (this.correoNuevo.invalid) {
      this.correoNuevo.markAsTouched();

      return;
    }

    this.altaDeCorreo.mutate(
      { id: this.id(), valor: this.correoNuevo.value },
      { onSuccess: () => this.correoNuevo.reset() },
    );
  }

  protected agregarTelefono(): void {
    if (this.telefonoNuevo.invalid) {
      this.telefonoNuevo.markAsTouched();

      return;
    }

    this.altaDeTelefono.mutate(
      { id: this.id(), valor: this.telefonoNuevo.value },
      { onSuccess: () => this.telefonoNuevo.reset() },
    );
  }

  protected nombrar(): void {
    if (this.representanteElegido.invalid) {
      this.representanteElegido.markAsTouched();

      return;
    }

    this.nombramiento.mutate(
      { id: this.id(), idPersona: this.representanteElegido.value },
      { onSuccess: () => this.representanteElegido.reset() },
    );
  }

  protected retirarRepresentante(idPersona: string): void {
    this.retiroDeRepresentante.mutate({ id: this.id(), idPersona });
  }

  protected quitarCorreo(idContacto: string): void {
    this.bajaDeCorreo.mutate({ id: this.id(), idContacto });
  }

  protected quitarTelefono(idContacto: string): void {
    this.bajaDeTelefono.mutate({ id: this.id(), idContacto });
  }

  protected retirar(): void {
    this.baja.mutate(this.id(), {
      // Se vuelve al listado: quedarse en la ficha de alguien a quien se acaba de retirar invita a
      // seguir editandolo.
      onSuccess: () => void this.router.navigate(['/clientes']),
    });
  }
}
