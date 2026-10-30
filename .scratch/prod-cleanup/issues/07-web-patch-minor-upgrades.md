# 07: Web : montées patch/minor

**What to build:** `apps/web` passe sur les dernières versions patch/minor compatibles : `@angular/*` et `@angular/ssr` en dernière 20.3.x, lucide-angular en dernière 0.x, express 5.x, Transloco, Tailwind, PostCSS, rxjs et les devDependencies, sans changement de major. `npm audit` ne signale plus de vulnérabilité corrigeable sans major.

**Blocked by:** [prod-cleanup/05](05-remove-unused-deps.md)

**Status:** resolved

Spec : [prod-cleanup](../spec.md), point 6.

- [x] Tableau avant/après des versions et sortie de `npm audit` dans la PR
- [x] `npm ci && npm run build && npm test -- --no-watch --browsers=ChromeHeadless` passe
- [x] Le serveur SSR démarre (`npm run serve:ssr:frontend`) et rend `/fr` avec `ng-server-context="ssr"`
- [x] Aucune montée de major

## Comments

| Package | Before | After |
|---|---|---|
| @angular/core, common, compiler, forms, platform-browser, platform-server, router, compiler-cli | 20.3.17 | 20.3.34 |
| @angular/build, @angular/cli, @angular/ssr | 20.3.19 | 20.3.42 |
| express | 5.2.1 | 5.3.0 |
| tailwindcss, @tailwindcss/postcss | 4.2.1 | 4.3.3 |
| postcss | 8.5.8 | 8.5.32 |
| tslib | 2.8.1 | 2.9.1 |
| @types/node | 20.19.37 | 20.19.43 |
| lucide-angular | 0.577.0 | 0.577.0 (0.x latest; 1.0.0 is a major, skipped) |

`npm audit`: before 51 vulnerabilities (1 low, 16 moderate, 31 high, 3 critical); after `npm audit fix` (no --force): `found 0 vulnerabilities`.

Results: `npm ci && npm run build` OK (existing dock-navbar CSS budget warning only); `npm test -- --no-watch --browsers=ChromeHeadless` 1685/1685 SUCCESS (same as baseline); SSR server on PORT=4377, `curl /fr` contains `ng-server-context="ssr"`. No major bump, nothing reverted. Only package-lock.json changed (package.json ranges already allow the new versions).
