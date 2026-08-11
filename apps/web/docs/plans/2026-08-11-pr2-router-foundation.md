# PR 2 — router foundation, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the signal state machine in `app.ts` with the Angular router, with the locale as the first segment of every URL.

**Architecture:** Five tasks, arranged so the riskiest one is as small as it can honestly be. Tasks 1–3 add pieces in isolation — the SSR host allowlist, the two locale guards, two shell components — each leaving the suite green and the app untouched. Task 4 is the switch itself: the route table goes in, `<router-outlet>` replaces the `@if` chain, the state machine is deleted and every component that used to emit navigation injects `Router` instead. Task 5 verifies what the switch was for.

**Tech Stack:** Angular 20.3.17 (standalone, zoneless, SSR), `@jsverse/transloco` 8.4.0, Karma + Jasmine, `RouterTestingHarness`.

**Design spec:** [`2026-08-10-i18n-and-routing-design.md`](./2026-08-10-i18n-and-routing-design.md). This is PR 2 of its five-PR breakdown. PR 1 is merged.

## Global Constraints

- **Branch:** `feat/router-foundation`, cut from `main` @ `0167f7f`. Never commit on `main`.
- **TDD, observed:** write the failing test, run it, *see it fail*, then implement. The red step is reported, not asserted.
- **Every task ends with a commit.** The branch ends with a push and a PR against `main` (root `CLAUDE.md`).
- **Locales:** `fr` is default and source language, `en` second. `SUPPORTED_LOCALES`, `DEFAULT_LOCALE`, `isLocale` already exist in `@i18n/locale`.
- **`LocaleService` already exists** with `negotiate(fromUrl?): Locale`, `use(locale): Observable<Translation>`, `remember(locale): void`, `active: Signal<Locale>`. **`use()` returns the load** — a guard must block on it or SSR serialises untranslated HTML.
- **Angular style** (`apps/web/.claude/CLAUDE.md`): `inject()`, signals, `input()`/`output()` functions, native control flow, `ChangeDetectionStrategy.OnPush`, no NgModules, no `standalone: true`, no `ngClass`/`ngStyle`.
- **Zoneless.** Every `TestBed` provides `provideZonelessChangeDetection()`.
- **TypeScript** `strict: true`, `noPropertyAccessFromIndexSignature: true`, `strictTemplates: true`.
- **The 97 existing specs must stay green** except the two this PR is allowed to rewrite: `app.spec.ts` and `school-detail-page.component.spec.ts`, both in Task 4.
- **Commits** use Conventional Commits and are signed. `1Password: failed to fill whole buffer` means a pending approval — ask, never `--no-gpg-sign`.
- **Always `docker compose down` when a stack has served its purpose.**

## File structure

| File | Task | Responsibility |
|---|---|---|
| `src/server.ts` | 1 | Reads `ALLOWED_HOSTS` and passes it to the engine |
| `src/app/i18n/locale.guard.ts` | 2 | `localeGuard` — validates `:lang`, activates it, or redirects |
| `src/app/i18n/locale.guard.spec.ts` | 2 | |
| `src/app/shared/components/not-found/not-found.component.ts` | 3 | 404 screen |
| `src/app/shared/layouts/auth-layout/auth-layout.component.ts` | 3 | The gradient shell around login/register/verify |
| `src/app/services/registration-flow.service.ts` | 4 | Carries the email from register to verify without putting it in the URL |
| `src/app/app.routes.ts` | 4 | The route table |
| `src/app/app.ts`, `app.html` | 4 | Reduced to `<router-outlet>` |
| 7 feature components + `wishlist-cart` | 4 | Navigate through `Router` instead of emitting |
| `src/app/app.routes.server.ts` | 5 | `RenderMode.Server` |

---

### Task 1: Let the SSR engine render at all

Not cosmetic, and not scope creep: PR 2's acceptance criterion is *"a full page reload on any URL renders the right screen through SSR"*, and that cannot be observed today. `@angular/ssr` rejects every `Host` header with `URL with hostname "..." is not allowed` and silently falls back to client-side rendering, so the Docker stack serves the CSR shell. Verified on 2026-08-10: identical 10229 bytes for any `Host`, no `ng-server-context` marker.

