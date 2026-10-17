# 03: Back office : liste des messages reçus

**What to build:** `GET /api/v1/admin/messages` (paginé, plus récent d'abord) et `DELETE /api/v1/admin/messages/{id}` (ADMIN) ; page « Messages » au back office (route, entrée du menu, service, composant, spec) : expéditeur, sujet, date, texte, suppression avec confirmation.

**Blocked by:** [contact-rework/01](01-api-contact-messages.md).

**Status:** ready-for-agent

- [ ] Un non-admin reçoit 403, un anonyme 401.
- [ ] La liste est paginée et triée par date décroissante ; un id inconnu en suppression répond 404.
- [ ] La page affiche les messages, l'état vide et l'erreur avec nouvel essai ; la suppression demande confirmation.
- [ ] Tests en échec d'abord, puis verts ; builds verts.
