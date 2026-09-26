import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AreaApi } from '../area-api';
import { EncargadoApi } from '../encargado-api';
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
  private readonly router = inject(Router);

  protected readonly sede = this.api.detalle(this.id);
  private readonly ciudades = inject(UbicacionApi).listarCiudades();

  protected readonly cierre = this.api.cerrar();

  private readonly areaApi = inject(AreaApi);
  protected readonly areas = this.areaApi.listarDe(this.id);
  protected readonly altaDeArea = this.areaApi.crear();
  protected readonly cambioDeArea = this.areaApi.editar();
  protected readonly cierreDeArea = this.areaApi.cerrar();

  private readonly encargadoApi = inject(EncargadoApi);
  private readonly sesion = inject(Sesion);

  /** Quien no puede leer encargados no ve la seccion; quien no puede asignar, no ve el boton. */
  // Senales y no booleanos: las autoridades se releen en cada evento de Keycloak -renovacion de
  // token incluida-, y un valor calculado una vez en el constructor se quedaria con el de entonces.
  protected readonly puedeVerEncargados = computed(() => this.sesion.puede('engineer.read'));
  protected readonly puedeAsignar = computed(() => this.sesion.puede('engineer.assign'));

  private readonly encargados = this.encargadoApi.listar();
  private readonly personas = inject(PersonaApi).listar();
  protected readonly bajaDeEncargado = this.encargadoApi.retirar();

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
   */
  protected readonly encargadosDeLaSede = computed(() => {
    const areas = this.areas.data() ?? [];
    const porArea = new Map(areas.map((area) => [area.id, area.nombre]));
    const porPersona = new Map((this.personas.data() ?? []).map((p) => [p.identificador, p]));

    return (this.encargados.data() ?? [])
      .filter(
        (encargado) =>
          encargado.idSede === this.id() ||
          (encargado.idAreaServicio && porArea.has(encargado.idAreaServicio)),
      )
      .map((encargado) => ({
        idPersona: encargado.idPersona!,
        estadoActivo: encargado.estadoActivo,
        // Se dice de que tipo es la asignacion y no solo su nombre: «Urgencias» a secas no
        // distingue un area de una sede que se llamara igual.
        aCargoDe:
          encargado.tipo === 'SERVICE_AREA'
            ? `Área: ${porArea.get(encargado.idAreaServicio!) ?? 'de esta sede'}`
            : 'Toda la sede',
        // Sin la lista de personas -por permisos o porque aun no llego- no se inventa un nombre.
        nombre: porPersona.has(encargado.idPersona!)
          ? nombreCompleto(porPersona.get(encargado.idPersona!)!)
          : '',
      }));
  });

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

  protected quitarEncargado(idPersona: string): void {
    this.bajaDeEncargado.mutate(idPersona);
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
