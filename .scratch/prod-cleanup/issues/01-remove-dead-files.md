# 01: Supprimer les fichiers morts

**What to build:** Le dépôt ne contient plus de fichiers que rien n'utilise. Le prototype React `apps/web/meetstudent/` disparaît, ainsi que les tests manuels de l'API `apps/api/dummy.txt`, `apps/api/seed_and_test.js` et `apps/api/api_tests.robot`. Les mentions du prototype dans `CLAUDE.md` (racine) et `apps/web/.claude/CLAUDE.md` sont retirées. En local seulement (fichiers non suivis) : `shared/`, `target/` et les `.DS_Store`.

**Blocked by:** None (can start immediately)

**Status:** resolved

Spec : [prod-cleanup](../spec.md), points 1 et « Implementation Decisions ».

- [x] `git grep -n "apps/web/meetstudent\|seed_and_test\|api_tests.robot\|dummy.txt"` ne trouve plus rien en dehors de `.scratch/prod-cleanup/`
- [x] `npm run build` et les tests web passent ; `./mvnw clean verify` passe dans `apps/api`
- [x] La ligne « `shared/` — empty placeholder » du CLAUDE.md racine est retirée si le dossier n'existe plus
- [x] Aucun job CI n'est modifié

## Comments

2026-10-30 : suppression de `apps/web/meetstudent/`, `apps/api/dummy.txt`, `seed_and_test.js`, `api_tests.robot` ; mentions retirées de `CLAUDE.md` (racine : ligne prototype et « `shared/` — empty placeholder », `shared/` n'existe pas dans git) et `apps/web/.claude/CLAUDE.md`.
- `git grep -n "apps/web/meetstudent\|seed_and_test\|api_tests.robot\|dummy.txt" -- . ':!.scratch/prod-cleanup'` : il ne reste que 3 mentions dans des docs historiques (`apps/web/docs/plans/2026-08-09-frontend-completion.md`, `docs/superpowers/plans/2026-10-10-landing-navbar-auth-redesign.md`), supprimées par le ticket 02 ; non modifiées ici.
- `cd apps/web && npm ci && npm run build` : OK ; `npm test -- --no-watch --browsers=ChromeHeadless` : 1685 sur 1685 SUCCESS.
- `cd apps/api && ./mvnw clean verify` : BUILD SUCCESS, 835 tests, 0 échec, 0 erreur, 8 ignorés.
- Aucun job CI modifié. Les fichiers locaux non suivis (`target/`, `.DS_Store`) ne sont pas dans la PR.