`allowedHosts` can be set in `angular.json` under `architect.build.options.security.allowedHosts`, but that bakes hostnames in at build time — wrong for a container image that may be served under any name. It goes through the environment instead, matching the `API_URL` / `SERVER_URL` pattern already in `server.ts`.

**Files:**
- Modify: `apps/web/src/server.ts`
- Modify: `compose.yml` (add `ALLOWED_HOSTS` to the `web` service)
- Test: `apps/web/src/environments/server-environment.spec.ts` (extend)

**Interfaces:**
- Consumes: nothing.
- Produces: `readAllowedHosts(vars: Record<string, string | undefined>): string[]` exported from `src/environments/server-environment.ts`.

- [ ] **Step 1: Write the failing test**

Append to `apps/web/src/environments/server-environment.spec.ts`:

```ts
describe('readAllowedHosts', () => {
  it('splits a comma-separated list and trims each entry', () => {
    expect(readAllowedHosts({ ALLOWED_HOSTS: 'localhost, meetstudent.app ,web' }))
      .toEqual(['localhost', 'meetstudent.app', 'web']);
  });

  it('defaults to localhost and the compose service name when unset', () => {
    expect(readAllowedHosts({})).toEqual(['localhost', 'web']);
  });

  it('treats an empty or blank value as unset', () => {
    expect(readAllowedHosts({ ALLOWED_HOSTS: '   ' })).toEqual(['localhost', 'web']);
  });

  it('drops empty entries rather than allowing an empty hostname', () => {
    expect(readAllowedHosts({ ALLOWED_HOSTS: 'a,,b,' })).toEqual(['a', 'b']);
  });
});
```

Add `readAllowedHosts` to the existing import from `./server-environment`.

- [ ] **Step 2: Run the test and watch it fail**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/server-environment.spec.ts'
```

Expected: FAIL — `readAllowedHosts is not exported`.

- [ ] **Step 3: Implement**

Append to `apps/web/src/environments/server-environment.ts`:

```ts
/**
 * `@angular/ssr` refuses to render a request whose `Host` is not on this list,
 * falling back to client-side rendering — which silently costs every page its
 * server-rendered HTML. The default covers the two names the stack actually
 * uses: `localhost` from the host machine, `web` from inside the compose
 * network.
 */
export function readAllowedHosts(vars: Record<string, string | undefined>): string[] {
  const raw = vars['ALLOWED_HOSTS']?.trim();
  if (!raw) {
    return ['localhost', 'web'];
  }

  return raw
    .split(',')
    .map((host) => host.trim())
    .filter((host) => host !== '');
}
```

In `apps/web/src/server.ts`, replace the engine construction:

```ts
const angularApp = new AngularNodeAppEngine({
  allowedHosts: readAllowedHosts(process.env),
});
```

and add `readAllowedHosts` to the existing import from `./environments/server-environment`.

In `compose.yml`, under the `web` service's `environment:` block, next to `API_URL` and `SERVER_URL`:

```yaml
      # @angular/ssr renders only for hostnames on this list; anything else
      # silently degrades to client-side rendering.
      ALLOWED_HOSTS: localhost,web
```

- [ ] **Step 4: Run the test and watch it pass**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/server-environment.spec.ts'
```

Expected: PASS.

- [ ] **Step 5: Prove SSR actually renders now**

```bash
docker compose up --build -d
curl -s http://localhost:4200/ | grep -c 'ng-server-context'
docker compose logs web --tail=20
docker compose down
```

Expected: the grep finds at least one `ng-server-context` marker, and the logs no longer contain `is not allowed`. **Record the before/after byte counts in the report** — this is the evidence the task exists for.

- [ ] **Step 6: Commit**

```bash
git add apps/web/src/server.ts apps/web/src/environments/server-environment.ts apps/web/src/environments/server-environment.spec.ts compose.yml
git commit -m "fix(web): allow the SSR engine to render for known hostnames"
```

---

### Task 2: The locale guard

