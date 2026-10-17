# 03: Back office : liste des messages reçus

**What to build:** `GET /api/v1/admin/messages` (paginé, plus récent d'abord) et `DELETE /api/v1/admin/messages/{id}` (ADMIN) ; page « Messages » au back office (route, entrée du menu, service, composant, spec) : expéditeur, sujet, date, texte, suppression avec confirmation.

**Blocked by:** [contact-rework/01](01-api-contact-messages.md).

**Status:** resolved

- [x] Un non-admin reçoit 403, un anonyme 401.
- [x] La liste est paginée et triée par date décroissante ; un id inconnu en suppression répond 404.
- [x] La page affiche les messages, l'état vide et l'erreur avec nouvel essai ; la suppression demande confirmation.
- [x] Tests en échec d'abord, puis verts ; builds verts.

## Comments

2026-10-17 : tri fixe (`createdAt` puis `id` décroissants) : un `?sort=` est ignoré et ne renvoie pas 400, pour qu'aucun champ interne ne serve de tri. Aucun plafond de page n'existait dans le projet : 100 sur cet endpoint.
