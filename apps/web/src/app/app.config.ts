import {
  ApplicationConfig,
  inject,
  provideBrowserGlobalErrorListeners,
  provideEnvironmentInitializer,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors, withFetch } from '@angular/common/http';
import { provideTransloco } from '@jsverse/transloco';

import { routes } from './app.routes';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { jwtInterceptor } from './interceptors/jwt.interceptor';
import { translocoOptions } from '@i18n/transloco.config';
import { AlternateLinksService } from '@i18n/alternate-links.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([jwtInterceptor]), withFetch()),
    provideClientHydration(withEventReplay()),
    provideTransloco(translocoOptions),
    // Subscribes to the router, so it has to be told to start — nothing else
    // injects it, and a service nobody injects is never constructed.
    provideEnvironmentInitializer(() => inject(AlternateLinksService).start()),
  ],
};
