import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  injectQueryClient,
  keepPreviousData,
} from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DocumentoEnPdf, pedirPdf } from '../../core/api/pdf';
import {
  CambioDeEquipoDeCliente,
  ConsultaDeEquipos,
  EquipoDeCliente,
  EquiposDeArea,
  FotoDeEquipo,
  HojaDeVida,
  NuevoEquipoDeCliente,
  ResumenDeEquipos,
} from '../../core/api/tipos';
import { SedeApi } from '../client/sede-api';

/**
 * La unica puerta al servidor para los equipos instalados en un area.
 *
 * <p>Es el final de la cadena del catalogo: aqui un modelo deja de ser una entrada de catalogo y pasa
 * a ser <b>una maquina concreta</b>, con su serie, su numero de inventario y su fecha de compra. Es
 * tambien lo que una orden de trabajo toca.
 *
 * <p><b>El alta exige {@code equipment.assign} y no {@code equipment.write}</b>, y esa diferencia es
 * del backend: instalar un equipo en un area es repartir algo a alguien, no editar un catalogo. La
 * misma autoridad protege el traslado.
 *
 * <p>Las claves cuelgan de la de las sedes —{@code ['sedes', idSede, 'areas', idArea, 'equipos']}—,
 * de modo que lo que invalida una sede alcanza a sus equipos. Se pide el area, no la sede, porque es
 * del area de donde cuelgan.
 */
@Injectable({ providedIn: 'root' })
export class EquipoApi {
  private readonly http = inject(HttpClient);
  private readonly queryClient = injectQueryClient();

  private readonly api = `${environment.api}/v1/api`;

  static readonly CLAVE = ['equipos-de-cliente'] as const;

  /**
   * La hoja de vida va fuera de {@link CLAVE} a proposito: su cuarta seccion cambia cuando se cierra un
   * reporte, que es una escritura de otro modulo. {@code ReporteApi} invalida este prefijo al cerrar,
   * y no tendria por que conocer las claves de los equipos.
   */
  static readonly CLAVE_HOJA_DE_VIDA = ['hoja-de-vida'] as const;

  /**
   * El listado del rediseño cuelga de {@link CLAVE}, de modo que dar de alta o de baja un equipo lo
   * recarga. Cerrar un reporte cambia su ultimo servicio, y por eso {@code ReporteApi} lo invalida
   * tambien, igual que la hoja de vida.
   */
  static readonly CLAVE_RESUMEN = [...EquipoApi.CLAVE, 'resumen'] as const;

  static claveDeArea(idArea: string) {
    return [...EquipoApi.CLAVE, idArea] as const;
  }

  /**
   * Todos los equipos de cliente registrados.
   *
   * <p>Sin filtros de consulta, porque el API no los ofrece. Lo que <b>si</b> filtra desde el
   * 2026-10-04 es el servidor, por dueno: un supervisor del cliente recibe solo los equipos de sus
   * clientes. (Este comentario decia que la ausencia de filtros era «la misma que el filtrado por
   * dueno»: cierto hasta ese dia.) Con miles de equipos seguira haciendo falta paginacion.
   */
  listarTodos() {
    return injectQuery(() => ({
      queryKey: EquipoApi.CLAVE,
      queryFn: () =>
        firstValueFrom(this.http.get<EquipoDeCliente[]>(`${this.api}/client-equipments`)),
    }));
  }

  /**
   * La hoja de vida en PDF, compuesta y convertida en el servidor (2026-10-18). Cuelga de la clave de la
   * hoja de vida: lo que la caduca —cerrar un reporte, corregir el catálogo— caduca también su PDF.
   */
  hojaDeVidaEnPdf(id: Signal<string>) {
    return injectQuery(() => ({
      queryKey: [...EquipoApi.CLAVE_HOJA_DE_VIDA, id(), 'pdf'],
      queryFn: (): Promise<DocumentoEnPdf> =>
        pedirPdf(
          this.http,
          `${this.api}/client-equipments/${id()}/life-sheet/pdf`,
          'hoja-de-vida.pdf',
        ),
      enabled: !!id(),
    }));
  }

