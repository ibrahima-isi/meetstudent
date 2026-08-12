import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { localeGuard } from '@i18n/locale.guard';
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
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/public/landing-page/landing-page.component').then(
            (m) => m.LandingPageComponent,
          ),
      },
      {
        path: 'home',
        loadComponent: () =>
          import('./features/student/home-page/home-page.component').then(
            (m) => m.HomePageComponent,
          ),
      },
      {
        path: 'schools/:id',
        loadComponent: () =>
          import(
            './features/student/school-detail-page/school-detail-page.component'
          ).then((m) => m.SchoolDetailPageComponent),
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/student/profile-page/profile-page.component').then(
            (m) => m.ProfilePageComponent,
          ),
      },
      {
        // Prefix match, so the three auth screens render inside the shared shell.
        path: '',
        loadComponent: () =>
          import('./shared/layouts/auth-layout/auth-layout.component').then(
            (m) => m.AuthLayoutComponent,
          ),
        children: [
          {
            path: 'login',
            loadComponent: () =>
              import('./features/auth/login-form/login-form.component').then(
                (m) => m.LoginFormComponent,
              ),
          },
          {
            path: 'register',
            loadComponent: () =>
              import('./features/auth/register-form/register-form.component').then(
                (m) => m.RegisterFormComponent,
              ),
          },
          {
            path: 'verify',
            loadComponent: () =>
              import(
                './features/auth/email-verification/email-verification.component'
              ).then((m) => m.EmailVerificationComponent),
          },
        ],
      },
      {
        // Inside `:lang`, not at the top level: the 404 renders through
        // `*transloco`, so it must sit behind `localeGuard` or the first SSR
        // paint can serialise an empty page with nothing loaded.
        path: '**',
        loadComponent: () =>
          import('./shared/components/not-found/not-found.component').then(
            (m) => m.NotFoundComponent,
          ),
      },
    ],
  },
];
