import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  injectQueryClient,
} from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CambioDeTipoDeEquipo,
  EquipoDeCatalogo,
  Fabricante,
  Magnitud,
  Marca,
  Modelo,
  NuevaVerificacionDeTipo,
  NuevoEquipoDeCatalogo,
  NuevoFabricante,
  NuevoModelo,
  NuevoNombre,
  NuevoTipoDeEquipo,
  TipoDeEquipo,
  UnidadDeMedida,
} from '../../core/api/tipos';

/**
 * La unica puerta al servidor para el catalogo de equipos: sus cinco piezas.
 *
 * <p><b>Por que cinco y no una.</b> El catalogo no describe «un equipo», describe la cadena por la
 * que se llega a uno: una <b>marca</b> y un <b>tipo</b> se combinan en un <b>equipo</b> —«tensiometro
 * Welch Allyn»—, y ese equipo con un <b>fabricante</b> forma un <b>modelo</b>, que es lo que se
 * instala en un area. Separarlas es lo que permite que dos clientes compartan el mismo modelo sin
 * duplicar nada, y es del esquema: son cinco tablas.
 *
 * <p>Van en un solo servicio, al contrario que los agregados de {@code client}, y es a proposito: las
 * cinco se consultan juntas en cada pantalla del catalogo, porque ninguna respuesta trae nombres y
 * todas hay que cruzarlas para pintar una fila legible. Repartirlas en cinco servicios obligaria a
 * inyectar los cinco en cada sitio.
 *
 * <p>Toda escritura invalida el catalogo entero. Es barato —son listas cortas— y evita razonar sobre
 * que pieza depende de cual: crear una marca cambia lo que puede elegirse al crear un equipo.
 *
 * <p><b>Y la invalidacion no se espera</b>, al contrario que en el resto de los servicios. TanStack
 * aguarda la promesa que devuelve {@code onSuccess} antes de resolver la mutacion, de modo que
 * devolverla encadenaba cada alta con la recarga completa del catalogo: el panel que crea un modelo con
 * sus piezas hace cinco llamadas en serie, y cada una se quedaba esperando cuatro consultas que no
 * necesitaba. Se descubrio porque la prueba de ese panel se colgaba en el segundo paso.
 */
