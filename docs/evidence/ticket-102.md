# Ticket #102 — preuves (2026-10-14)

Base épinglée : `b56e3845984a1627d3d5ff8c30e3513dcd6012aa` (`origin/dev` actualisé). Worktree isolé `/tmp/meetstudent-ticket-102`, branche `fix/email-confirmation-session-revocation`. Sources : tickets GitHub #102/#99 et passation #101. Aucun ancien checkout importé.

## Red → green

| Régression | Rouge observé | Vert |
| --- | --- | --- |
| Confirmation email / anciens JWT | HTTP 200 au lieu de 401 | Anciennes sessions refusées ; login nouvelle adresse et profil accessibles |
| Credentials historiques, trois rôles | 3 échecs quand l’incrément est retiré | JWT sans `pv` et refresh version zéro fonctionnent avant confirmation, puis sont refusés |
| Courses login/refresh contre confirmation | 2 échecs quand l’incrément est retiré | Revalidation sous verrou refuse les credentials obsolètes |
| Identité Angular fr/en | 2 échecs, cache/session conservés et rechargement du profil | Cache et tokens supprimés après succès ; lien de reconnexion localisé |
| Autre onglet | 1 échec, ancienne identité encore affichée | Événement browser `storage` efface les signaux et le cache ; lien login affiché |

La première tranche HTTP a échoué avant modification du produit. Les tests historiques et les courses ont aussi été éprouvés par suppression temporaire de l’incrément de version (5 échecs), puis restauration. Tests HTTP réellement committés ; mail capturé à sa frontière, aucun SMTP. Les courses utilisent la barrière JDBC existante après lecture, avant verrou utilisateur, pas un simple départ simultané. Treize courses couvrent login/refresh, demande/renvoi email et réutilisation de reset ; quatre cas supplémentaires concernent la confirmation email. Les validations, limites, annulation, liens remplacés, inconnus, mauvaise finalité, usage unique et adresse prise entre-temps restent vérifiés.

La demande ne change ni l’adresse ni la session. La confirmation consomme le lien sous verrou utilisateur, échange l’adresse et incrémente la version par écriture ciblée, puis supprime les refresh tokens dans la même transaction. Aucune migration nouvelle. Le web conserve la session sur échec réseau et la supprime après réussite/retry ; la vérification ordinaire conserve son rechargement de profil. La synchronisation inter-onglets ignore sessionStorage et les événements anciens quand une nouvelle connexion est déjà stockée ; l’écouteur est retiré avec le service, et SSR ne consulte pas window.

## Vérifications locales

| Contrôle | Commande | Résultat |
| --- | --- | --- |
| API complète | `cd apps/api && ./mvnw clean verify -Dspring.jpa.show-sql=false` | 226 unitaires + 294 intégrations ; aucun échec/erreur/skip |
| PostgreSQL 18 | Commande ci-dessous, migrations production et `ddl-auto=validate` | 20 migrations V1–V20, schéma validé, 39 HTTP/courses réussis |
| Web finale | Node 22.21.1, `npm test -- --no-watch --browsers=ChromeHeadless` | 930 tests réussis |
| Web ciblée | `--include='**/verify-email.component.spec.ts'` | 23 tests réussis |
| Backoffice | Même commande ChromeHeadless | 275 tests réussis |
| Builds | `npm run build` dans web/backoffice | Build SSR web et SPA backoffice réussis |
| Types web | `npx tsc --noEmit -p tsconfig.app.json` | Réussi |
| Diff | `git diff --check` | Réussi |

```sh
./mvnw verify -Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=EmailChangeIntegrationTests,PasswordSessionRaceIntegrationTests \
  -Dspring.datasource.url=jdbc:postgresql://localhost:55433/ms102 \
  -Dspring.datasource.username=ms102 -Dspring.datasource.password=disposable-ms102 \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dspring.flyway.enabled=true \
  -Dspring.flyway.locations=filesystem:src/main/resources/db/migration \
  -Dspring.jpa.hibernate.ddl-auto=validate -Dspring.jpa.show-sql=false
```

La localisation explicite exclut le V1 H2 de test. PostgreSQL 18 émet l’avis de compatibilité Flyway déjà connu ; migrations, validation et tests réussissent.

## Standards

Deux revues indépendantes Sol sur `b56e384...HEAD` : aucun écart aux consignes ni smell actionnable. Service transactionnel, écriture ciblée, ordre utilisateur → jetons et signaux Angular conservés. Vérification complémentaire de la correction inter-onglets sans défaut.

## Spec

Un P2 initial : identité obsolète dans l’autre onglet après confirmation. Reproduit rouge, corrigé et retesté vert ; revue complémentaire confirme sa résolution. Aucun autre écart ni élargissement de périmètre. Bilan final : Standards 0, Spec 0 restant.

## Ressources et livraison

`docker compose -p ms102 -f /tmp/ms102-compose.yml down -v` a supprimé seulement le conteneur et réseau PostgreSQL #102. Aucun SMTP réel, déploiement, promotion main, modification d’autres worktrees ou démarrage de #103. PR vers dev, CI finale, squash et nettoyage sont suivis dans les documents de completion et dans la copie locale exportée après livraison.

Livraison : [PR #133](https://github.com/ibrahima-isi/meetstudent/pull/133). [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38528795149) sur `48256a32c58889ed1fbeb8ecf515de20f98b33a7` : API 2m39s, web 1m8s, backoffice 43s. Le commit documentaire final est soumis à une nouvelle CI avant squash ; PR et copie locale exportée conservent le bilan définitif.
