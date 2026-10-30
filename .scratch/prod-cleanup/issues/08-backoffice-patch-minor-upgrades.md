# 08: Back-office : montées patch/minor

**What to build:** `apps/backoffice` passe sur les dernières versions patch/minor compatibles : `@angular/*` en dernière 20.3.x, Tailwind, PostCSS, rxjs, tslib et les devDependencies, sans changement de major. `npm audit` ne signale plus de vulnérabilité corrigeable sans major.

**Blocked by:** [prod-cleanup/05](05-remove-unused-deps.md)

**Status:** ready-for-agent

Spec : [prod-cleanup](../spec.md), point 6.

- [ ] Tableau avant/après des versions et sortie de `npm audit` dans la PR
- [ ] `npm ci && npm run build && npm test -- --no-watch --browsers=ChromeHeadless` passe
- [ ] Aucune montée de major

## Comments
