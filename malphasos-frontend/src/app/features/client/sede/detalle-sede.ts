import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AreaApi } from '../area-api';
import { ClienteApi } from '../cliente-api';
import { EquipoApi } from '../../equipment/equipo-api';
import { OrdenApi } from '../../workOrder/orden-api';
import { fechaCorta } from '../../../core/fechas';
import { SedeApi } from '../sede-api';
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
 * <p><b>Rehecha el 2026-10-11 con el rediseño</b> —la maqueta «A-Sede»—: las áreas son una tabla con
 * sus equipos, y a un lado las órdenes abiertas en la sede. «Nueva orden aquí» lleva al alta con el
 * cliente y la sede ya puestos.
 *
 * <p><b>Hasta V53 (2026-11-01) tenía además sus encargados</b>, los de la sede y los de cada área, con
 * una columna en la tabla y una sección para ponerlos al frente. Salieron con ellos: quien responde
 * por el cliente es su supervisor, en todas sus sedes, y se nombra en la ficha del cliente.
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
   * la sede la protege la del cliente, y las áreas tienen la suya. El supervisor de un cliente solo
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
      this.altaDeArea.isError() ||
      this.cambioDeArea.isError() ||
      this.cierreDeArea.isError(),
  );
  protected readonly mensajeDeError = computed(() =>
    traducirError(
      this.sede.error() ??
        this.cierre.error() ??
        this.areas.error() ??
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

  /** Las áreas como filas de la tabla, con cuántos equipos tienen en servicio. */
  protected readonly filasDeAreas = computed(() => {
    const equipos = new Map((this.conteos.data() ?? []).map((c) => [c.idArea, c.equipos ?? 0]));

    return (this.areas.data() ?? []).map((area) => ({
      id: area.id!,
      nombre: area.nombre ?? '',
      abierta: !!area.estadoActivo,
      equipos: equipos.get(area.id) ?? 0,
    }));
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
