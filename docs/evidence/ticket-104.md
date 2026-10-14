# Ticket #104 — preuves (2026-10-14)

Base isolée `e84ce2beafd4eaa91ce4e7afb22bdbbb205829b6`, branche `feat/account-deletion-104`, worktree `/tmp/meetstudent-ticket-104`. Ticket #104 et spec #99 relus live : OPEN, aucun commentaire, seul prérequis #100 CLOSED ; ticket réclamé. Aucun travail sur #105, aucune promotion main.

## Contrat et nettoyage

`DELETE /api/v1/users/{id}` exige `{currentPassword}` pour soi, administrateur inclus. Un administrateur autorisé supprimant un autre compte peut omettre le corps. Succès 204 vide, cible absente 404, anonymat 401, autre utilisateur non autorisé 403, mot de passe absent/incorrect 400 `{currentPassword}`.

Le verrou utilisateur précède la vérification et le nettoyage transactionnel : toutes les sessions refresh et notes school/program/course de ce compte sont supprimées, puis ses médias, liens de wishlist et compte. Les autres notes/comptes et ressources restent présents. Les jetons d'action deviennent orphelins via le FK `ON DELETE SET NULL` existant : inutilisables, mais toujours comptés pour le budget d'adresse et purgés selon la rétention existante de sept jours. Aucune nouvelle migration : V21 reste la dernière. La suppression physique des fichiers conserve le comportement préexistant de MediaService (best effort, non transactionnel).

Audit des clients : aucun appel de suppression de compte dans `apps/web/src/app/services/user.service.ts`, aucune route/service de gestion des utilisateurs dans le backoffice. Aucun parcours à adapter ou nouveau parcours créé ; contrat documenté dans `apps/web/docs/backend-api-integration.md`.

## Red → green HTTP

- Mot de passe : premier test attendait 400, recevait 500, avant code produit (`/tmp/ms104-red-password.log`). L'ancien endpoint tentait effectivement la suppression et le mapping d'une collection détachée. Après correction : test vert (`/tmp/ms104-green-password.log`).
- Données liées : après correction de la fixture de wishlist historique d'un ancien étudiant devenu expert, suppression attendue 204, reçue 500 sur FK `course_rates.user_id` (`/tmp/ms104-red-dependencies.log`). Après nettoyage explicite des trois types de notes et sessions : suite UserController **20 tests verts** (`/tmp/ms104-green-user-http.log`).
- Couverture HTTP finale : missing/incorrect/correct password, administrateur soi/autrui, 401/403/404/204 vide, deux appareils, notes des trois types, médias, wishlist historique, autres notes/utilisateur et catalogue préservés ; trois finalités de liens d'action invalides après suppression, budget d'adresse survivant à register/delete/register via HTTP réel.
- Deux nouvelles courses login/refresh versus suppression utilisent les barrières JDBC existantes, forçant la lecture des credentials avant le commit de suppression. Aucun credential survivant ; ancien access/refresh et nouvelle connexion à ce compte refusés. Les seize courses précédentes restent vertes.

Passe ciblée avant simplification de signature : **16 unitaires + 61 intégrations**, zéro échec/erreur/skip (`/tmp/ms104-focused-api.log`). La suite complète finale couvre la signature définitive.

## PostgreSQL réel

PostgreSQL 18.6 jetable, projet Compose `ms104`, port 55435, bases séparées `ms104` (suppression/courses) et `ms104mail` (budget/retention). Flyway production V1–V21, `ddl-auto=validate`, aucun V1 H2 importé.

```sh
./mvnw verify -Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false \
  '-Dit.test=UserControllerIntegrationTests#selfDeletionRequiresCurrentPasswordAndLeavesAccountUsableWhenRejected+administratorNeedsPasswordForSelfButCanDeleteAnotherAccountWithoutIt+deletionRejectsAnonymousAndOtherUsersBeforeAnyMutation+deletionRemovesDependentRatingsWishlistMediaAndAllDevicesButPreservesOtherUsers,PasswordSessionRaceIntegrationTests#credentialsReadBeforeSensitiveChangeCannotMintASurvivingSession' \
  -Dspring.datasource.url=jdbc:postgresql://localhost:55435/ms104 \
  -Dspring.datasource.username=ms104 -Dspring.datasource.password=disposable-ms104 \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dspring.flyway.enabled=true -Dspring.flyway.locations=filesystem:src/main/resources/db/migration \
  -Dspring.jpa.hibernate.ddl-auto=validate -Dspring.jpa.show-sql=false
```

**12 tests verts** : quatre parcours de suppression et huit courses credentials. Commande identique avec `-Dit.test=EmailVerificationIntegrationTests` sur `ms104mail` : **23 tests verts**, dont budget d'adresse et tokens orphelins. Le code de purge par rétention est inchangé ; sa couverture unitaire existante est réexécutée dans la suite API complète. Total **35**, zéro échec/erreur/skip, sur code `a7d48f3`. Logs `/tmp/ms104-postgres-http.log`, `/tmp/ms104-postgres-budget.log`. Ressources arrêtées et supprimées avec `docker compose -p ms104 -f /tmp/ms104-compose.yml down -v`.

## Clients

Node 22.21.1, Chrome 152, dépendances propres au worktree installées offline. Web **940/940**, backoffice **286/286**, contrôles `tsc --noEmit -p tsconfig.app.json` et builds SSR web/backoffice réussis. Logs `/tmp/ms104-web-{test,types,build}.log`, `/tmp/ms104-backoffice-{test,types,build}.log`. Les essais sandbox des tests natifs échouaient au lancement code 134 ; les exécutions hors sandbox sont vertes.

## Standards

Revue indépendante Sol sur `e84ce2b...a7d48f3` : **0 violation ferme**. Une observation facultative de duplication sur les trois requêtes de suppression JPA ; conservées car les entités et dépôts sont distincts, une abstraction générique ajouterait du coût pour trois lignes spécifiques. Aucun défaut transactionnel introduit démontré. `git diff --check` propre.

## Spec

Revue indépendante Sol : **0 écart confirmé** (exigence manquante, scope creep, comportement incorrect). Aucun consommateur client existant à changer. Le risque de fichiers non transactionnels est préexistant et hors diff. La preuve PostgreSQL, alors en cours pendant la revue, est désormais complète ci-dessus.

## Livraison

Suite API complète finale `./mvnw clean verify` : **226 unitaires + 308 intégrations**, zéro échec/erreur/skip, BUILD SUCCESS (1m47s), `/tmp/ms104-api-final.log`. Code produit `a7d48f3` après simplification de la signature du service ; aucune modification produit ultérieure. Cases cochées pour l'implémentation et les vérifications locales requises ; PR/CI/fusion restent suivies séparément. Le bilan final et le nettoyage du seul worktree/branche seront enregistrés après livraison dans la copie locale exportée.