@Injectable({ providedIn: 'root' })
export class CatalogoApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly api = `${environment.api}/v1/api`;

  static readonly CLAVE = ['catalogo'] as const;

  private static clave(pieza: string) {
    return [...CatalogoApi.CLAVE, pieza] as const;
  }

  // --- Marcas ---------------------------------------------------------------

  listarMarcas() {
    return this.listar<Marca>('marcas', 'brands');
  }

  crearMarca() {
    return this.crear<Marca, NuevoNombre>('brands');
  }

  renombrarMarca() {
    return this.editar<Marca, NuevoNombre>('brands');
  }

  retirarMarca() {
    return this.retirar('brands');
  }

  // --- Tipos de equipo ------------------------------------------------------

  listarTipos() {
    return this.listar<TipoDeEquipo>('tipos', 'equipment-types');
  }

  /** Un tipo por su identificador, para la pantalla de edicion. */
  detalleTipo(id: Signal<string>) {
    return injectQuery(() => ({
      queryKey: [...CatalogoApi.clave('tipos'), id()],
      queryFn: () =>
        firstValueFrom(this.http.get<TipoDeEquipo>(`${this.api}/equipment-types/${id()}`)),
    }));
  }

  crearTipo() {
    return this.crear<TipoDeEquipo, NuevoTipoDeEquipo>('equipment-types');
  }

  editarTipo() {
    return this.editar<TipoDeEquipo, CambioDeTipoDeEquipo>('equipment-types');
  }

  retirarTipo() {
    return this.retirar('equipment-types');
  }

  /**
   * Declara que se verifica en un tipo: una magnitud por verificacion, con su unidad, su modalidad,
   * cuantas lecturas por punto y en que valores.
   *
   * <p>Tiene ruta propia y no entra en la edicion general porque no es un dato mas: decide como se
   * verifica el equipo, y el backend la separo para que cambiarla sea una decision explicita.
   *
   * <p><b>Se manda la lista entera</b>, y el backend lo exige asi: por separado existiria el instante
   * en que un tipo dice verificar temperatura contra un patron constante sin decir contra que valor.
   * Una lista vacia significa que el tipo deja de verificarse, y las verificaciones anteriores quedan
   * retiradas, no borradas -- con ellas se firmaron reportes.
   *
   * <p>La ruta se llamaba `/verification-mode` y recibia una sola modalidad. Desde el 2026-10-03 es
   * `/verifications` y recibe la lista: un termohigrometro se verifica en dos magnitudes.
   */
  declararVerificaciones() {
    return injectMutation(() => ({
      mutationFn: ({
        id,
        verificaciones,
      }: {
        id: string;
        verificaciones: readonly NuevaVerificacionDeTipo[];
      }) =>
        firstValueFrom(
          this.http.patch<TipoDeEquipo>(`${this.api}/equipment-types/${id}/verifications`, {
            verificaciones,
          }),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  // --- Catalogo metrologico -------------------------------------------------

  /**
   * Las magnitudes que se pueden verificar.
   *
   * <p>Se consulta una vez y se queda en cache: son datos de referencia sembrados que nadie edita, asi
   * que no hay nada que invalidar. El `staleTime` infinito lo dice explicitamente en vez de dejar que
   * TanStack Query recargue por costumbre.
   *
   * <p><b>La clave vive FUERA del prefijo `catalogo`</b>, y no es cosmetica: toda escritura de este
   * servicio invalida `['catalogo']` entero, de modo que con la clave dentro cada alta de una marca
   * habria vuelto a pedir las veinte magnitudes y sus unidades. Lo delato una prueba que acabo con dos
   * peticiones abiertas despues de guardar.
   */
  listarMagnitudes() {
    return injectQuery(() => ({
      queryKey: ['metrologia', 'magnitudes'],
      queryFn: () => firstValueFrom(this.http.get<Magnitud[]>(`${this.api}/magnitudes`)),
      staleTime: Infinity,
    }));
  }

  /**
   * Las unidades de una magnitud.
   *
   * <p>Por magnitud y no todas de golpe, porque es lo que la pantalla necesita: elegida la magnitud, se
   * ofrecen solo sus unidades. El servidor responde 404 si la magnitud no existe, para que una lista
   * vacia no se confunda con una magnitud sin unidades.
   */
  listarUnidades(magnitudId: () => string | undefined) {
    return injectQuery(() => ({
      queryKey: ['metrologia', 'magnitudes', magnitudId(), 'unidades'],
      queryFn: () =>
        firstValueFrom(
          this.http.get<UnidadDeMedida[]>(`${this.api}/magnitudes/${magnitudId()}/units`),
        ),
      enabled: !!magnitudId(),
      staleTime: Infinity,
    }));
  }

  // --- Fabricantes ----------------------------------------------------------

  listarFabricantes() {
    return this.listar<Fabricante>('fabricantes', 'manufacturers');
  }

  crearFabricante() {
    return this.crear<Fabricante, NuevoFabricante>('manufacturers');
  }

  editarFabricante() {
    return this.editar<Fabricante, NuevoFabricante>('manufacturers');
  }

  retirarFabricante() {
    return this.retirar('manufacturers');
  }

  // --- Equipos del catalogo: tipo x marca -----------------------------------

  listarEquipos() {
    return this.listar<EquipoDeCatalogo>('equipos', 'equipments');
  }

  crearEquipo() {
    return this.crear<EquipoDeCatalogo, NuevoEquipoDeCatalogo>('equipments');
  }

  retirarEquipo() {
    return this.retirar('equipments');
  }

  // --- Modelos: equipo x fabricante -----------------------------------------

  listarModelos() {
    return this.listar<Modelo>('modelos', 'models');
  }

  crearModelo() {
    return this.crear<Modelo, NuevoModelo>('models');
  }

  retirarModelo() {
    return this.retirar('models');
  }

  /** El registro INVIMA de un modelo, que tambien tiene ruta propia en el backend. */
  anotarInvima() {
    return injectMutation(() => ({
      mutationFn: ({ id, invima }: { id: string; invima: string }) =>
        firstValueFrom(this.http.patch<Modelo>(`${this.api}/models/${id}/invima`, { invima })),
      onSuccess: () => this.invalidar(),
    }));
  }

  // --- Las cinco piezas se manejan igual ------------------------------------

  private listar<T>(pieza: string, recurso: string) {
    return injectQuery(() => ({
      queryKey: CatalogoApi.clave(pieza),
      queryFn: () => firstValueFrom(this.http.get<T[]>(`${this.api}/${recurso}`)),
    }));
  }

  private crear<T, C>(recurso: string) {
    return injectMutation(() => ({
      mutationFn: (cuerpo: C) => firstValueFrom(this.http.post<T>(`${this.api}/${recurso}`, cuerpo)),
      onSuccess: () => this.invalidar(),
    }));
  }

  private editar<T, C>(recurso: string) {
    return injectMutation(() => ({
      mutationFn: ({ id, cambio }: { id: string; cambio: C }) =>
        firstValueFrom(this.http.patch<T>(`${this.api}/${recurso}/${id}`, cambio)),
      onSuccess: () => this.invalidar(),
    }));
  }

  private retirar(recurso: string) {
    return injectMutation(() => ({
      mutationFn: (id: string) =>
        firstValueFrom(this.http.delete<void>(`${this.api}/${recurso}/${id}`)),
      onSuccess: () => this.invalidar(),
    }));
  }

  private invalidar(): void {
    // Sin `return`: ver la nota de la clase. La recarga ocurre igual, pero no bloquea a quien escribio.
    void this.queryClient.invalidateQueries({ queryKey: CatalogoApi.CLAVE });
  }
}
