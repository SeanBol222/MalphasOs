import { Type } from '@angular/core';
import { Routes } from '@angular/router';

/**
 * Una entrada de la navegacion principal, con sus hijas si las tiene.
 *
 * <p><b>Es la unica fuente.</b> Las rutas de la aplicacion se derivan de aqui junto con el menu:
 * anadir un modulo es anadir una entrada, y no hay forma de que el menu ofrezca un destino que no
 * existe ni de que exista uno que el menu no ofrece. Lo fija una prueba.
 *
 * <p>Cuando exista un permiso por destino entrara aqui tambien, de modo que el menu y el guard de ruta
 * lean de la misma lista. Dos listas escritas por separado se desincronizan, y este proyecto ya tiene
 * precedentes documentados de eso — el ultimo, unas autoridades inventadas en las rutas de las sedes.
 */
export interface EntradaDeNavegacion {
  readonly ruta: string;
  readonly etiqueta: string;

  /**
   * Las piezas que cuelgan de esta entrada.
   *
   * <p>Una entrada con hijas <b>no tiene pagina propia</b>: su ruta lleva a la primera hija. El
   * catalogo de equipos es el caso — «catalogo» no es una pantalla, es un sitio donde hay cinco.
   */
  readonly hijos?: readonly EntradaDeNavegacion[];
}

/**
 * Los destinos de la navegacion principal, en el orden en que se muestran.
 */
export const NAVEGACION: readonly EntradaDeNavegacion[] = [
  { ruta: 'inicio', etiqueta: 'Inicio' },
  { ruta: 'clientes', etiqueta: 'Clientes' },
  // Los equipos de cliente van antes que el catalogo, y el orden importa: lo que se consulta a diario
  // es «que equipos hay», no «que marcas existen». Hasta el 2026-09-26 el catalogo ocupaba este sitio y
  // los equipos no tenian listado propio.
  { ruta: 'equipos', etiqueta: 'Equipos' },
  // Las ordenes de trabajo cierran el primer bloque del frontend: el backend las termino el 2026-09-13 y
  // cuatro de sus siete requisitos describen un formulario que hasta ahora no existia.
  { ruta: 'ordenes', etiqueta: 'Órdenes' },
  // El catalogo conserva su entrada porque administrarlo es una tarea aparte: no debe exigir empezar a
  // registrar un equipo para crear una marca.
  //
  // Y se despliega, decidido el 2026-09-26: sus cinco piezas estaban en una sola pagina, una debajo de
  // otra, y con treinta marcas registradas llegar a los fabricantes eran cuatro pantallas. Cada pieza
  // tiene ahora su ruta, de modo que se puede enlazar, recargar y volver con el boton de atras.
  {
    ruta: 'catalogo',
    etiqueta: 'Catálogo',
    hijos: [
      // El tipo va primero, pedido el 2026-09-26: es la pieza que describe QUE es el aparato, y la
      // que mas trabajo cuesta dar de alta -cuatro campos obligatorios y su ficha tecnica-. La marca
      // es un nombre. Y es tambien el orden en que se piensa: primero «un tensiometro», despues «de
      // que marca».
      { ruta: 'tipos', etiqueta: 'Tipos de equipo' },
      { ruta: 'marcas', etiqueta: 'Marcas' },
      { ruta: 'fabricantes', etiqueta: 'Fabricantes' },
      { ruta: 'equipos', etiqueta: 'Equipos del catálogo' },
      { ruta: 'modelos', etiqueta: 'Modelos' },
    ],
  },
] as const;

/**
 * Las rutas hijas del armazon, derivadas de {@link NAVEGACION}.
 *
 * <p>Una entrada con hijas se convierte en una ruta con hijas y una redireccion a la primera: entrar en
 * {@code /catalogo} deja en {@code /catalogo/marcas} en lugar de en una pagina vacia.
 */
export const rutasDeNavegacion: Routes = NAVEGACION.map((entrada) =>
  entrada.hijos
    ? {
        path: entrada.ruta,
        loadComponent: () => cargarPagina(entrada.ruta),
        children: [
          ...entrada.hijos.map((hija) => ({
            path: hija.ruta,
            loadComponent: () => cargarPagina(`${entrada.ruta}/${hija.ruta}`),
          })),
          { path: '', pathMatch: 'full' as const, redirectTo: entrada.hijos[0].ruta },
        ],
      }
    : { path: entrada.ruta, loadComponent: () => cargarPagina(entrada.ruta) },
);

/** Todas las rutas que la navegacion declara, incluidas las hijas, en forma de camino completo. */
export const CAMINOS_DE_NAVEGACION: readonly string[] = NAVEGACION.flatMap((entrada) =>
  entrada.hijos
    ? [entrada.ruta, ...entrada.hijos.map((hija) => `${entrada.ruta}/${hija.ruta}`)]
    : [entrada.ruta],
);

/**
 * Resuelve la pagina de un destino.
 *
 * <p>El mapa es explicito y no una plantilla de ruta calculada: un import dinamico con una cadena
 * construida no lo puede analizar el empaquetador, y el modulo acabaria fuera del paquete o cargado
 * entero.
 */
function cargarPagina(camino: string): Promise<Type<unknown>> {
  switch (camino) {
    case 'inicio':
      return import('../features/home/inicio').then((m) => m.Inicio);
    case 'clientes':
      return import('../features/client/lista/lista-clientes').then((m) => m.ListaClientes);
    case 'equipos':
      return import('../features/equipment/lista/lista-equipos').then((m) => m.ListaEquipos);
    case 'ordenes':
      return import('../features/workOrder/lista/lista-ordenes').then((m) => m.ListaOrdenes);
    case 'catalogo':
      return import('../features/equipment/catalogo/catalogo').then((m) => m.Catalogo);
    case 'catalogo/marcas':
      return import('../features/equipment/catalogo/marcas').then((m) => m.Marcas);
    case 'catalogo/tipos':
      return import('../features/equipment/catalogo/tipos').then((m) => m.TiposDeEquipo);
    case 'catalogo/fabricantes':
      return import('../features/equipment/catalogo/fabricantes').then((m) => m.Fabricantes);
    case 'catalogo/equipos':
      return import('../features/equipment/catalogo/equipos').then((m) => m.EquiposDeCatalogo);
    case 'catalogo/modelos':
      return import('../features/equipment/catalogo/modelos').then((m) => m.Modelos);
    default:
      throw new Error(`El destino de navegacion "${camino}" no tiene pagina asociada`);
  }
}
