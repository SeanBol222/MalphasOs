import { Type } from '@angular/core';
import { Routes } from '@angular/router';
import { requiereAutoridad } from './auth/guards';

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
   * La autoridad que el destino exige, si exige alguna.
   *
   * <p><b>Se usa para dos cosas a la vez, y esa es toda la idea</b>: el menu no pinta lo que la sesion
   * no puede usar, y la ruta no deja entrar por la barra de direcciones. Dos listas escritas por
   * separado se desincronizan, y este proyecto ya tiene el precedente escrito —unas autoridades
   * inventadas en las rutas de las sedes que dejaron la pantalla inalcanzable para todos en silencio—.
   *
   * <p>Entro el <b>2026-10-02</b>, y lo que lo hizo necesario fue «Personas»: es la primera entrada que
   * un grupo legitimo del realm no puede usar —{@code clients} no tiene {@code person.read}—, de modo
   * que sin esto el menu ofrecia un destino que responde 403.
   */
  readonly autoridad?: string;

  /**
   * El bloque del menu en que se pinta: lo que se usa a diario arriba, administrar quien existe y que
   * catalogo hay debajo. Solo lo leen las entradas de primer nivel.
   *
   * <p>Entro el <b>2026-10-10</b> con la barra lateral, que es el armazon que el usuario eligio entre
   * tres propuestas («sala de control»). Con el menu en una fila no hacia falta: la fila no tenia sitio
   * para dos grupos, y por eso mismo se desbordaba en un telefono.
   */
  readonly grupo?: GrupoDeNavegacion;

  /**
   * Las piezas que cuelgan de esta entrada.
   *
   * <p>Una entrada con hijas <b>no tiene pagina propia</b>: su ruta lleva a la primera hija. El
   * catalogo de equipos es el caso — «catalogo» no es una pantalla, es un sitio donde hay cinco.
   */
  readonly hijos?: readonly EntradaDeNavegacion[];
}

/** Los dos bloques del menu, en el orden en que se pintan. */
export type GrupoDeNavegacion = 'operacion' | 'administracion';

export const GRUPOS_DE_NAVEGACION: readonly {
  readonly id: GrupoDeNavegacion;
  readonly etiqueta: string;
}[] = [
  { id: 'operacion', etiqueta: 'Operación' },
  { id: 'administracion', etiqueta: 'Administración' },
];

/**
 * Los destinos de la navegacion principal, en el orden en que se muestran.
 */
