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
        // Pathless, prefix match: every page except the 404 shares one frame (skip
        // link + navbar), the signed-in ones included. They sit before the landing
        // `''` so `home`, `profile` and `schools/:id` are tried first; each carries
        // its own `authGuard`, which still yields a `returnUrl` under this lazy parent.
        path: '',
        loadComponent: () =>
          import('./shared/layouts/public-shell/public-shell.component').then(
            (m) => m.PublicShellComponent,
          ),
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
            // Public, and before `schools/:id` or "compare" would be taken for an id
            // and bounced to the login.
            path: 'schools/compare',
            pathMatch: 'full',
            title: 'pageTitle.compare',
            loadComponent: () =>
              import('./features/public/schools-compare-page/schools-compare-page.component').then(
                (m) => m.SchoolsComparePageComponent,
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
            path: '',
            pathMatch: 'full',
            title: 'pageTitle.landing',
            loadComponent: () =>
              import('./features/public/landing-page/landing-page.component').then(
                (m) => m.LandingPageComponent,
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
            path: 'programs',
            pathMatch: 'full',
            title: 'pageTitle.programs',
            loadComponent: () =>
              import('./features/public/programs-page/programs-page.component').then(
                (m) => m.ProgramsPageComponent,
              ),
          },
          {
            path: 'courses',
            pathMatch: 'full',
            title: 'pageTitle.courses',
            loadComponent: () =>
              import('./features/public/courses-page/courses-page.component').then(
                (m) => m.CoursesPageComponent,
              ),
          },
          {
            path: 'accreditations',
            pathMatch: 'full',
            title: 'pageTitle.accreditations',
            loadComponent: () =>
              import('./features/public/accreditations-page/accreditations-page.component').then(
                (m) => m.AccreditationsPageComponent,
              ),
          },
          {
            path: 'about',
            title: 'pageTitle.about',
            loadComponent: () =>
              import('./features/public/about-page/about-page.component').then((m) => m.AboutPageComponent),
          },
          {
            path: 'contact',
            title: 'pageTitle.contact',
            loadComponent: () =>
              import('./features/public/contact-page/contact-page.component').then((m) => m.ContactPageComponent),
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
              {
                path: 'forgot-password',
                title: 'pageTitle.forgotPassword',
                loadComponent: () =>
                  import('./features/auth/forgot-password/forgot-password.component').then(
                    (m) => m.ForgotPasswordComponent,
                  ),
              },
              {
                // Public, but rendered in the browser (see app.routes.server.ts).
                path: 'reset-password',
                title: 'pageTitle.resetPassword',
                loadComponent: () =>
                  import('./features/auth/reset-password/reset-password.component').then(
                    (m) => m.ResetPasswordComponent,
                  ),
              },
              {
                // Same as reset-password: public, token in the fragment, rendered in the browser.
                path: 'verify-email',
                title: 'pageTitle.verifyEmail',
                loadComponent: () =>
                  import('./features/auth/verify-email/verify-email.component').then(
                    (m) => m.VerifyEmailComponent,
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
