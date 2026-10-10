import {
  ApplicationConfig,
  inject,
  provideBrowserGlobalErrorListeners,
  provideEnvironmentInitializer,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter, TitleStrategy, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { ViewportScroller } from '@angular/common';
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
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }),
    ),
    // No withFetch(): the fetch backend emits no upload progress events, so the
    // document upload bar would stay at 0%. XHR does, in the browser and on the server.
    provideHttpClient(withInterceptors([jwtInterceptor, refreshInterceptor, absoluteUrlInterceptor])),
    provideClientHydration(withEventReplay()),
    provideTransloco(translocoOptions),
    { provide: TitleStrategy, useExisting: PageTitleStrategy },
    // Subscribes to the router, so it has to be told to start — nothing else
    // injects it, and a service nobody injects is never constructed.
    provideEnvironmentInitializer(() => inject(AlternateLinksService).start()),
    // The router scrolls to anchors with an explicit offset and ignores CSS scroll-padding/margin
    // (`scrollOffset` is not an option of provideRouter). The Dock pill is ~68px tall, plus breathing room.
    provideEnvironmentInitializer(() => inject(ViewportScroller).setOffset([0, 96])),
  ],
};
