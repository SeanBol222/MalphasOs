import { Routes } from '@angular/router';

/**
 * Una entrada de la navegacion principal.
 *
 * <p>Cuando exista la sesion, aqui entrara la autoridad que exige cada destino,
 * de modo que el menu y el guard de ruta lean de la misma lista. Dos listas
 * escritas por separado se desincronizan, y este proyecto ya tiene precedentes
 * documentados de eso en el backend.
 */
export interface EntradaDeNavegacion {
  readonly ruta: string;
  readonly etiqueta: string;
}

/**
 * Los destinos de la navegacion principal, en el orden en que se muestran.
 *
 * <p><b>Es la unica fuente.</b> Las rutas de la aplicacion se derivan de aqui
 * junto con el menu: anadir un modulo es anadir una entrada, y no hay forma de
 * que el menu ofrezca un destino que no existe ni de que exista uno que el menu
 * no ofrece. Lo fija una prueba.
 */
export const NAVEGACION: readonly EntradaDeNavegacion[] = [
  { ruta: 'inicio', etiqueta: 'Inicio' },
] as const;

/** Las rutas hijas del armazon, derivadas de {@link NAVEGACION}. */
export const rutasDeNavegacion: Routes = NAVEGACION.map(({ ruta }) => ({
  path: ruta,
  loadComponent: () => cargarPagina(ruta),
}));

/**
 * Resuelve la pagina de una entrada.
 *
 * <p>El mapa es explicito y no una plantilla de ruta calculada: un import
 * dinamico con una cadena construida no lo puede analizar el empaquetador, y
 * el modulo acabaria fuera del paquete o cargado entero.
 */
function cargarPagina(ruta: string): Promise<any> {
  switch (ruta) {
    case 'inicio':
      return import('../features/home/inicio').then((m) => m.Inicio);
    default:
      throw new Error(`La entrada de navegacion "${ruta}" no tiene pagina asociada`);
  }
}
