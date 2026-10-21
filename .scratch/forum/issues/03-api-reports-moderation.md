# 03: API des signalements et de la modération

**What to build:** Un utilisateur signale un message ; l'administrateur masque, supprime, verrouille et clôt les signalements via l'API.

**Blocked by:** [01](01-api-threads-replies.md)

**Status:** resolved

**Worktree:** `forum-03`

- [x] Migration : signalements (message ciblé, auteur, motif ≤255, état ouvert/traité/rejeté)
- [x] Signaler : utilisateur connecté, un seul signalement par utilisateur et par message (409 sinon)
- [x] Admin sous `/api/v1/admin/` : liste paginée des signalements ouverts, masquer, supprimer, verrouiller/déverrouiller un fil, clore ; 403 non-admin
- [x] Un message masqué n'est plus renvoyé au public (corps compris), reste visible de l'admin
- [x] Signalements lisibles par l'admin seulement
- [x] Tests d'intégration, rouge d'abord
- [x] Verrouiller un fil prend un verrou de ligne (`SELECT … FOR UPDATE`) ou relit le fil dans la transaction de réponse, pour éviter la course verrou/réponse (une réponse déjà validée par `createReply` pendant que l'admin verrouille). Test de concurrence si faisable.

## Résultat

Branche `feat/forum-moderation-api`, migration V34. Preuves réelles :
- `./mvnw -o clean verify` avec `MIGRATION_PG_*` sur un PostgreSQL 18 jetable (conteneur supprimé) : 399 tests unitaires + 630 d'intégration, 0 échec, 0 ignoré (migrations V33 et V34 rejouées sur Postgres comprises).
- Rouge d'abord : première passe, 29 des 36 tests signalements/modération en échec sans les endpoints, test de course rouge, migration V34 rouge. Passe de corrections : 5 des 6 tests de course (modification par l'auteur contre masquage, masquage contre verrouillage, réponses) rouges avant le verrou de ligne et `@DynamicUpdate` ; 11 des 16 tests de signalement et 2 des 3 tests unitaires de traduction FK/unicité rouges avant le code ; test « open-in-view désactivé » rouge (500) ; instantané, `handled_by` et extrait signalé rouges. Pas vus rouges : le test de course réponse/verrou d'origine (déjà vert, resté vert avec l'attente déterministe), les tests de migration Postgres V34 de la 2e passe (écrits avec V34 déjà modifiée), le test de coupe de l'instantané.
- Test du budget de signalements : `checkReport` branché temporairement sur la fenêtre des écritures, le test échoue (429 inattendu), rétabli ensuite, vert.
- Application démarrée en `ddl-auto: validate` sur la V34 finale (34 migrations validées, profil dev, Postgres jetable) : GET `/api/v1/forum/threads` 200, GET `/api/v1/admin/forum/reports` anonyme 401.

Endpoints : `POST /api/v1/forum/threads|replies/{id}/report` ; `/api/v1/admin/forum/` : `GET reports?status=`, `POST reports/{id}/handle|dismiss`, `POST threads/{id}/hide|unhide|lock|unlock`, `POST replies/{id}/hide|unhide`, `DELETE threads/{id}` et `replies/{id}`.

Décisions :
- Qui signale : tout étudiant ou expert connecté (expert en attente ou rejeté compris) ; anonyme 401, admin 403. Corps `{"reason"}` obligatoire, ≤255, `SafeText`. 201 sans corps. Message inconnu ou masqué 404 ; son propre message 409 ; déjà signalé 409 (même si clos). Course à l'insertion : clé étrangère (message supprimé entre-temps) 404, unicité concurrente 409 (le gestionnaire global ne les voit pas, mais Hibernate journalise encore la violation, éventuellement en ERROR) ; le budget étant consommé avant l'insertion, une telle course 404/409 coûte un signalement du budget.
- Budget séparé : 10 signalements par heure et par utilisateur (`app.forum.report-rate-limit`), 429 + `Retry-After`, sans partage avec les 5 écritures par minute.
- Instantané : le texte signalé (1000 unités au plus) est conservé ; la liste admin montre `excerpt` (actuel), `reportedExcerpt` (au moment du signalement) et `targetModifiedAt`. `handled_by` (admin qui a clos, `SET NULL`) posé à la clôture, jamais exposé.
- Cascade : le signalement suit son message (traités compris, sans piste d'audit) ; signaleur supprimé : `reporter_id` NULL, signalement conservé.
- Liste admin : tri fixe récent d'abord, taille ≤100, `?status=` facultatif, prénom du signaleur seul. `handle`/`dismiss` idempotents (première décision, date et admin conservés), DTO construit dans la transaction (testé avec `open-in-view=false`). Masquer, verrouiller et déverrouiller sont idempotents ; le second `DELETE` répond 404.
- Verrous de ligne : masquage, verrouillage, modification et suppression par l'auteur, et `createReply` chargent la ligne en `SELECT … FOR UPDATE` ; `ForumThread` et `ForumReply` en `@DynamicUpdate` : ni mise à jour perdue ni réponse acceptée après un verrouillage. Tests de course déterministes (H2, `LOCK_TIMEOUT=10000`, attente sur l'état du thread, sans `sleep` décisif) ; sur Postgres, test JDBC du `FOR UPDATE` (attente vue dans `pg_stat_activity`, puis état validé lu).

Limites connues (acceptées) : la suppression d'un message efface ses signalements et leur historique ; un seul signalement par utilisateur et message même clos (pas de nouveau signalement après modification, d'où l'instantané) ; pas de regroupement des signalements, donc des faux comptes peuvent inonder la file (compteur par cible à suivre pour forum/04) ; sur un fil verrouillé, les auteurs peuvent encore modifier et supprimer leurs propres messages (décision) ; deadlock très rare possible entre suppression de compte et réponse de l'utilisateur à son propre fil, ou entre la clôture d'un signalement (`handled_by`) et la suppression du compte de l'admin qui le clôt (Postgres 40P01, 500, nouvel essai possible) ; limiteurs en mémoire (une instance API) ; le test de course Spring tourne sur H2 ; aucun rendu web ni back office (forum/04 et forum/05).

