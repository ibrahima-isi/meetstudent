import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';
import { environment } from './environments/environment';
import {
  applyServerEnvironment,
  isBackendPath,
  readAllowedHosts,
  TRUSTED_PROXY_HEADERS,
} from './environments/server-environment';

/**
 * Applies the override to *this* bundle's copy of `environment`, for anything
 * read here rather than inside the application.
 *
 * It is not what makes the application's services see it: the build emits
 * `environment.ts` into two chunks, so `app.config.server.ts` applies the same
 * override to the copy they actually import. Changing one without the other
 * leaves server-side requests pointing at `localhost`.
 */
applyServerEnvironment(environment, process.env);

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
const angularApp = new AngularNodeAppEngine({
  allowedHosts: readAllowedHosts(process.env),
  trustProxyHeaders: TRUSTED_PROXY_HEADERS,
});

/**
 * API paths are not pages. Reaching this server with one means nothing in front
 * routes `/api` and `/uploads` to the API (see `isBackendPath`).
 */
app.use((req, res, next) => {
  if (!isBackendPath(req.path)) {
    next();
    return;
  }
  res
    .status(404)
    .type('text/plain')
    .send('Not served here: /api and /uploads belong to the API. Check the reverse proxy in front of the web server.');
});

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) =>
      response ? writeResponseToNodeResponse(response, res) : next(),
    )
    .catch(next);
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
