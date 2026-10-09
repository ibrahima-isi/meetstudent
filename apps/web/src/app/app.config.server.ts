import {
  mergeApplicationConfig,
  ApplicationConfig,
  inject,
  provideEnvironmentInitializer,
  REQUEST,
} from '@angular/core';
import { HTTP_TRANSFER_CACHE_ORIGIN_MAP } from '@angular/common/http';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';
import { environment } from '../environments/environment';
import { applyServerEnvironment } from '../environments/server-environment';
import { transferCacheOriginMap } from './http-transfer-origin';

/**
 * The `API_URL` override has to be applied *here*, not only in `server.ts`.
 *
 * The build emits `environment.ts` into two chunks — one reachable from the
 * server entry point, one reachable from the application — so they are two
 * objects, not one. Mutating the entry point's copy leaves every service
 * pointing at the baked-in `http://localhost:8080`, which inside the web
 * container refuses the connection. This module is part of the application
 * bundle, so it holds the same object the services import.
 *
 * An environment initializer rather than a module-level side effect: it runs
 * inside the application injector, before any `providedIn: 'root'` service is
 * constructed and therefore before any of them read `environment.apiUrl`.
 */
const serverConfig: ApplicationConfig = {
  providers: [
    provideEnvironmentInitializer(() => {
      if (typeof process !== 'undefined' && process.env) {
        applyServerEnvironment(environment, process.env);
      }
    }),
    {
      // Read lazily, on the first request, so it sees the API_URL override.
      provide: HTTP_TRANSFER_CACHE_ORIGIN_MAP,
      useFactory: () => transferCacheOriginMap(environment.apiUrl, inject(REQUEST, { optional: true })),
    },
    provideServerRendering(withRoutes(serverRoutes)),
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
