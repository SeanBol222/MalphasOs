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
