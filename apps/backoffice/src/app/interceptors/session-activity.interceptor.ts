import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SessionActivityService } from '@services/session-activity.service';
import { TokenService } from '@services/token.service';
import { API_URL } from '@services/api-config';

/** Initial loads, auth calls and unattended reads never extend a session. */
export const sessionActivityInterceptor: HttpInterceptorFn = (req, next) => {
  const activity = inject(SessionActivityService);
  const tokens = inject(TokenService);
  const apiUrl = inject(API_URL);
  const path = req.url.split('?')[0];
  const businessRead = req.method === 'GET' && path.startsWith(`${apiUrl}/`)
    && path !== `${apiUrl}/auth` && !path.startsWith(`${apiUrl}/auth/`);
  if (businessRead && tokens.isAuthenticated() && activity.consume()) {
    req = req.clone({ setHeaders: { 'X-Session-Activity': 'true' } });
  }
  return next(req);
};
