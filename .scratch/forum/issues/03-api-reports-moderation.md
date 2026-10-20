# 03: API des signalements et de la modération

**What to build:** Un utilisateur signale un message ; l'administrateur masque, supprime, verrouille et clôt les signalements via l'API.

**Blocked by:** [01](01-api-threads-replies.md)

**Status:** ready-for-agent

**Worktree:** `forum-03`

- [ ] Migration : signalements (message ciblé, auteur, motif ≤255, état ouvert/traité/rejeté)
- [ ] Signaler : utilisateur connecté, un seul signalement par utilisateur et par message (409 sinon)
- [ ] Admin sous `/api/v1/admin/` : liste paginée des signalements ouverts, masquer, supprimer, verrouiller/déverrouiller un fil, clore ; 403 non-admin
- [ ] Un message masqué n'est plus renvoyé au public (corps compris), reste visible de l'admin
- [ ] Signalements lisibles par l'admin seulement
- [ ] Tests d'intégration, rouge d'abord
