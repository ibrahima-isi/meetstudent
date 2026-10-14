# Ticket #103 — preuves (2026-10-14)

Base `40802dd6176a5a369002aba8e017fad91d8fbf90`, worktree `/tmp/meetstudent-ticket-103`, branche `feat/session-inactivity-103`. Sources live : tickets #103/#99, passation #102 ; aucune importation des checkouts historiques. Les interfaces HTTP et Angular et le périmètre multi-fichiers étaient approuvés dans #99.

## Comportement

Chaque connexion crée une session UUID indépendante ; refresh change le secret sur la même ligne et le JWT porte `sid`. Access et refresh refusent à sept jours révolus depuis l'activité serveur. Réponses métier authentifiées 2xx sur mutations et lectures explicitement marquées après interaction prolongent uniquement leur session. Refresh, `/auth`, chargements initiaux, lectures passives et erreurs n'étendent pas. La version des identifiants et la suppression globale des lignes continuent à révoquer tous les appareils. Horloge injectée partagée avec l'émetteur et le vérificateur JWT.

Choix et limites de transition : [ADR 0002](../adr/0002-session-inactivity.md). Les anciens JWT sans identité d'appareil partagent la ligne legacy de leur compte jusqu'à rotation ; les nouvelles connexions sont indépendantes. V21 accorde sept jours depuis le déploiement aux sessions encore valides, conserve leurs secrets et ne réactive pas les refresh déjà expirés ; une ligne de compatibilité couvre aussi les access JWT dont l'ancien refresh a été supprimé. `expiryDate` n'impose plus de plafond absolu aux sessions actives.

## Red → green

Premier test HTTP avant produit : access au seuil exact attendu 401, reçu 200 (`/tmp/ms103-red-http.log`). Après implémentation, seuil exact et refresh seul sont verts. Couverture HTTP supplémentaire : activité/mutations et GET explicite, appareil indépendant, session active encore utilisable au jour 18, lectures passives et mutations invalides, tentative de réactivation, révocation globale après changement de mot de passe, transition legacy sans durée absolue.

Trois nouvelles courses utilisent les barrières JDBC déjà présentes, pas des départs simultanés : refresh traversant le seuil, activité juste avant le seuil et requête autorisée avant mais terminant après expiration. La dernière peut finir sa lecture déjà autorisée, sans réactiver la session. Les treize courses des tickets précédents restent exercées.

Frontend : [preuves détaillées](ticket-103-frontend.md), rouges observés puis web 940 / backoffice 286 tests verts, types et builds des deux apps réussis. Refresh unique partagé, purge et invitation fr/en/admin, 401 du retry, SSR et marqueur d'activité browser trusted couverts.

## PostgreSQL

PostgreSQL 18 jetable `ms103`, port 55434. Flyway production V1–V20 puis fixtures SQL valides, access sans refresh, refresh expiré, puis V21. Assertions SQL : secret valide conservé avec grâce >6 jours, compte access-only couvert, refresh expiré encore expiré, aucune identité/activité nulle, V21 réussie. Schéma Hibernate validé.

61 tests HTTP/courses verts (sans échec/erreur/skip) avec :

```sh
./mvnw verify -Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=SessionInactivityIntegrationTests,PasswordSessionRaceIntegrationTests,AuthIntegrationTests,EmailChangeIntegrationTests,ErrorDispatchIntegrationTests \
  -Dspring.datasource.url=jdbc:postgresql://localhost:55434/ms103 \
  -Dspring.datasource.username=ms103 -Dspring.datasource.password=disposable-ms103 \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dspring.flyway.enabled=true -Dspring.flyway.locations=filesystem:src/main/resources/db/migration \
  -Dspring.jpa.hibernate.ddl-auto=validate -Dspring.jpa.show-sql=false
```

Première passe sur `0b68512`, puis relance sur le code final après centralisation du délai et enrichissement bearer : **61 tests réussis**, zéro échec/erreur/skip, BUILD SUCCESS (`/tmp/ms103-postgres-final.log`, 27,952 s). Les premiers refus d'attachement Mockito sandbox et la fixture SQL sans timestamps obligatoires étaient des erreurs de lancement/fixture corrigées, pas des rouges fonctionnels.

## Standards

Revue indépendante Sol sur `40802dd...0b68512` : aucune violation ferme, une observation facultative P3 sur centralisation de la durée d'inactivité. Durée et prédicat centralisés dans le service pendant la revue ; aucun changement de politique. Relecture du delta : Standards 0 violation, 0 observation restante.

## Spec

Revue indépendante Sol : un P2 initial supposait un cache JPA partagé entre filtre et contrôleur. La reproduction enrichie avec bearer/barrière après authentification passe sans correction (deux cas verts, `/tmp/ms103-osiv-red.log`). La source locale Spring Boot 3.5.13 confirme que l’interceptor OSIV MVC ouvre son contexte après le filtre JWT. Le reviewer retire donc cette hypothèse ; aucun défaut ni scope creep retenu. Le test enrichi est conservé. Relecture du delta : Spec 0 finding retenu.

## Livraison

Suite API complète finale (`/tmp/ms103-api-delivery.log`) : **226 unitaires + 302 intégrations**, zéro échec/erreur/skip, BUILD SUCCESS, 1m44s. Le code final inclut la centralisation du délai et la course enrichie avec bearer.

HTTP réel après V20→V21 sur la base séparée `ms103legacy` : trois anciens JWT sans `pv`/`sid` encore acceptés (200), dont compte sans refresh et compte dont le refresh a expiré ; deux rotations réelles (200) conservent le `sid`, et le refresh historique expiré répond 401. Logs `/tmp/ms103-live-legacy.log`. Le premier essai utilisait des fixtures que les suites HTTP venaient de nettoyer : il n’était pas une régression produit ; séparation des bases puis succès réel.

La validation automatique hors sandbox a été temporairement indisponible à 08h04 UTC (quota). Reprise explicitement demandée par le propriétaire après rétablissement : PostgreSQL final réussi, serveur API temporaire arrêté normalement (session `12618`, PID `72497`). Aucun contournement, déploiement, SMTP réel, promotion main ou début de #104.

PR vers dev et CI finale à enregistrer ci-dessous avant squash. PostgreSQL jetable `ms103` est supprimé via `docker compose -p ms103 -f /tmp/ms103-compose.yml down -v` ; l'inventaire vide est vérifié. Les autres sessions/worktrees restent préservés. Le nettoyage du seul worktree/branche #103 suivra comparaison du head au squash et préservation.
