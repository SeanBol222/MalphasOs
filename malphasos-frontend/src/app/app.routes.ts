import { Routes } from '@angular/router';
import { rutasDeNavegacion } from './core/navegacion';
import { requiereAutoridad, sesionIniciada } from './core/auth/guards';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./core/layout/shell').then((m) => m.Shell),
    // Todo lo que cuelga del armazon exige sesion. Las rutas publicas, si
    // aparecen, van fuera de aqui.
    canActivate: [sesionIniciada],
    children: [
      ...rutasDeNavegacion,
      {
        // Cuelgan de 'clientes' pero no son entradas del menu: se llega desde el listado.
        // El orden importa: 'nuevo' antes de ':id', o el router leeria "nuevo" como un id.
        path: 'clientes/nuevo',
        loadComponent: () =>
          import('./features/client/nuevo/nuevo-cliente').then((m) => m.NuevoClienteComponent),
        canActivate: [requiereAutoridad('client.write')],
      },
      {
        path: 'clientes/:id/editar',
        loadComponent: () =>
          import('./features/client/editar/editar-cliente').then((m) => m.EditarCliente),
        canActivate: [requiereAutoridad('client.write')],
      },
      {
        path: 'clientes/:id/sedes/nueva',
        loadComponent: () => import('./features/client/sede/nueva-sede').then((m) => m.NuevaSede),
        // Las sedes NO tienen autoridad propia: las protege la del cliente, tal como lo declara
        // HeadquarterRestAdapter. Aqui se puso 'headquarter.write' por simetria con el nombre del
        // recurso y no existe; lo caza la prueba del contrato con el realm.
        canActivate: [requiereAutoridad('client.write')],
      },
      {
        path: 'sedes/:id/encargado',
        loadComponent: () =>
          import('./features/client/sede/nuevo-encargado').then((m) => m.NuevoEncargado),
        // Los encargados los reparte quien puede asignar gente, no quien edita clientes. Lo declara
        // ManagerRestAdapter, y es la misma autoridad con la que se asigna un ingeniero.
        canActivate: [requiereAutoridad('engineer.assign')],
      },
      {
        path: 'sedes/:id/editar',
        loadComponent: () => import('./features/client/sede/editar-sede').then((m) => m.EditarSede),
        canActivate: [requiereAutoridad('client.write')],
      },
      {
        path: 'sedes/:id',
        loadComponent: () => import('./features/client/sede/detalle-sede').then((m) => m.DetalleSede),
        canActivate: [requiereAutoridad('client.read')],
      },
      {
        path: 'clientes/:id',
        loadComponent: () =>
          import('./features/client/detalle/detalle-cliente').then((m) => m.DetalleCliente),
        canActivate: [requiereAutoridad('client.read')],
      },
      {
        path: 'catalogo/tipos/nuevo',
        loadComponent: () =>
          import('./features/equipment/catalogo/nuevo-tipo').then((m) => m.NuevoTipo),
        canActivate: [requiereAutoridad('equipment.write')],
      },
      {
        path: 'catalogo/tipos/:id/editar',
        loadComponent: () =>
          import('./features/equipment/catalogo/editar-tipo').then((m) => m.EditarTipo),
        canActivate: [requiereAutoridad('equipment.write')],
      },
      {
        // La ficha tecnica de un modelo, con pagina propia como la edicion de un tipo: siete datos que
        // se leen de la placa del equipo. Entro el 2026-10-05 para la hoja de vida impresa.
        path: 'catalogo/modelos/:id/ficha',
        loadComponent: () =>
          import('./features/equipment/catalogo/ficha-de-modelo').then((m) => m.FichaDeModelo),
        canActivate: [requiereAutoridad('equipment.write')],
      },
      {
        // Alta desde el listado de equipos: el area se elige en el formulario, encadenada al cliente y
        // su sede. Es la misma pantalla que la de abajo; lo unico que cambia es de donde sale el area.
        path: 'equipos/nuevo',
        loadComponent: () =>
          import('./features/equipment/area/nuevo-equipo').then((m) => m.NuevoEquipo),
        canActivate: [requiereAutoridad('equipment.assign')],
      },
      {
        path: 'areas/:id/equipos/nuevo',
        loadComponent: () =>
          import('./features/equipment/area/nuevo-equipo').then((m) => m.NuevoEquipo),
        // Instalar un equipo en un area exige equipment.assign y no equipment.write: es repartir algo
        // a alguien, no editar el catalogo. Lo declara ClientEquipmentRestAdapter.
        canActivate: [requiereAutoridad('equipment.assign')],
      },
      {
        path: 'areas/:id',
        loadComponent: () =>
          import('./features/equipment/area/detalle-area').then((m) => m.DetalleArea),
        canActivate: [requiereAutoridad('equipment.read')],
      },
      {
        path: 'ordenes/nueva',
        loadComponent: () =>
          import('./features/workOrder/nueva/nueva-orden').then((m) => m.NuevaOrden),
        canActivate: [requiereAutoridad('work-order.write')],
      },
      {
        path: 'ordenes/:id/equipos',
        loadComponent: () =>
          import('./features/workOrder/equipos/agregar-equipos').then((m) => m.AgregarEquipos),
        canActivate: [requiereAutoridad('work-order.write')],
      },
      {
        // Las cuatro altas de persona son la misma pantalla con la clase en la ruta, como el alta de un
        // equipo. Y cada una exige SU autoridad, que es la escalera del backend: la gente de la casa
        // -ingenieros y administradores- va por super.person.write y la del cliente por person.write.
        // El orden importa: estas van antes de 'personas/:id', o el router leeria «nueva» como un id.
        path: 'personas/nueva/ingeniero',
        loadComponent: () =>
          import('./features/person/nueva/nueva-persona').then((m) => m.NuevaPersona),
        data: { clase: 'ingeniero' },
        canActivate: [requiereAutoridad('super.person.write')],
      },
      {
        path: 'personas/nueva/administrador',
        loadComponent: () =>
          import('./features/person/nueva/nueva-persona').then((m) => m.NuevaPersona),
        data: { clase: 'administrador' },
        canActivate: [requiereAutoridad('super.person.write')],
      },
      {
        path: 'personas/nueva/representante',
        loadComponent: () =>
          import('./features/person/nueva/nueva-persona').then((m) => m.NuevaPersona),
        data: { clase: 'representante' },
        canActivate: [requiereAutoridad('person.write')],
      },
      {
        path: 'personas/nueva/encargado',
        loadComponent: () =>
          import('./features/person/nueva/nueva-persona').then((m) => m.NuevaPersona),
        data: { clase: 'encargado' },
        canActivate: [requiereAutoridad('person.write')],
      },
      {
        // La ficha va DESPUES de las cuatro altas, o el router leeria «nueva» como un identificador.
        // Exige solo person.read: quien puede ESCRIBIR sobre esta persona depende de su tipo, y eso no
        // se sabe hasta cargarla. Esa decision vive en el componente, igual que en el backend vive en un
        // bean y no en la anotacion.
        path: 'personas/:id',
        loadComponent: () =>
          import('./features/person/detalle/detalle-persona').then((m) => m.DetallePersona),
        canActivate: [requiereAutoridad('person.read')],
      },
      {
        // La hoja de vida de un equipo: las cuatro secciones de RF-22, de solo lectura. Exige
        // equipment.read porque es un documento del equipo; su historial sale de reportes cerrados,
        // pero ya compilado por el servidor.
        path: 'equipos/:id/hoja-de-vida',
        loadComponent: () =>
          import('./features/equipment/hoja-de-vida/hoja-de-vida').then((m) => m.HojaDeVida),
        canActivate: [requiereAutoridad('equipment.read')],
      },
      {
        // Los reportes de un equipo, incluidos borradores y retirados: lo que se esta haciendo o se
        // hizo al aparato. No es la hoja de vida -esa solo cuenta mantenimientos cerrados-, y por eso
        // conviven. (Este comentario decia que era «la consulta sobre la que se construira la hoja de
        // vida»: la hoja de vida se construyo el 2026-10-04 y no sale de aqui.)
        // Exige report.read porque lo que lista son reportes, no equipos.
        path: 'equipos/:id/historial',
        loadComponent: () =>
          import('./features/report/historial/historial-equipo').then((m) => m.HistorialEquipo),
        canActivate: [requiereAutoridad('report.read')],
      },
      {
        // El reporte cuelga de la orden -se llega desde su ficha, que es lo que pide RF-09- pero tiene
        // ruta propia para poder enlazarlo, recargarlo y volver atras. Es un documento, no un panel.
        path: 'reportes/:id',
        loadComponent: () =>
          import('./features/report/detalle/detalle-reporte').then((m) => m.DetalleReporte),
        canActivate: [requiereAutoridad('report.read')],
      },
      {
        path: 'ordenes/:id',
        loadComponent: () =>
          import('./features/workOrder/detalle/detalle-orden').then((m) => m.DetalleOrden),
        canActivate: [requiereAutoridad('work-order.read')],
      },
      {
        path: 'sin-permiso',
        loadComponent: () => import('./core/layout/sin-permiso').then((m) => m.SinPermiso),
      },
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
    ],
  },
  { path: '**', redirectTo: '' },
];
