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
        path: 'sin-permiso',
        loadComponent: () => import('./core/layout/sin-permiso').then((m) => m.SinPermiso),
      },
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
    ],
  },
  { path: '**', redirectTo: '' },
];