**Files:**
- Create: `apps/web/src/app/i18n/locale.guard.ts`
- Test: `apps/web/src/app/i18n/locale.guard.spec.ts`

**Interfaces:**
- Consumes: `isLocale`, `DEFAULT_LOCALE` from `@i18n/locale`; `LocaleService.negotiate` and `.use` from `@services/locale.service`.
- Produces: `export const localeGuard: CanActivateFn`.

**Why `:lang` is greedy, and why that is the design.** The route table matches `:lang` against *any* first segment, so `/login` binds `lang = 'login'`. The guard turns that into a feature: an unrecognised segment redirects to `/{negotiated}/{original path}`, so every un-prefixed link keeps working instead of 404-ing. One guard, no special cases. If this guard stops redirecting, every un-prefixed URL breaks at once — hence its own spec.

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/app/i18n/locale.guard.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { firstValueFrom, isObservable } from 'rxjs';
import { localeGuard } from './locale.guard';
import { LocaleService } from '@services/locale.service';

function snapshotFor(lang: string | null, url: string) {
  const route = { paramMap: { get: (key: string) => (key === 'lang' ? lang : null) } };
  return {
    route: route as unknown as ActivatedRouteSnapshot,
    state: { url } as RouterStateSnapshot,
  };
}

describe('localeGuard', () => {
  let localeService: jasmine.SpyObj<LocaleService>;

  function run(lang: string | null, url: string) {
    const { route, state } = snapshotFor(lang, url);
    return TestBed.runInInjectionContext(() => localeGuard(route, state));
  }

  beforeEach(() => {
    localeService = jasmine.createSpyObj<LocaleService>('LocaleService', ['negotiate', 'use']);
    localeService.use.and.returnValue(of({}));
    localeService.negotiate.and.returnValue('fr');

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: LocaleService, useValue: localeService },
      ],
    });
  });

  it('activates a supported locale and waits for its translations', async () => {
    const result = run('en', '/en/home');

    expect(isObservable(result)).toBe(true);
    await expectAsync(firstValueFrom(result as never)).toBeResolvedTo(true);
    expect(localeService.use).toHaveBeenCalledWith('en');
  });

  it('redirects an unknown first segment, preserving the rest of the path', () => {
    const result = run('login', '/login');

    expect(result instanceof UrlTree).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/fr/login');
    expect(localeService.use).not.toHaveBeenCalled();
  });

  it('preserves deeper paths when redirecting', () => {
    const result = run('schools', '/schools/12');

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/fr/schools/12');
  });

  it('preserves the query string when redirecting', () => {
    const result = run('schools', '/schools/12?tab=programs');

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree))
      .toBe('/fr/schools/12?tab=programs');
  });

  it('negotiates rather than assuming French', () => {
    localeService.negotiate.and.returnValue('en');

    const result = run('login', '/login');

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/en/login');
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/locale.guard.spec.ts'
```

Expected: FAIL — `Could not resolve "./locale.guard"`.

- [ ] **Step 3: Implement**

Create `apps/web/src/app/i18n/locale.guard.ts`:

```ts
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { isLocale } from '@i18n/locale';
import { LocaleService } from '@services/locale.service';

/**
 * `:lang` matches any first segment, so this guard is what separates a locale
 * from an ordinary path. A recognised locale is activated, and the guard waits
 * on its translations — returning `true` early would let SSR serialise
 * untranslated HTML and flash on hydration.
 *
 * Anything else is treated as a path that lost its prefix and is redirected to
 * the negotiated locale, so un-prefixed links keep working instead of 404-ing.
 */
export const localeGuard: CanActivateFn = (route, state) => {
  const locales = inject(LocaleService);
  const segment = route.paramMap.get('lang');

  if (isLocale(segment)) {
    return locales.use(segment).pipe(map(() => true));
  }

  return inject(Router).parseUrl(`/${locales.negotiate(null)}${state.url}`);
};
```

- [ ] **Step 4: Run the test and watch it pass**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/locale.guard.spec.ts'
```

Expected: PASS, 5 specs.

- [ ] **Step 5: Commit**

```bash
git add apps/web/src/app/i18n/locale.guard.ts apps/web/src/app/i18n/locale.guard.spec.ts
git commit -m "feat(web): guard routes on the locale segment"
```

---

### Task 3: The two shell components

Built before the switch so Task 4 only has to reference them.

**Files:**
- Create: `apps/web/src/app/shared/layouts/auth-layout/auth-layout.component.ts`
- Create: `apps/web/src/app/shared/components/not-found/not-found.component.ts`
- Test: `apps/web/src/app/shared/components/not-found/not-found.component.spec.ts`

**Interfaces:**
- Produces: `AuthLayoutComponent`, `NotFoundComponent`. Task 4's route table imports both.

The auth layout is the gradient wrapper currently written once in `app.html` around `login`, `register` and `verify`. It becomes a layout route with its own outlet rather than the same block repeated three times.

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/app/shared/components/not-found/not-found.component.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { translocoOptions } from '@i18n/transloco.config';
import { NotFoundComponent } from './not-found.component';

describe('NotFoundComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotFoundComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideTransloco(translocoOptions),
      ],
    }).compileComponents();
  });

  it('links home through the active locale rather than a bare slash', () => {
    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a');
    expect(link.getAttribute('href')).toBe('/fr');
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/not-found.component.spec.ts'
```

Expected: FAIL — `Could not resolve "./not-found.component"`.

- [ ] **Step 3: Implement**

Create `apps/web/src/app/shared/layouts/auth-layout/auth-layout.component.ts`:

```ts
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * The gradient shell around login, register and verify. It was the same block
 * written three times in `app.html`; as a layout route it is written once and
 * the child route decides what fills it.
 */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center p-4"
    >
      <div class="w-full max-w-md">
        <router-outlet />
      </div>
    </div>
  `,
})
export class AuthLayoutComponent {}
```

Create `apps/web/src/app/shared/components/not-found/not-found.component.ts`:

```ts
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LocaleService } from '@services/locale.service';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-screen flex flex-col items-center justify-center gap-4 p-4 text-center"
         *transloco="let t">
      <p class="text-6xl font-bold text-indigo-600">404</p>
      <h1 class="text-2xl font-semibold">{{ t('notFound.title') }}</h1>
      <p class="text-gray-600">{{ t('notFound.message') }}</p>
      <a
        [routerLink]="['/', locale.active()]"
        class="mt-2 rounded-lg bg-indigo-600 px-5 py-2.5 text-white hover:bg-indigo-700"
      >
        {{ t('notFound.backHome') }}
      </a>
    </div>
  `,
})
export class NotFoundComponent {
  protected readonly locale = inject(LocaleService);
}
```

Add to **both** `apps/web/src/app/i18n/fr.json` and `en.json`, alongside the existing `common` and `language` namespaces:

```json
  "notFound": {
    "title": "Page introuvable",
    "message": "Cette page n'existe pas ou a été déplacée.",
    "backHome": "Retour à l'accueil"
  }
