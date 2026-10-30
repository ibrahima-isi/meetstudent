# 01: Supprimer les fichiers morts

**What to build:** Le dépôt ne contient plus de fichiers que rien n'utilise. Le prototype React `apps/web/meetstudent/` disparaît, ainsi que les tests manuels de l'API `apps/api/dummy.txt`, `apps/api/seed_and_test.js` et `apps/api/api_tests.robot`. Les mentions du prototype dans `CLAUDE.md` (racine) et `apps/web/.claude/CLAUDE.md` sont retirées. En local seulement (fichiers non suivis) : `shared/`, `target/` et les `.DS_Store`.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [prod-cleanup](../spec.md), points 1 et « Implementation Decisions ».

- [ ] `git grep -n "apps/web/meetstudent\|seed_and_test\|api_tests.robot\|dummy.txt"` ne trouve plus rien en dehors de `.scratch/prod-cleanup/`
- [ ] `npm run build` et les tests web passent ; `./mvnw clean verify` passe dans `apps/api`
- [ ] La ligne « `shared/` — empty placeholder » du CLAUDE.md racine est retirée si le dossier n'existe plus
- [ ] Aucun job CI n'est modifié

## Comments
