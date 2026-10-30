# 07: Web : montées patch/minor

**What to build:** `apps/web` passe sur les dernières versions patch/minor compatibles : `@angular/*` et `@angular/ssr` en dernière 20.3.x, lucide-angular en dernière 0.x, express 5.x, Transloco, Tailwind, PostCSS, rxjs et les devDependencies, sans changement de major. `npm audit` ne signale plus de vulnérabilité corrigeable sans major.

**Blocked by:** [prod-cleanup/05](05-remove-unused-deps.md)

**Status:** ready-for-agent

Spec : [prod-cleanup](../spec.md), point 6.

- [ ] Tableau avant/après des versions et sortie de `npm audit` dans la PR
- [ ] `npm ci && npm run build && npm test -- --no-watch --browsers=ChromeHeadless` passe
- [ ] Le serveur SSR démarre (`npm run serve:ssr:frontend`) et rend `/fr` avec `ng-server-context="ssr"`
- [ ] Aucune montée de major

## Comments
