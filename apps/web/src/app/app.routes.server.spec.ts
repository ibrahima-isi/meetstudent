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

function allRoutes(list: Route[], prefix = ''): { path: string; route: Route }[] {
  return list.flatMap((route) => {
    const path = [prefix, route.path].filter(Boolean).join('/');
    return [{ path, route }, ...allRoutes(route.children ?? [], path)];
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

  it('renders the reset page in the browser although it is public: its token lives in the URL fragment, which the server never receives', () => {
    expect(modeOf(':lang/reset-password')).toBe(RenderMode.Client);
    expect(guardedPaths(routes)).not.toContain(':lang/reset-password');
  });

  it('declares the reset page as a route of its own, open to anonymous visitors', () => {
    const paths = allRoutes(routes);

    expect(paths.find((r) => r.path === ':lang/reset-password')?.route.canActivate).toBeUndefined();
    expect(paths.map((r) => r.path)).toContain(':lang/forgot-password');
    expect(modeOf(':lang/forgot-password')).toBeUndefined();
  });

  it('renders the verification page in the browser although it is public: its token lives in the URL fragment too', () => {
    expect(modeOf(':lang/verify-email')).toBe(RenderMode.Client);
    expect(guardedPaths(routes)).not.toContain(':lang/verify-email');
  });

  it('declares the verification page as a route of its own, open to anonymous visitors', () => {
    const route = allRoutes(routes).find((r) => r.path === ':lang/verify-email')?.route;

    expect(route).toBeDefined();
    expect(route?.canActivate).toBeUndefined();
  });

  it('server-renders the public comparison page, listed before the client-rendered schools/:id which would swallow it', () => {
    const paths = serverRoutes.map((r) => r.path);

    expect(modeOf(':lang/schools/compare')).toBe(RenderMode.Server);
    expect(guardedPaths(routes)).not.toContain(':lang/schools/compare');
    expect(paths.indexOf(':lang/schools/compare')).toBeGreaterThanOrEqual(0);
    expect(paths.indexOf(':lang/schools/compare')).toBeLessThan(paths.indexOf(':lang/schools/:id'));
  });

  it('server-renders the public programmes comparison, listed before the catch-all', () => {
    const paths = serverRoutes.map((r) => r.path);

    expect(modeOf(':lang/programs/compare')).toBe(RenderMode.Server);
    expect(guardedPaths(routes)).not.toContain(':lang/programs/compare');
    expect(paths.indexOf(':lang/programs/compare')).toBeGreaterThanOrEqual(0);
    expect(paths.indexOf(':lang/programs/compare')).toBeLessThan(paths.indexOf('**'));
  });

  it('server-renders the public courses comparison, listed before the catch-all', () => {
    const paths = serverRoutes.map((r) => r.path);

    expect(modeOf(':lang/courses/compare')).toBe(RenderMode.Server);
    expect(guardedPaths(routes)).not.toContain(':lang/courses/compare');
    expect(paths.indexOf(':lang/courses/compare')).toBeGreaterThanOrEqual(0);
    expect(paths.indexOf(':lang/courses/compare')).toBeLessThan(paths.indexOf('**'));
  });

  it('server-renders the public schools ranking explicitly, listed before the catch-all', () => {
    const paths = serverRoutes.map((r) => r.path);

    expect(modeOf(':lang/rankings/schools')).toBe(RenderMode.Server);
    expect(guardedPaths(routes)).not.toContain(':lang/rankings/schools');
    expect(allRoutes(routes).find((r) => r.path === ':lang/rankings/schools')?.route.canActivate).toBeUndefined();
    expect(paths.indexOf(':lang/rankings/schools')).toBeLessThan(paths.indexOf('**'));
  });

  it('server-renders the public programmes ranking explicitly, listed before the catch-all', () => {
    const paths = serverRoutes.map((r) => r.path);

    expect(modeOf(':lang/rankings/programs')).toBe(RenderMode.Server);
    expect(guardedPaths(routes)).not.toContain(':lang/rankings/programs');
    expect(allRoutes(routes).find((r) => r.path === ':lang/rankings/programs')?.route.canActivate).toBeUndefined();
    expect(paths.indexOf(':lang/rankings/programs')).toBeLessThan(paths.indexOf('**'));
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