export const NAVEGACION: readonly EntradaDeNavegacion[] = [
  // Inicio no exige nada: es la unica pagina que cualquiera con sesion puede ver.
  { ruta: 'inicio', etiqueta: 'Inicio', grupo: 'operacion' },
  // Las ordenes van primero desde el 2026-10-10, con la barra lateral: son el trabajo del dia, y el
  // resto de la operacion -equipos y clientes- se consulta desde ellas. Hasta entonces iban cuartas.
  // Entraron el 2026-09-27: el backend las termino el 2026-09-13 y cuatro de sus siete requisitos
  // describen un formulario que hasta entonces no existia.
  {
    ruta: 'ordenes',
    etiqueta: 'Órdenes de trabajo',
    autoridad: 'work-order.read',
    grupo: 'operacion',
  },
  // Los equipos de cliente van antes que el catalogo, y el orden importa: lo que se consulta a diario
  // es «que equipos hay», no «que marcas existen». Hasta el 2026-09-26 el catalogo ocupaba este sitio y
  // los equipos no tenian listado propio.
  { ruta: 'equipos', etiqueta: 'Equipos', autoridad: 'equipment.read', grupo: 'operacion' },
  { ruta: 'clientes', etiqueta: 'Clientes', autoridad: 'client.read', grupo: 'operacion' },
  // Las personas van despues del trabajo diario y antes del catalogo: administrar quien existe es una
  // tarea de administracion, como el catalogo, y no algo que se consulte a cada rato. Entro el
  // 2026-10-02, cuando el modulo dejo de tener catorce operaciones y ninguna pantalla.
  { ruta: 'personas', etiqueta: 'Personas', autoridad: 'person.read', grupo: 'administracion' },
  // El catalogo conserva su entrada porque administrarlo es una tarea aparte: no debe exigir empezar a
  // registrar un equipo para crear una marca.
  //
  // Y se despliega, decidido el 2026-09-26: sus cinco piezas estaban en una sola pagina, una debajo de
  // otra, y con treinta marcas registradas llegar a los fabricantes eran cuatro pantallas. Cada pieza
  // tiene ahora su ruta, de modo que se puede enlazar, recargar y volver con el boton de atras.
  {
    ruta: 'catalogo',
    etiqueta: 'Catálogo',
    // Se ofrece a quien lo administra, equipment.write, y no a quien puede leerlo (2026-10-26, pedido
    // por el usuario): el representante de un cliente tiene equipment.read para ver SUS equipos y su
    // hoja de vida, y el catalogo de la empresa no es asunto suyo. Lo oculta el menu y lo cierra la
    // ruta; el API sigue sirviendolo con equipment.read, porque los nombres de tipo, marca y modelo de
    // sus equipos salen de ahi.
    autoridad: 'equipment.write',
    grupo: 'administracion',
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
      // Los protocolos de mantenimiento, del 2026-10-10: el catalogo. Su asignacion a cada tipo, con su
      // prioridad, se hace en la edicion del tipo.
      { ruta: 'protocolos', etiqueta: 'Protocolos' },
      // Los equipos patrón, del 2026-10-14: contra ellos se toman las lecturas de una verificación.
      { ruta: 'patrones', etiqueta: 'Patrones' },
    ],
  },
  // La empresa que presta el servicio, del 2026-10-17: el membrete de los documentos. Va al final porque
  // se configura una vez y se toca poco.
  { ruta: 'empresa', etiqueta: 'Empresa', autoridad: 'company.read', grupo: 'administracion' },
] as const;

/**
 * Las rutas hijas del armazon, derivadas de {@link NAVEGACION}.
 *
 * <p>Una entrada con hijas se convierte en una ruta con hijas y una redireccion a la primera: entrar en
 * {@code /catalogo} deja en {@code /catalogo/marcas} en lugar de en una pagina vacia.
 */
export const rutasDeNavegacion: Routes = NAVEGACION.map((entrada) => {
  // El guard sale de la MISMA entrada que pinta el menu. Ocultar sin proteger la ruta dejaria el
  // destino alcanzable escribiendo la direccion a mano, y proteger sin ocultar ofreceria un enlace
  // que acaba en «sin permiso».
  const guardas = entrada.autoridad ? { canActivate: [requiereAutoridad(entrada.autoridad)] } : {};

  return entrada.hijos
    ? {
        path: entrada.ruta,
        loadComponent: () => cargarPagina(entrada.ruta),
        ...guardas,
        children: [
          ...entrada.hijos.map((hija) => ({
            path: hija.ruta,
            loadComponent: () => cargarPagina(`${entrada.ruta}/${hija.ruta}`),
          })),
          { path: '', pathMatch: 'full' as const, redirectTo: entrada.hijos[0].ruta },
        ],
      }
    : { path: entrada.ruta, loadComponent: () => cargarPagina(entrada.ruta), ...guardas };
});

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
    case 'personas':
      return import('../features/person/lista/lista-personas').then((m) => m.ListaPersonas);
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
    case 'catalogo/protocolos':
      return import('../features/equipment/catalogo/protocolos').then((m) => m.Protocolos);
    case 'catalogo/patrones':
      return import('../features/equipment/catalogo/patrones').then((m) => m.Patrones);
    case 'empresa':
      return import('../features/company/empresa').then((m) => m.Empresa);
    default:
      throw new Error(`El destino de navegacion "${camino}" no tiene pagina asociada`);
  }
}
