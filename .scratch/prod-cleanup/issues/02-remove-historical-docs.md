# 02: Supprimer la documentation historique terminée

**What to build:** Seule la documentation vivante reste. On supprime `docs/completion.md`, `completion.md` (racine), `docs/evidence/`, `docs/superpowers/`, `apps/api/docs/superpowers/` et `apps/web/docs/plans/`. Pour `docs/plans/2026-10-26-deploy-hetzner-coolify.md`, on vérifie d'abord que chacune de ses décisions figure dans l'ADR 0003, l'ADR 0004, `docs/DEPLOY.md` ou `.scratch/coolify-deploy/spec.md` ; une décision absente est reportée dans `docs/DEPLOY.md` avant la suppression. Les renvois vers ces fichiers sont retirés (notamment dans `docs/MONOREPO.md`, `docs/agents/issue-tracker.md` et `CLAUDE.md`).

**Blocked by:** None (can start immediately)

**Status:** resolved

Spec : [prod-cleanup](../spec.md), point 2. Conservé : `docs/plans/2026-10-10-backoffice-vite-rewrite.md`.

- [x] La PR liste chaque décision du plan du 2026-10-26 et l'endroit où elle est reprise
- [x] `git grep` ne trouve aucun lien vers un fichier supprimé (hors `.scratch/prod-cleanup/`)
- [x] La règle « legacy `docs/completion.md` frozen » est retirée de `docs/agents/issue-tracker.md` et du CLAUDE.md racine
- [x] `infra/tests/deploy-docs.test.sh` passe

## Comments

Resolved 2026-10-30 on branch `docs/remove-historical-docs`.

- Supprimés : `docs/completion.md`, `completion.md`, `docs/evidence/`, `docs/superpowers/`, `apps/api/docs/superpowers/`, `apps/web/docs/plans/`, `docs/plans/2026-10-26-deploy-hetzner-coolify.md`.
- Plan du 2026-10-26 : chaque décision est reprise dans l'ADR 0003/0004, `docs/DEPLOY.md` §9, la feuille de route `docs/deploy/coolify-roadmap.html` (étapes du propriétaire : phases 1, 2, 4, 5, 6) ou le code (`compose.coolify.yml`, `infra/`, `ci.yml`, V35 pour le hachage des refresh tokens). Quatre décisions absentes ont été ajoutées à `docs/DEPLOY.md` §9 : VPS 4 vCPU / 8 Go en Allemagne sans staging, pare-feu Hetzner Cloud (22/80/443, 8000 temporaire), snapshots Hetzner optionnels, Postgres interne. Le tableau complet est dans la PR.
- Renvois retirés ou redirigés : `CLAUDE.md`, `docs/MONOREPO.md`, `docs/agents/issue-tracker.md` (règle « legacy frozen »), `docs/DEPLOY.md`, `docs/deploy/coolify-roadmap.html`, `.scratch/coolify-deploy/` (spec et tickets 02 à 05).
- Vérification : `git grep -nE "2026-10-26-deploy|docs/completion|docs/evidence|superpowers|apps/web/docs/plans" -- . ':!.scratch/prod-cleanup'` : aucun résultat. Liens relatifs des fichiers modifiés : aucun cassé. `bash infra/tests/deploy-docs.test.sh` : `all passed` (4 tests).