  /**
   * La foto del equipo, que viaja en la hoja de vida (V58): un {@code data:} URI, o nula. Cuelga de la
   * clave de la hoja de vida, como su PDF, de modo que poner o quitar la foto caduca los dos. Solo se pide
   * si {@code activa}: quien no puede cambiarla ya la ve en el PDF.
   */
  fotoDe(id: Signal<string>, activa: Signal<boolean>) {
    return injectQuery(() => ({
      queryKey: [...EquipoApi.CLAVE_HOJA_DE_VIDA, id(), 'foto'],
      queryFn: async () => {
        const hoja = await firstValueFrom(
          this.http.get<HojaDeVida>(`${this.api}/client-equipments/${id()}/life-sheet`),
        );
        return hoja.identificacion?.foto ?? null;
      },
      enabled: !!id() && activa(),
    }));
  }

  /** Pone o cambia la foto: un PNG ya reducido, en el campo {@code archivo}. */
  ponerFoto() {
    return injectMutation(() => ({
      mutationFn: ({ id, png }: { id: string; png: Blob }) => {
        const formulario = new FormData();
        formulario.append('archivo', png, 'foto.png');

        return firstValueFrom(
          this.http.patch<FotoDeEquipo>(`${this.api}/client-equipments/${id}/photo`, formulario),
        );
      },
      onSuccess: () => this.invalidarHojaDeVida(),
    }));
  }

  quitarFoto() {
    return injectMutation(() => ({
      mutationFn: (id: string) =>
        firstValueFrom(this.http.delete<void>(`${this.api}/client-equipments/${id}/photo`)),
      onSuccess: () => this.invalidarHojaDeVida(),
    }));
  }

  listarDe(idArea: Signal<string>) {
    return injectQuery(() => ({
      queryKey: EquipoApi.claveDeArea(idArea()),
      queryFn: () =>
        firstValueFrom(
          this.http.get<EquipoDeCliente[]>(`${this.api}/service-areas/${idArea()}/equipments`),
        ),
    }));
  }

