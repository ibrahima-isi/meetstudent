import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { localeGuard } from '@i18n/locale.guard';
import { authGuard } from './guards/auth.guard';
import { LocaleService } from '@services/locale.service';

/**
 * Every screen lives under `/:lang`. The segment is greedy on purpose — it
 * matches any first segment, and `localeGuard` decides whether it is a locale
 * or a path that lost its prefix. See the guard for why.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: () => `/${inject(LocaleService).negotiate(null)}`,
  },
  {
    path: ':lang',
    canActivate: [localeGuard],
    children: [
      {
        path: 'home',
        title: 'pageTitle.home',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/student/home-page/home-page.component').then(
            (m) => m.HomePageComponent,
          ),
      },
      {
        path: 'schools/:id',
        title: 'pageTitle.school',
        canActivate: [authGuard],
        loadComponent: () =>
          import(
            './features/student/school-detail-page/school-detail-page.component'
          ).then((m) => m.SchoolDetailPageComponent),
      },
      {
        path: 'profile',
        title: 'pageTitle.profile',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/student/profile-page/profile-page.component').then(
            (m) => m.ProfilePageComponent,
          ),
      },
      {
        // Pathless, prefix match: the public pages share one frame (skip link + navbar).
        // Tried after the guarded routes above; the router backtracks out of it for `home` etc.
        path: '',
        loadComponent: () =>
          import('./shared/layouts/public-shell/public-shell.component').then(
            (m) => m.PublicShellComponent,
          ),
        children: [
          {
            // Temporary: serves the catalogue until the new landing replaces it (plan Task 13).
            path: '',
            pathMatch: 'full',
            title: 'pageTitle.landing',
            loadComponent: () =>
              import('./features/public/schools-page/schools-page.component').then(
                (m) => m.SchoolsPageComponent,
              ),
          },
          {
            path: 'schools',
            pathMatch: 'full',
            title: 'pageTitle.schools',
            loadComponent: () =>
              import('./features/public/schools-page/schools-page.component').then(
                (m) => m.SchoolsPageComponent,
              ),
          },
          {
            path: '',
            loadComponent: () =>
              import('./shared/layouts/auth-layout/auth-layout.component').then(
                (m) => m.AuthLayoutComponent,
              ),
            children: [
              {
                path: 'login',
                title: 'pageTitle.login',
                loadComponent: () =>
                  import('./features/auth/login-form/login-form.component').then(
                    (m) => m.LoginFormComponent,
                  ),
              },
              {
                path: 'register',
                title: 'pageTitle.register',
                loadComponent: () =>
                  import('./features/auth/register-form/register-form.component').then(
                    (m) => m.RegisterFormComponent,
                  ),
              },
            ],
          },
        ],
      },
      {
        // Inside `:lang`, not at the top level: the 404 renders through
        // `*transloco`, so it must sit behind `localeGuard` or the first SSR
        // paint can serialise an empty page with nothing loaded.
        path: '**',
        title: 'pageTitle.notFound',
        loadComponent: () =>
          import('./shared/components/not-found/not-found.component').then(
            (m) => m.NotFoundComponent,
          ),
      },
    ],
  },
];
