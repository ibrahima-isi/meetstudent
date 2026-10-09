import {
  ApplicationConfig,
  inject,
  provideBrowserGlobalErrorListeners,
  provideEnvironmentInitializer,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter, TitleStrategy, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideTransloco } from '@jsverse/transloco';

import { routes } from './app.routes';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { jwtInterceptor } from './interceptors/jwt.interceptor';
import { refreshInterceptor } from './interceptors/refresh.interceptor';
import { absoluteUrlInterceptor } from './interceptors/absolute-url.interceptor';
import { translocoOptions } from '@i18n/transloco.config';
import { AlternateLinksService } from '@i18n/alternate-links.service';
import { PageTitleStrategy } from '@shared/page-title';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),
    // No withFetch(): the fetch backend emits no upload progress events, so the
    // document upload bar would stay at 0%. XHR does, in the browser and on the server.
    provideHttpClient(withInterceptors([jwtInterceptor, refreshInterceptor, absoluteUrlInterceptor])),
    provideClientHydration(withEventReplay()),
    provideTransloco(translocoOptions),
    { provide: TitleStrategy, useExisting: PageTitleStrategy },
    // Subscribes to the router, so it has to be told to start — nothing else
    // injects it, and a service nobody injects is never constructed.
    provideEnvironmentInitializer(() => inject(AlternateLinksService).start()),
  ],
};
