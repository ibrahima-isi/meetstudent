You are an expert in TypeScript, Angular, and scalable web application development. You write maintainable, performant, and accessible code following Angular and TypeScript best practices.

This workspace contains two frontend apps:
- Root Angular app at the repository root: `angular.json`, `src/`, and `package.json`.
- Nested React/Vite app under `meetstudent/` with its own `package.json` and `src/`.

Only modify the nested `meetstudent/` app when the task explicitly targets it.

## Project commands

- `npm install`
- `npm start` → `ng serve`
- `npm run build`
- `npm test`
- `npm run serve:ssr:frontend` for server-side rendering in the root Angular app

## TypeScript Best Practices

- Use strict type checking
- Prefer type inference when the type is obvious
- Avoid `any`; use `unknown` when type is uncertain

## Angular Best Practices

- Prefer standalone components over NgModules
- Do NOT set `standalone: true` inside decorators; Angular 20 defaults to standalone mode
- Use signals for state management and `computed()` for derived state
- Use `inject()` instead of constructor injection
- Use `ChangeDetectionStrategy.OnPush` for components
- Implement lazy loading for feature routes
- Use `NgOptimizedImage` for static images
  - Note: it does not work with inline base64 images
- Avoid `@HostBinding` and `@HostListener`; use the `host` object in the decorator

## Components

- Keep components small and focused on a single responsibility
- Use `input()` and `output()` functions instead of decorators
- Prefer inline templates for small components
- Prefer Reactive forms over template-driven forms
- Do NOT use `ngClass`; use `class` bindings instead
- Do NOT use `ngStyle`; use `style` bindings instead

## Templates

- Keep templates simple and avoid complex logic
- Use native control flow (`@if`, `@for`, `@switch`) instead of `*ngIf`, `*ngFor`, `*ngSwitch`
- Use the async pipe for observables

## State Management

- Use signals for local component state
- Keep state transformations pure and predictable
- Do NOT use `mutate` on signals; use `update` or `set` instead

## Services

- Design services around a single responsibility
- Use `providedIn: 'root'` for application-wide services
