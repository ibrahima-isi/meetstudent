# 01: API des fils et des réponses

**What to build:** Via l'API, tout le monde lit les fils ; étudiants et experts approuvés créent des fils et répondent ; l'auteur modifie et supprime ses messages.

**Blocked by:** [expert-approval/01](../../expert-approval/issues/01-status-and-rating-guard.md)

**Status:** ready-for-agent

**Worktree:** `forum-01`

- [ ] Migration : fils (titre ≤150, message ≤5000, établissement facultatif, verrouillé), réponses, masqué ; auteur supprimé → null affiché « Utilisateur supprimé »
- [ ] Lecture publique paginée (≤100, récents d'abord), filtre par établissement ; DTO sans e-mail ni identifiant interne
- [ ] Écriture : STUDENT ou expert approuvé ; expert en attente/rejeté 403 ; anonyme 401 ; fil verrouillé refuse les réponses
- [ ] Modifier/supprimer : auteur seulement (autrui 403)
- [ ] Texte brut stocké et renvoyé tel quel (une charge `<script>` n'est pas interprétée) ; contrôles de caractères invisibles refusés
- [ ] Limite de 5 messages par minute et par utilisateur : 429 + `Retry-After`
- [ ] Tests d'intégration des contrôleurs, rouge d'abord ; glossaire : Forum, Fil, Réponse
