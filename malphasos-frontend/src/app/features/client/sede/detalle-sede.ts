import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AreaApi } from '../area-api';
import { ClienteApi } from '../cliente-api';
import { EquipoApi } from '../../equipment/equipo-api';
import { OrdenApi } from '../../workOrder/orden-api';
import { fechaCorta } from '../../../core/fechas';
import { asignacionesDe, EncargadoApi } from '../encargado-api';
import { SedeApi } from '../sede-api';
import { nombreCompleto, PersonaApi } from '../../person/persona-api';
import { Sesion } from '../../../core/auth/sesion';
import { UbicacionApi } from '../../location/ubicacion-api';
import { traducirError } from '../../../core/errores/traducir';

/**
 * Una sede con sus datos, y desde donde se la edita o se la cierra.
 *
 * <p>Se llega desde la ficha de su cliente, y se vuelve a ella: {@code idCliente} viene en la
 * respuesta, de modo que la ruta no tiene que arrastrarlo. Repetirlo en la URL seria un segundo sitio
 * donde el dato puede no coincidir.
 *
 * <p>Aqui viven tambien <b>sus areas de servicio</b>, que es donde cuelgan los equipos. Se manejan en
 * esta misma pantalla y no en una propia porque un area es un nombre y nada mas: un formulario de una
 * sola linea no merece una pagina, y verlas junto a la sede es como se trabaja con ellas.
 *
 * <p>Y <b>sus encargados</b>, los de la sede y los de cada area. La seccion se oculta a quien no tenga
 * {@code engineer.read} —el grupo {@code clients} no la tiene—, que es ocultar y no autorizar: el
 * permiso lo sigue comprobando el servidor en cada llamada.
 *
 * <p><b>Rehecha el 2026-10-11 con el rediseño</b> —la maqueta «A-Sede»—: las áreas son una tabla con
 * sus equipos y su encargado —el propio, o el de la sede si no tiene—, y a un lado los encargados y las
 * órdenes abiertas en la sede. «Nueva orden aquí» lleva al alta con el cliente y la sede ya puestos.
 */