```

and in `en.json`:

```json
  "notFound": {
    "title": "Page not found",
    "message": "This page does not exist, or it has moved.",
    "backHome": "Back to home"
  }
```

- [ ] **Step 4: Run the test and watch it pass**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/not-found.component.spec.ts'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/web/src/app/shared/layouts apps/web/src/app/shared/components/not-found apps/web/src/app/i18n/fr.json apps/web/src/app/i18n/en.json
git commit -m "feat(web): add the auth layout and the 404 screen"
```

---

### Task 4: The switch

**This is the task the whole PR exists for, and it is large by necessity.** Removing the state machine deprives `school-detail-page` of its `[school]` input, so it must read the route in the same commit; splitting by component leaves `app.html` juggling outputs and an outlet at once, which is worse. The design says it: *"Do it in one focused PR."*

**Files:**
- Create: `apps/web/src/app/services/registration-flow.service.ts` (+ spec)
- Rewrite: `apps/web/src/app/app.routes.ts`, `app.ts`, `app.html`, `app.spec.ts`
- Modify: `apps/web/src/app/app.config.ts` (add `withComponentInputBinding()`)
- Modify: 7 feature components and their templates, plus `shared/components/wishlist-cart/wishlist-cart.component.ts`
- Rewrite: `apps/web/src/app/features/student/school-detail-page/school-detail-page.component.spec.ts`

