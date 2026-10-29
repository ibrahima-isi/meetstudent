# 03: Lecture d'un utilisateur par e-mail sans révéler l'existence du compte, e-mail encodé côté web

**What to build:** Un utilisateur connecté qui demande `GET /users/email/{email}` pour une adresse qui n'est pas la sienne reçoit la même réponse, que le compte existe ou non. Le propriétaire et l'admin gardent l'accès actuel. Côté web, l'e-mail est encodé dans l'URL, si bien qu'une adresse avec `+`, `#`, `?` ou `%` se connecte normalement. Si la lecture du profil échoue juste après la connexion, les jetons sont effacés au lieu de laisser un état à moitié connecté.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (lecture par e-mail).

- [ ] Non-admin, adresse d'autrui : même statut et même corps pour un compte existant et inexistant (test d'intégration des deux cas)
- [ ] Propriétaire : 200 ; admin : 200 si le compte existe, 404 sinon
- [ ] Web : connexion réussie avec une adresse contenant `+` et `%` (spec du service d'auth, URL encodée attendue)
- [ ] Web : échec de la lecture du profil après connexion → jetons effacés, message d'erreur, aucun état connecté
- [ ] Le back-office (qui encode déjà) reste vert

## Comments
