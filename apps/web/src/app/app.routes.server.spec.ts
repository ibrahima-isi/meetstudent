import { Route } from '@angular/router';
import { RenderMode } from '@angular/ssr';
import { authGuard } from './guards/auth.guard';
import { routes } from './app.routes';
import { serverRoutes } from './app.routes.server';

/** Full paths (`:lang/home`) of every route behind `authGuard`, however deeply nested. */
function guardedPaths(list: Route[], prefix = ''): string[] {
  return list.flatMap((route) => {
    const path = [prefix, route.path].filter(Boolean).join('/');
    const own = route.canActivate?.includes(authGuard) ? [path] : [];
    return [...own, ...guardedPaths(route.children ?? [], path)];
  });
}

describe('server routes', () => {
  const modeOf = (path: string) => serverRoutes.find((r) => r.path === path)?.renderMode;

  it('finds the guarded screens it is checking', () => {
    expect(guardedPaths(routes).sort()).toEqual([':lang/home', ':lang/profile', ':lang/schools/:id']);
  });

  it('renders every guarded screen in the browser: the session lives in localStorage, which the server cannot see', () => {
    for (const path of guardedPaths(routes)) {
      expect(modeOf(path)).withContext(path).toBe(RenderMode.Client);
    }
  });

  it('keeps the public pages server-rendered for SEO', () => {
    expect(modeOf('**')).toBe(RenderMode.Server);
  });

  it('lists the client-rendered screens before the catch-all, which would otherwise win', () => {
    const catchAll = serverRoutes.findIndex((r) => r.path === '**');
    const lastClient = serverRoutes.map((r) => r.renderMode).lastIndexOf(RenderMode.Client);

    expect(lastClient).toBeLessThan(catchAll);
  });
});
