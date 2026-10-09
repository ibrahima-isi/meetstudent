# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 20.3.8.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

The browser bundle calls the API with relative URLs (`/api/v1/...`, `/uploads/...`). Under `ng serve`, `proxy.conf.json` forwards `/api` and `/uploads` to the API on `http://localhost:8080`, so start the API first. In production a reverse proxy does the same on a single origin.

## Production runtime (SSR container)

Server-side rendering has no page origin, so the container needs the API's in-network address:

| Variable | Example | Purpose |
| --- | --- | --- |
| `API_URL` | `http://api:8080/api/v1` | REST base used while rendering on the server |
| `SERVER_URL` | `http://api:8080` | API server root used while rendering on the server |
| `ALLOWED_HOSTS` | `meetstudent.example.com` | Comma-separated hostnames SSR will render for (default `localhost,web`) |
| `PORT` | `4200` | Listening port |

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Karma](https://karma-runner.github.io) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
