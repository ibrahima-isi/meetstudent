import { SessionActivityService } from '@services/session-activity.service';
import { ThemeService } from '@services/theme.service';
import { sessionActivityInterceptor } from './interceptors/session-activity.interceptor';
import { ApplicationConfig, inject, provideEnvironmentInitializer, provideBrowserGlobalErrorListeners, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { jwtInterceptor } from './interceptors/jwt.interceptor';
import { refreshInterceptor } from './interceptors/refresh.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideEnvironmentInitializer(() => inject(SessionActivityService)),
    // Started at boot, not by the shell: login and 404 must follow live OS theme changes too.
    provideEnvironmentInitializer(() => inject(ThemeService)),
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([jwtInterceptor, sessionActivityInterceptor, refreshInterceptor]), withFetch()),
  ],
};