  registrar() {
    return injectMutation(() => ({
      mutationFn: ({ idArea, equipo }: { idArea: string; equipo: NuevoEquipoDeCliente }) =>
        firstValueFrom(
          this.http.post<EquipoDeCliente>(`${this.api}/service-areas/${idArea}/equipments`, equipo),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Corrige los datos administrativos de un equipo: inventario, fecha y valor de compra.
   *
   * <p>La serie y el modelo no se editan, y no es un olvido del contrato: la serie identifica a la
   * maquina y el modelo dice que maquina es. Cambiar cualquiera de los dos seria decir que esta fila
   * describe otro equipo, no corregir este.
   */
  editar() {
    return injectMutation(() => ({
      mutationFn: ({ id, cambio }: { id: string; cambio: CambioDeEquipoDeCliente }) =>
        firstValueFrom(
          this.http.patch<EquipoDeCliente>(`${this.api}/client-equipments/${id}`, cambio),
        ),
      onSuccess: () => this.invalidar(),
    }));
  }

  /**
   * Una pagina del listado de equipos, filtrada y paginada en el servidor (2026-10-11).
   *
   * <p>Mientras llega la pagina nueva se queda la anterior a la vista: cambiar de pagina o escribir en
   * el buscador no debe vaciar la tabla en cada tecla.
   */
  resumen(consulta: Signal<ConsultaDeEquipos>) {
    return injectQuery(() => {
      const { q, idCliente, idTipoEquipo, resultado, dadosDeBaja, pagina, tamano } = consulta();
      const parametros: Record<string, string | number | boolean> = { pagina, tamano };
      if (q) parametros['q'] = q;
      if (idCliente) parametros['idCliente'] = idCliente;
      if (idTipoEquipo) parametros['idTipoEquipo'] = idTipoEquipo;
      if (resultado) parametros['resultado'] = resultado;
      if (dadosDeBaja) parametros['dadosDeBaja'] = true;

      return {
        queryKey: [...EquipoApi.CLAVE_RESUMEN, parametros],
        queryFn: () =>
          firstValueFrom(
            this.http.get<ResumenDeEquipos>(`${this.api}/client-equipments/overview`, {
              params: parametros,
            }),
          ),
        placeholderData: keepPreviousData,
      };
    });
  }

  /**
   * Estos equipos y ningun otro, con todo lo que el listado sabe de cada uno: lo pide la ficha de una
   * orden para su alcance. El servidor no filtra por baja cuando se piden por identificador —un equipo
   * dado de baja sigue en la orden en la que entro—.
   *
   * <p><b>En tandas de {@code 100}</b>, el maximo de una pagina, y reunidas en una sola respuesta. Hasta el
   * 2026-10-27 iba en una peticion, y una orden o una sede con mas de cien equipos se quedaba sin los
   * datos de los que pasaban de cien; lo destapo pedirlos para elegir los equipos de una sede entera.
   */
  resumenDe(ids: Signal<readonly string[]>) {
    return injectQuery(() => {
      const ordenados = [...ids()].sort();

      return {
        queryKey: [...EquipoApi.CLAVE_RESUMEN, { ids: ordenados }],
        queryFn: async (): Promise<ResumenDeEquipos> => {
          const tandas: string[][] = [];
          for (let i = 0; i < ordenados.length; i += EquipoApi.POR_PAGINA) {
            tandas.push(ordenados.slice(i, i + EquipoApi.POR_PAGINA));
          }
          const paginas = await Promise.all(
            tandas.map((tanda) =>
              firstValueFrom(
                this.http.get<ResumenDeEquipos>(`${this.api}/client-equipments/overview`, {
                  params: { ids: tanda.join(','), tamano: EquipoApi.POR_PAGINA },
                }),
              ),
            ),
          );
          const filas = paginas.flatMap((pagina) => pagina.filas ?? []);

          return { filas, pagina: 0, tamano: filas.length, total: filas.length };
        },
        enabled: ordenados.length > 0,
      };
    });
  }

  /** El maximo de filas que el listado del servidor entrega por pagina. */
  static readonly POR_PAGINA = 100;

  /**
   * Los equipos en servicio contados por area, de todo el alcance o de un cliente (2026-10-11). Cuelga
   * de {@link CLAVE_RESUMEN}: dar de alta un equipo o cerrar un reporte lo recarga.
   *
   * <p>Admite una senal que la apaga: lo piden pantallas de clientes, y quien lee clientes no siempre
   * puede leer equipos.
   */
  conteoPorArea(idCliente?: Signal<string>, habilitada?: Signal<boolean>) {
    return injectQuery(() => {
      const id = idCliente?.() ?? '';

      return {
        queryKey: [...EquipoApi.CLAVE_RESUMEN, 'por-area', id || 'todos'],
        queryFn: () =>
          firstValueFrom(
            this.http.get<EquiposDeArea[]>(`${this.api}/client-equipments/summary`, {
              params: id ? { idCliente: id } : {},
            }),
          ),
        enabled: habilitada ? habilitada() : true,
      };
    });
  }

  /** Da de baja el equipo. Como todo aqui, queda inactivo con su historia intacta. */
  darDeBaja() {
    return injectMutation(() => ({
      mutationFn: (id: string) =>
        firstValueFrom(this.http.delete<void>(`${this.api}/client-equipments/${id}`)),
      onSuccess: () => this.invalidar(),
    }));
  }

  private async invalidarHojaDeVida(): Promise<void> {
    await this.queryClient.invalidateQueries({ queryKey: EquipoApi.CLAVE_HOJA_DE_VIDA });
  }

  private async invalidar(): Promise<void> {
    await Promise.all([
      this.queryClient.invalidateQueries({ queryKey: EquipoApi.CLAVE }),
      // Las areas viven bajo la sede, y cuantos equipos tiene un area es algo que esa rama muestra.
      this.queryClient.invalidateQueries({ queryKey: SedeApi.CLAVE }),
    ]);
  }
}