**The exact output surface being removed** (verified, do not re-derive):

| Component | Removed |
|---|---|
| `landing-page` | `onSchoolClick`, `onLoginClick`, `onRegisterClick` |
| `home-page` | `onLogout`, `onSchoolClick`, `onProfileClick` |
| `school-detail-page` | inputs `school`, `isAuthenticated`; outputs `onBack`, `onLoginPrompt` |
| `profile-page` | `onBack` |
| `login-form` | `onSwitchToRegister`, `onLoginSuccess` |
| `register-form` | `onSwitchToLogin`, `onRegisterSuccess` |
| `email-verification` | input `email`; output `onVerificationSuccess` |
| `wishlist-cart` | input `isAuthenticated`; output `onLoginPrompt` |

`star-rating`'s `onRate` is **not** navigation and stays exactly as it is.

**Three decisions already made — implement them, do not revisit:**

1. **`withComponentInputBinding()`.** The router binds route parameters straight to component inputs, so `school-detail-page` keeps an input — `id`, not `school` — and its spec keeps using `setInput`. This is why the task is smaller than the design first assumed.
2. **The email between register and verify does not go in the URL.** An email address is personal data and URLs are shared, logged and cached. It travels through a signal on `RegistrationFlowService`, and `email-verification` renders an email field when the value is absent — which also fixes reload and direct access.
3. **`wishlist-cart` reads `TokenService` directly.** Today it is used once, in `home-page.component.html:10`, with `[isAuthenticated]="true"` hard-coded and its `onLoginPrompt` bound to nothing — so an unauthenticated action silently does nothing. It injects `TokenService` and `Router` instead.

- [ ] **Step 1: Write the failing test — the route table**

Create `apps/web/src/app/app.routes.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, withComponentInputBinding, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco } from '@jsverse/transloco';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { routes } from './app.routes';

describe('routes', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    // LocaleService is stubbed on purpose. The real one negotiates from
    // `navigator.languages` when no cookie is set, which is the CI machine's
    // locale — Chrome Headless reports en-US, so a real service would send `/`
    // to `/en` here and this suite would pass or fail depending on the machine.
    // Negotiation itself is covered by locale.service.spec.ts and
    // locale.guard.spec.ts; this suite is about the route table.
    const locale = {
      negotiate: () => 'fr' as const,
      use: () => of({}),
      remember: () => undefined,
      active: signal('fr' as const),
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco(translocoOptions),
        { provide: LocaleService, useValue: locale },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  it('redirects the bare root to the negotiated locale', async () => {
    await harness.navigateByUrl('/');

    expect(TestBed.inject(Router).url).toBe('/fr');
  });

  it('redirects an un-prefixed path, keeping the path', async () => {
    await harness.navigateByUrl('/login');

    expect(TestBed.inject(Router).url).toBe('/fr/login');
  });

  it('serves the landing page at the locale root', async () => {
    await harness.navigateByUrl('/fr');

    expect(TestBed.inject(Router).url).toBe('/fr');
  });

  it('renders an unknown path under a valid locale as the 404', async () => {
    await harness.navigateByUrl('/fr/nope');

    expect(harness.routeNativeElement?.textContent).toContain('404');
  });

  it('keeps both locales addressable for the same screen', async () => {
    await harness.navigateByUrl('/en/login');
    expect(TestBed.inject(Router).url).toBe('/en/login');

    await harness.navigateByUrl('/fr/login');
    expect(TestBed.inject(Router).url).toBe('/fr/login');
  });
});
```

- [ ] **Step 2: Run it and watch it fail**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/app.routes.spec.ts'
```

Expected: FAIL — the route table is `[]`, so `/` does not redirect and `Router.url` is `/`.

- [ ] **Step 3: Write the route table**

Replace `apps/web/src/app/app.routes.ts` entirely:

```ts
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
        path: '**',
        loadComponent: () =>
          import('./shared/components/not-found/not-found.component').then(
            (m) => m.NotFoundComponent,
          ),
      },
    ],
  },
];
```

In `apps/web/src/app/app.config.ts`, change the router provider to:

```ts
provideRouter(routes, withComponentInputBinding()),
```

adding `withComponentInputBinding` to the `@angular/router` import.

- [ ] **Step 4: Run it and watch it pass**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/app.routes.spec.ts'
```

