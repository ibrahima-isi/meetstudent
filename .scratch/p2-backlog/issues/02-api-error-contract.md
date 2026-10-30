# 02: Contrat d'erreur API : corps sur 400/404, statut de modération invalide en 400, rôle avec ou sans préfixe

**What to build:** Toute réponse 400 de l'API porte `{champ: message}` et toute réponse 404 une `ErrorResponse`, comme le contrat documenté le promet (aujourd'hui 29 réponses sont vides). Un admin qui envoie un statut de modération autre que `VERIFIED`/`REJECTED` reçoit 400 sur le champ `status` au lieu d'une 500. `GET /users/role/STUDENT` et `GET /users/role/ROLE_STUDENT` renvoient la même liste.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (contrat d'erreur). À faire avant 03, 05 et 06 de préférence (mêmes contrôleurs, voir « Further Notes »).

- [ ] Chaque 400/404 auparavant vide a un corps conforme ; les codes HTTP ne changent pas
- [ ] Tests d'intégration sur un échantillon représentatif (programme inconnu sur une note, utilisateur inconnu, école inconnue…) : corps présent et forme exacte
- [ ] `PATCH /media/{id}/verification` avec `PENDING` → 400 `{status: …}` ; `VERIFIED` et `REJECTED` inchangés
- [ ] `GET /users/role/STUDENT` = `GET /users/role/ROLE_STUDENT` (admin) ; rôle inconnu → 400 avec corps ; non-admin toujours refusé
- [ ] Les clients web et back-office qui lisent ces erreurs restent verts

## Comments
