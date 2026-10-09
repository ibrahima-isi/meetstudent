import { Routes } from '@angular/router';
import { adminGuard } from '@guards/admin.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login-page/login-page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'moderation' },
      {
        path: 'moderation',
        loadComponent: () => import('./features/moderation/moderation-page').then((m) => m.ModerationPage),
      },
      {
        path: 'schools',
        loadComponent: () => import('./features/schools/schools-page').then((m) => m.SchoolsPage),
      },
      {
        path: 'programs',
        loadComponent: () => import('./features/programs/programs-page').then((m) => m.ProgramsPage),
      },
      {
        path: 'tags',
        loadComponent: () => import('./features/tags/tags-page').then((m) => m.TagsPage),
      },
    ],
  },
  {
    path: '**',
    loadComponent: () => import('./features/not-found/not-found-page').then((m) => m.NotFoundPage),
  },
];
