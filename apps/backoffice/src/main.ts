import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { loadRuntimeConfig } from './app/runtime-config';
import { API_URL, SERVER_URL, WEB_URL } from '@services/api-config';

// Fetch /config.json first so the API URL can differ per deployment without a rebuild.
loadRuntimeConfig()
  .then(({ apiUrl, serverUrl, webUrl }) =>
    bootstrapApplication(App, {
      providers: [
        ...appConfig.providers,
        ...(webUrl ? [{ provide: WEB_URL, useValue: webUrl }] : []),
        ...(apiUrl ? [{ provide: API_URL, useValue: apiUrl }] : []),
        ...(serverUrl ? [{ provide: SERVER_URL, useValue: serverUrl }] : []),
      ],
    }),
  )
  .catch((err) => console.error(err));
