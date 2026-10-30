# 08: Back-office : montées patch/minor

**What to build:** `apps/backoffice` passe sur les dernières versions patch/minor compatibles : `@angular/*` en dernière 20.3.x, Tailwind, PostCSS, rxjs, tslib et les devDependencies, sans changement de major. `npm audit` ne signale plus de vulnérabilité corrigeable sans major.

**Blocked by:** [prod-cleanup/05](05-remove-unused-deps.md)

**Status:** resolved

Spec : [prod-cleanup](../spec.md), point 6.

- [x] Tableau avant/après des versions et sortie de `npm audit` dans la PR
- [x] `npm ci && npm run build && npm test -- --no-watch --browsers=ChromeHeadless` passe
- [x] Aucune montée de major

## Comments

Montées via `npm update` + `npm audit fix` (sans `--force`), lockfile seul modifié (`package.json` inchangé : les plages `^`/`~` couvrent déjà ces versions). Aucune montée de major, rien annulé.

| Paquet | Avant | Après |
|---|---|---|
| @angular/build, @angular/cli | 20.3.39 | 20.3.42 |
| @angular/{common,compiler,compiler-cli,core,forms,platform-browser,router} | 20.3.33 | 20.3.34 |
| postcss | 8.5.29 | 8.5.32 |
| tslib | 2.8.1 | 2.9.1 |
| tailwindcss, @tailwindcss/postcss | 4.3.3 | 4.3.3 |
| rxjs, @types/node | 7.8.2, 20.19.43 | inchangés |

`npm audit` : avant `14 vulnerabilities (1 low, 7 moderate, 6 high)` (@angular/*, @modelcontextprotocol/sdk, body-parser, fast-uri, ip-address, qs, undici, vite, zod), après `found 0 vulnerabilities`.

Résultats : `npm ci` OK, `npm run build` OK, `npm test -- --no-watch --browsers=ChromeHeadless` : 498 sur 498 SUCCESS. Restent seulement des majors (Angular 22, TypeScript 7, jasmine-core 7, @types/jasmine 7, @types/node 26), hors périmètre.