Expected: PASS, 5 specs.

- [ ] **Step 5: Reduce `App` to an outlet**

Replace `apps/web/src/app/app.html` entirely with:

```html
<router-outlet />
```

Replace `apps/web/src/app/app.ts` entirely with:

```ts
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
```

Replace `apps/web/src/app/app.spec.ts` entirely — the old second spec asserted an `<h1>` that the landing page used to render through the state machine:

```ts
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });

  it('renders a router outlet and nothing else', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('router-outlet')).toBeTruthy();
  });
});
```

- [ ] **Step 6: Carry the registration email without the URL**

Create `apps/web/src/app/services/registration-flow.service.ts`:

```ts
import { Injectable, signal } from '@angular/core';

/**
 * Holds the address between registering and verifying. Deliberately not a query
 * parameter: an email is personal data, and URLs are shared, logged and cached.
 * It is in-memory only, so a reload clears it — `email-verification` asks for
 * the address when it is empty, which also covers arriving at `/verify` cold.
 */
@Injectable({ providedIn: 'root' })
export class RegistrationFlowService {
  private readonly pending = signal('');

  readonly pendingEmail = this.pending.asReadonly();

  remember(email: string): void {
    this.pending.set(email);
  }

  clear(): void {
    this.pending.set('');
  }
}
```

- [ ] **Step 7: Move every component off outputs and onto `Router`**

For each component in the table above: delete the listed `output()` declarations and their `.emit(...)` calls, inject `Router` and `LocaleService`, and navigate instead. The locale prefix comes from `LocaleService.active()`, so a navigation stays in the current language:

```ts
private readonly router = inject(Router);
private readonly locale = inject(LocaleService);

protected goTo(...segments: (string | number)[]): void {
  void this.router.navigate(['/', this.locale.active(), ...segments]);
}
```

Specific replacements:

- `landing-page`: `onSchoolClick` → `goTo('schools', school.id)`; `onLoginClick` → `goTo('login')`; `onRegisterClick` → `goTo('register')`.
- `home-page`: `onLogout` → `tokenService.clear()` then `goTo()`; `onSchoolClick` → `goTo('schools', school.id)`; `onProfileClick` → `goTo('profile')`. Its template drops `[isAuthenticated]="true"` from `<app-wishlist-cart>`.
- `school-detail-page`: replace `school = input.required<School>()` with `id = input.required<string>()`, bound from `:id` by `withComponentInputBinding()`. Load with `schoolService.getSchool(Number(this.id()))`. Replace the `isAuthenticated` input with `tokenService.isAuthenticated()`. `onBack` → `goTo('home')`; `onLoginPrompt` → `goTo('login')`.
- `profile-page`: `onBack` → `goTo('home')`.
- `login-form`: `onSwitchToRegister` → `goTo('register')`; `onLoginSuccess` → `goTo('home')`.
- `register-form`: `onSwitchToLogin` → `goTo('login')`; `onRegisterSuccess` → `registrationFlow.remember(email)` then `goTo('verify')`.
- `email-verification`: drop the `email` input; read `registrationFlow.pendingEmail()`, and render an email input when it is empty. `onVerificationSuccess` → `registrationFlow.clear()` then `goTo('login')`.
- `wishlist-cart`: drop both members; inject `TokenService` for the authenticated check and navigate to login itself.

Every `(onXxx)` binding disappears from the templates that carried them.

- [ ] **Step 8: Rewrite the school-detail spec**

`school-detail-page.component.spec.ts:55` currently does `setInput('school', mockSchool)`. It becomes `setInput('id', '1')` plus an `HttpTestingController` expectation for `GET .../schools/1`. Keep every existing assertion about programs and ratings that still applies — this is a rewrite of *how the component is fed*, not of what it does.

