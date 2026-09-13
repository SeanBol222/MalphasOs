import { Routes } from '@angular/router';
import { rutasDeNavegacion } from './core/navegacion';
import { sesionIniciada } from './core/auth/guards';

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
        path: 'sin-permiso',
        loadComponent: () => import('./core/layout/sin-permiso').then((m) => m.SinPermiso),
      },
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
    ],
  },
  { path: '**', redirectTo: '' },
];
