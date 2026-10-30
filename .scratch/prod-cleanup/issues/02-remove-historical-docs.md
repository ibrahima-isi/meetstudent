# 02: Supprimer la documentation historique terminée

**What to build:** Seule la documentation vivante reste. On supprime `docs/completion.md`, `completion.md` (racine), `docs/evidence/`, `docs/superpowers/`, `apps/api/docs/superpowers/` et `apps/web/docs/plans/`. Pour `docs/plans/2026-10-26-deploy-hetzner-coolify.md`, on vérifie d'abord que chacune de ses décisions figure dans l'ADR 0003, l'ADR 0004, `docs/DEPLOY.md` ou `.scratch/coolify-deploy/spec.md` ; une décision absente est reportée dans `docs/DEPLOY.md` avant la suppression. Les renvois vers ces fichiers sont retirés (notamment dans `docs/MONOREPO.md`, `docs/agents/issue-tracker.md` et `CLAUDE.md`).

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [prod-cleanup](../spec.md), point 2. Conservé : `docs/plans/2026-10-10-backoffice-vite-rewrite.md`.

- [ ] La PR liste chaque décision du plan du 2026-10-26 et l'endroit où elle est reprise
- [ ] `git grep` ne trouve aucun lien vers un fichier supprimé (hors `.scratch/prod-cleanup/`)
- [ ] La règle « legacy `docs/completion.md` frozen » est retirée de `docs/agents/issue-tracker.md` et du CLAUDE.md racine
- [ ] `infra/tests/deploy-docs.test.sh` passe

## Comments
