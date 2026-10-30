# 04: Le dépôt, seule source de vérité de la mémoire du projet

**What to build:** Une session qui démarre ne dépend plus de la mémoire perso. Chaque fait de `~/.claude/projects/-Users-ibrahimadiallo-IdeaProjects-meetstudent/memory/` est comparé au dépôt. S'il est absent et toujours vrai (par exemple les hôtes Coolify et le VPS dans `docs/DEPLOY.md`, les suites de l'audit 2026-10-27 non couvertes par `p2-backlog`, la règle de promotion `origin/dev:refs/heads/main` dans `docs/MONOREPO.md`), il est ajouté au bon doc du dépôt. Un instantané d'état (hash, « dev en avance sur main ») est abandonné. Ensuite, les notes projet sont supprimées et `MEMORY.md` ne garde que la note `onepassword-commit-signing.md`.

**Blocked by:** [prod-cleanup/03](03-compact-claude-md.md)

**Status:** ready-for-human

Spec : [prod-cleanup](../spec.md), point 4. La partie dépôt passe par une PR. La suppression dans `~/.claude` se fait hors Git, seulement après la fusion de cette PR et avec l'accord du propriétaire.

- [ ] La PR contient un tableau : note mémoire → fait → destination dans le dépôt, ou « abandonné (instantané) »
- [ ] Aucun secret, ni l'adresse IP du VPS si le propriétaire la refuse dans le dépôt (lui demander)
- [ ] Après la fusion : le dossier mémoire ne contient plus que `MEMORY.md` et `onepassword-commit-signing.md`

## Comments
