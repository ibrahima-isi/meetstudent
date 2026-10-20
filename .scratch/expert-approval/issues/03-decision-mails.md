# 03: Mails de demande et de décision

**What to build:** L'enseignant reçoit un mail à l'inscription (demande reçue) et à la décision (approuvé ou rejeté avec motif).

**Blocked by:** [02](02-admin-approval-queue.md)

**Status:** ready-for-agent

**Worktree:** `expert-approval-03`

- [ ] Trois nouveaux types de mail via le service de messagerie existant (résultats d'envoi, envoi après commit)
- [ ] Le motif de rejet est échappé dans le mail ; aucun secret ni donnée inutile
- [ ] Un échec d'envoi ne bloque pas la décision ni l'inscription
- [ ] Tests d'intégration sur le contenu et le destinataire des mails, rouge d'abord