- [ ] **Step 9: Run the full suite**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless
```

Expected: 0 failures. Every spec other than `app.spec.ts` and `school-detail-page.component.spec.ts` must be untouched — **if a third pre-existing spec needs editing to pass, stop and report it** rather than editing it.

- [ ] **Step 10: Commit**

```bash
git add apps/web/src
git commit -m "feat(web): route every screen instead of switching a signal"
```

---

### Task 5: Render mode, lazy chunks, and the live stack

**Files:**
- Modify: `apps/web/src/app/app.routes.server.ts`

- [ ] **Step 1: Switch the render mode**

`app.routes.server.ts` currently declares `path: '**'` as `RenderMode.Prerender`. That is harmless only while the route table is empty; a parameterised route cannot be prerendered without `getPrerenderParams`. Replace with:

```ts
import { RenderMode, ServerRoute } from '@angular/ssr';

/**
 * Server-rendered, not prerendered: the root redirect needs the request to read
 * `Accept-Language`, and `schools/:id` needs live data. Prerendering the two
 * landing pages is a later SEO optimisation.
 */
export const serverRoutes: ServerRoute[] = [
  {
    path: '**',
    renderMode: RenderMode.Server,
  },
];
```

- [ ] **Step 2: Confirm the build lazy-loads and the budget warning is gone**

```bash
cd apps/web && npm run build
```

Expected: a separate lazy chunk per feature component, and the initial bundle down from its pre-PR 525.62 kB against a 500 kB budget. Quote the chunk table and the new initial-bundle figure in the report.

**If the warning survives, report the number — do not raise the budget in `angular.json` to silence it.** Moving seven feature components out of the initial bundle should clear 500 kB comfortably; if it does not, something is still being pulled in eagerly and that is worth knowing rather than hiding.

- [ ] **Step 3: Verify on the live stack**

```bash
docker compose up --build -d
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' http://localhost:4200/
curl -s http://localhost:4200/fr/schools/1 | grep -c 'ng-server-context'
curl -s -H 'Accept-Language: en' -o /dev/null -w '%{http_code} %{redirect_url}\n' http://localhost:4200/
docker compose logs web --tail=20
docker compose down
```

Expected: `/` redirects to `/fr`; with `Accept-Language: en` it redirects to `/en`; a deep URL renders server-side with an `ng-server-context` marker; no errors in the logs. **Do not skip `docker compose down`.**

- [ ] **Step 4: Commit, push, open the PR**

```bash
git add apps/web/src/app/app.routes.server.ts
git commit -m "feat(web): server-render every route"
git push -u origin feat/router-foundation
```

Then open a PR against `main` describing the navigation rewrite, the locale-prefixed URLs, and the SSR fix from Task 1.

---

## Definition of done

- Every screen has its own URL, and a full reload on any of them renders the right screen through SSR.
- `/` redirects to the negotiated locale; `/login` redirects to `/{locale}/login`.
- `/fr/schools/:id` renders standalone with no prior navigation.
- Browser back and forward work.
- The production build no longer warns about the initial bundle budget.
- The full suite is green, with only `app.spec.ts` and `school-detail-page.component.spec.ts` rewritten.
- No Docker stack left running.

## Out of scope

- `authGuard` on `home` and `profile` — Phase 2 of the frontend completion plan. Routes are declared without it so this PR does one thing.
- Deleting the `@data/*` mock fallbacks — Phase 3.
- Extracting strings from templates — PRs 4 and 5. Only the `notFound` namespace is added here, because Task 3 creates the screen that needs it.
- Per-locale prerendering of the landing pages.

## Carried forward from PR 1

- With the production config (`fallbackLang: 'fr'` plus `useFallbackTranslation`), `transloco.load('en')` emits an **array**, not a single `Translation`, contradicting the library's own typing. `localeGuard` maps it to `true` and never reads the value, so nothing breaks — but do not destructure it.
- `LocaleService.use()` sets the active-locale signal **eagerly**, before the returned observable is subscribed. A caller that drops the observable still gets the right `active()`; what it loses is the load guarantee.