@Component({
  selector: 'app-detalle-sede',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './detalle-sede.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleSede {
  readonly id = input.required<string>();

  private readonly api = inject(SedeApi);
  private readonly sesion = inject(Sesion);
  private readonly router = inject(Router);

  protected readonly sede = this.api.detalle(this.id);
  private readonly ciudades = inject(UbicacionApi).listarCiudades(
    computed(() => this.sesion.puede('location.read')),
  );

  private readonly idCliente = computed(() => this.sede.data()?.idCliente ?? '');
  private readonly cliente = inject(ClienteApi).detalle(this.idCliente);
  protected readonly nombreDelCliente = computed(() => this.cliente.data()?.razonSocial ?? '');

  /**
   * Quien edita la sede y quien edita sus áreas, con la autoridad que el servidor exige a cada cosa:
   * la sede la protege la del cliente, y las áreas tienen la suya. El representante de un cliente solo
   * lee, y no se le ofrece lo que el servidor le rechazaría (2026-10-26, pedido por el usuario).
   */
  protected readonly editaSede = computed(() => this.sesion.puede('client.write'));
  protected readonly editaAreas = computed(() => this.sesion.puede('service-area.write'));
  protected readonly veEquipos = computed(() => this.sesion.puede('equipment.read'));
  protected readonly registraEquipos = computed(() => this.sesion.puede('equipment.assign'));
  protected readonly veOrdenes = computed(() => this.sesion.puede('work-order.read'));
  protected readonly programaOrdenes = computed(() => this.sesion.puede('work-order.write'));
  private readonly conteos = inject(EquipoApi).conteoPorArea(
    this.idCliente,
    computed(() => this.veEquipos() && !!this.idCliente()),
  );
  private readonly ordenes = inject(OrdenApi).listar(this.veOrdenes);

  protected readonly cierre = this.api.cerrar();

  private readonly areaApi = inject(AreaApi);
  protected readonly areas = this.areaApi.listarDe(this.id);
  protected readonly altaDeArea = this.areaApi.crear();
  protected readonly cambioDeArea = this.areaApi.editar();
  protected readonly cierreDeArea = this.areaApi.cerrar();

  private readonly encargadoApi = inject(EncargadoApi);

  /** Quien no puede leer encargados no ve la seccion; quien no puede asignar, no ve el boton. */
  // Senales y no booleanos: las autoridades se releen en cada evento de Keycloak -renovacion de
  // token incluida-, y un valor calculado una vez en el constructor se quedaria con el de entonces.
  protected readonly puedeVerEncargados = computed(() => this.sesion.puede('engineer.read'));
  protected readonly puedeAsignar = computed(() => this.sesion.puede('engineer.assign'));

  private readonly encargados = this.encargadoApi.listar(this.puedeVerEncargados);
  private readonly personas = inject(PersonaApi).listar(
    computed(() => this.sesion.puede('person.read')),
  );
  protected readonly bajaDeEncargado = this.encargadoApi.relevar();

  private readonly formBuilder = inject(FormBuilder);

  protected readonly areaNueva = this.formBuilder.nonNullable.control('', [
    Validators.required,
    Validators.maxLength(50),
  ]);
  protected readonly nombreEditado = this.formBuilder.nonNullable.control('', [
    Validators.required,
    Validators.maxLength(50),
  ]);

  protected readonly confirmandoCierre = signal(false);

  /** El area que se esta renombrando, si hay alguna. Solo una a la vez. */
  protected readonly renombrando = signal<string | null>(null);

  protected readonly hayError = computed(
    () =>
      this.sede.isError() ||
      this.cierre.isError() ||
      this.areas.isError() ||
      this.bajaDeEncargado.isError() ||
      this.altaDeArea.isError() ||
      this.cambioDeArea.isError() ||
      this.cierreDeArea.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.sede.error() ??
        this.cierre.error() ??
        this.areas.error() ??
        this.bajaDeEncargado.error() ??
        this.altaDeArea.error() ??
        this.cambioDeArea.error() ??
        this.cierreDeArea.error(),
    ),
  );

  /**
   * La ciudad en palabras, distinguiendo «no tiene» de «no se pudo resolver».
   *
   * <p>Mismo motivo que en la ficha del cliente: sin {@code location.read} el catalogo responde 403,
   * y una sede siempre tiene ciudad —el contrato la exige—, asi que «sin determinar» solo puede ser
   * un dato que no llego.
   */
  protected readonly ciudad = computed(() => {
    const id = this.sede.data()?.idCiudad;

    if (!id) {
      return 'Sin determinar';
    }

    return this.ciudades.data()?.find((c) => c.id === id)?.nombre ?? 'No disponible';
  });

  /**
   * Los encargados de esta sede y de sus areas, con nombre y con lo que tienen a cargo.
   *
   * <p>Se recorta aqui porque el API no ofrece filtrar por sede. Y el nombre se resuelve contra la
   * lista de personas: la respuesta del encargado solo trae identificadores.
   *
   * <p><b>Una fila por asignacion, no por persona</b> (2026-10-27): un encargado puede estarlo de la
   * sede y de varias de sus areas, y quitarlo de una no lo quita de las demas. Solo trae lo activo: de
   * lo que se le retiro ya no responde.
   */
  protected readonly encargadosDeLaSede = computed(() => {
    const areas = this.areas.data() ?? [];
    const porArea = new Map(areas.map((area) => [area.id, area.nombre]));
    const porPersona = new Map((this.personas.data() ?? []).map((p) => [p.identificador, p]));

    return (this.encargados.data() ?? []).flatMap((encargado) =>
      asignacionesDe(encargado)
        .filter(({ tipo, idAsignacion }) =>
          tipo === 'HEADQUARTER' ? idAsignacion === this.id() : porArea.has(idAsignacion),
        )
        .map(({ tipo, idAsignacion }) => ({
          idPersona: encargado.idPersona!,
          tipo,
          idAsignacion,
          idArea: tipo === 'SERVICE_AREA' ? idAsignacion : undefined,
          estadoActivo: true,
          // Se dice de que tipo es la asignacion y no solo su nombre: «Urgencias» a secas no
          // distingue un area de una sede que se llamara igual.
          aCargoDe:
            tipo === 'SERVICE_AREA'
              ? `Área: ${porArea.get(idAsignacion) ?? 'de esta sede'}`
              : 'Toda la sede',
          // Sin la lista de personas -por permisos o porque aun no llego- no se inventa un nombre.
          nombre: porPersona.has(encargado.idPersona!)
            ? nombreCompleto(porPersona.get(encargado.idPersona!)!)
            : '',
        })),
    );
  });

  /**
   * Las áreas como filas de la tabla: cuántos equipos tienen en servicio y quién responde por ellas.
   * Un área sin encargado propio responde con los de la sede —la regla de la hoja de vida, decidida el
   * 2026-10-05—, y se dice así para que no parezca que tiene uno.
   */
  protected readonly filasDeAreas = computed(() => {
    const equipos = new Map((this.conteos.data() ?? []).map((c) => [c.idArea, c.equipos ?? 0]));
    const encargados = this.encargadosDeLaSede().filter((e) => e.estadoActivo);
    const deLaSede = encargados
      .filter((e) => !e.idArea)
      .map((e) => e.nombre)
      .filter(Boolean);

    return (this.areas.data() ?? []).map((area) => {
      const propios = encargados
        .filter((e) => e.idArea === area.id)
        .map((e) => e.nombre)
        .filter(Boolean);

      return {
        id: area.id!,
        nombre: area.nombre ?? '',
        abierta: !!area.estadoActivo,
        equipos: equipos.get(area.id) ?? 0,
        encargado: propios.length
          ? propios.join(' · ')
          : deLaSede.length
            ? `De la sede: ${deLaSede.join(' · ')}`
            : '—',
        heredado: !propios.length,
      };
    });
  });

  /** Las órdenes de esta sede que siguen abiertas: ni ejecutadas ni anuladas. */
  protected readonly ordenesAbiertas = computed(() =>
    (this.ordenes.data() ?? [])
      .filter(
        (orden) =>
          orden.idSede === this.id() && orden.estadoActivo && orden.estadoEjecucion !== 'EJECUTADA',
      )
      .sort((a, b) => (a.fechaMantenimiento ?? '').localeCompare(b.fechaMantenimiento ?? ''))
      .map((orden) => ({
        id: orden.id!,
        fecha: fechaCorta(orden.fechaMantenimiento),
        enEjecucion: orden.estadoEjecucion === 'EN_EJECUCION',
      })),
  );

  /** Si alguien no tiene nombre, es porque la consulta de personas no pudo hacerse. */
  protected readonly faltanNombres = computed(() =>
    this.encargadosDeLaSede().some((encargado) => !encargado.nombre),
  );

  /** La direccion tal como se lee en Colombia: calle, carrera y numero, en ese orden. */
  protected readonly direccion = computed(() => {
    const datos = this.sede.data();

    return datos ? `Calle ${datos.calle} · Carrera ${datos.carrera} · N.º ${datos.numero}` : '';
  });

  protected agregarArea(): void {
    if (this.areaNueva.invalid) {
      this.areaNueva.markAsTouched();

      return;
    }

    this.altaDeArea.mutate(
      { idSede: this.id(), area: { nombre: this.areaNueva.value } },
      { onSuccess: () => this.areaNueva.reset() },
    );
  }

  protected empezarARenombrar(id: string, nombre: string | undefined): void {
    this.nombreEditado.setValue(nombre ?? '');
    this.renombrando.set(id);
  }

  protected renombrar(id: string): void {
    if (this.nombreEditado.invalid) {
      this.nombreEditado.markAsTouched();

      return;
    }

    this.cambioDeArea.mutate(
      { id, cambio: { nombre: this.nombreEditado.value } },
      { onSuccess: () => this.renombrando.set(null) },
    );
  }

  protected cerrarArea(id: string): void {
    this.cierreDeArea.mutate(id);
  }

  /** Lo quita de esa sede o de esa area, y solo de esa. */
  protected quitarEncargado(encargado: {
    idPersona: string;
    tipo: 'HEADQUARTER' | 'SERVICE_AREA';
    idAsignacion: string;
  }): void {
    this.bajaDeEncargado.mutate(encargado);
  }

  protected cerrar(): void {
    this.cierre.mutate(this.id(), {
      onSuccess: () => {
        const idCliente = this.sede.data()?.idCliente;

        // Se vuelve a la ficha del cliente si se sabe cual es; si la respuesta no lo trajo, al
        // listado. Nunca se queda en la ficha de una sede que se acaba de cerrar.
        void this.router.navigate(idCliente ? ['/clientes', idCliente] : ['/clientes']);
      },
    });
  }
}
