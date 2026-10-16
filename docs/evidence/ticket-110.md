# #110 — une note par utilisateur et établissement

Ticket : [#110](https://github.com/ibrahima-isi/meetstudent/issues/110), spécification [#99](https://github.com/ibrahima-isi/meetstudent/issues/99). Base immuable : `557adcd425c51733e31170f0eb9ae052733d7d05`, branche `fix/school-rating-uniqueness-110`. Qualification exécutée le 2026-10-16 avec Java 21.0.11.

## Changement et portée

Le comportement métier était déjà présent dans la base : POST crée en 201, renoter met à jour en 200 avec le même identifiant ; la contrainte `uk_school_rates_user_school` et le traitement de sa collision donnent 409. V24 réconcilie déjà les doublons des trois types. Aucun contrôleur, service ou fichier Flyway n'a été modifié ; #111/#112 ne sont pas livrés par ce ticket.

Les tests supplémentaires qualifient les garanties manquantes du suivi #110 :

- HTTP : auteur A note 2, auteur B (EXPERT) note 3, A renote 5 ; deux notes restent et la moyenne publiée devient 4. L'identifiant de A reste stable, la note de B et la note de A sur une autre école (1) restent intactes. STUDENT et EXPERT conservent leurs capacités existantes.
- HTTP : un ADMIN reçoit 403 en tentant de remplacer une note ; la liste publique confirme que la note de l'étudiant et son identifiant restent intacts. Les tests existants couvrent aussi 401 anonyme, usurpation d'utilisateur, commentaires et identifiant client hostile.
- Concurrence HTTP : deux requêtes du même auteur atteignent toutes deux le véritable INSERT après une recherche sans résultat. Une barrière sur la frontière JDBC suspend ces INSERT, puis les libère ensemble. Ni service ni dépôt n'est mocké et aucune exception n'est fabriquée. La vraie base impose exactement 201/409 ; la réponse 409 est structurée, GET expose une seule note et la moyenne correspond au gagnant. Le retry répond 200 avec le même identifiant, une seule note et moyenne 4.

Le nouveau fichier conserve le nom `SchoolRatePostgresConcurrencyIntegrationTests` autorisé dans le manifeste ; il tourne aussi sur H2 dans la suite normale, sans skip. Comme les garanties produit existaient déjà, ces tests de qualification sont verts sur la base existante : aucun rouge métier artificiel n'est revendiqué.

## Vérification HTTP et suite API

Commandes depuis `apps/api`, avec `JAVA_HOME=/Users/ibrahimadiallo/.sdkman/candidates/java/21.0.11-tem` passé uniquement au processus :

```sh
./mvnw verify -Dit.test=SchoolRateControllerIntegrationTests,SchoolRatePostgresConcurrencyIntegrationTests \
  -Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false
./mvnw clean verify
```

Ciblés H2 : **14 tests, zéro échec/erreur/skip**, BUILD SUCCESS (`/tmp/ms110-targeted.log`, 14,545 s). Suite API complète : **608 tests (239 unitaires + 369 intégration), zéro échec/erreur/skip**, BUILD SUCCESS (`/tmp/ms110-api-full.log`, 2 min 17 s).

Pour PostgreSQL, trois exécutions HTTP séparées utilisent une base fraîche chacune (`ms110http`, `ms110aggregate`, `ms110roles`) afin de ne pas confondre l'isolation H2 par contexte des fixtures existantes avec un nettoyage SQL. Chaque commande `./mvnw verify` sélectionne respectivement `SchoolRatePostgresConcurrencyIntegrationTests`, `SchoolRateControllerIntegrationTests#reRatingChangesOnlyTheAuthorsSchoolRatingAndRecomputesTheAverage` et `SchoolRateControllerIntegrationTests#administratorCannotCreateOrReplaceASchoolRating`, avec :

```sh
-Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false \
-Dspring.datasource.url=jdbc:postgresql://localhost:55440/BASE \
-Dspring.datasource.username=ms110 -Dspring.datasource.password=disposable-ms110 \
-Dspring.datasource.driver-class-name=org.postgresql.Driver \
-Dspring.flyway.enabled=true \
-Dspring.flyway.locations=filesystem:src/main/resources/db/migration \
-Dspring.jpa.hibernate.ddl-auto=validate -Dspring.jpa.show-sql=false
```

**3 tests PostgreSQL 18.6 verts, zéro échec/erreur/skip**, Flyway V1–V24 et schéma Hibernate validés. Logs : `/tmp/ms110-postgres-race.log` (6,834 s), `/tmp/ms110-postgres-aggregate.log` (7,022 s), `/tmp/ms110-postgres-roles.log` (6,482 s).

## Réconciliation PostgreSQL réelle

PostgreSQL 18 jetable, projet Compose `ms110`, port local 55440, stockage tmpfs. Une première exécution Spring/HTTP avec `spring.flyway.target=23` applique V1–V23 depuis le répertoire des migrations de production ; `ddl-auto=validate` passe et un test HTTP anonyme passe (`/tmp/ms110-postgres-v23.log`). Aucun V1 H2 de test n'est chargé.

Dans cette base `ms110`, neuf lignes historiques sont injectées directement :

| Identifiants et paire | Données avant V24 | Résultat attendu puis constaté |
|---|---|---|
| 700001–700003, utilisateur 600001 / école 600001 | 700001 daté 3 janvier, 700002 daté 1 janvier malgré son ID supérieur, 700003 sans date | 700001, note 5 |
| 700004–700005, même auteur / école 600002 | Dates égales le 2 janvier | 700005, note 4 |
| 700006–700007, même auteur / école 600003 | Deux dates NULL | 700007, note 3 |
| 700008, utilisateur 600002 / école 600001 | Autre auteur | Conservé, note 2 |
| 700009, utilisateur 600001 / école 600004 | Autre cible | Conservé, note 1 |

Le lanceur Java temporaire utilise l'API `Flyway.configure().dataSource(...).locations("filesystem:src/main/resources/db/migration").load()`, puis `migrate()` et `validate()`. Il applique **uniquement V24** à la base V23 et valide les checksums des **24 migrations**. Une assertion SQL compare les survivants à la liste littérale `[700001,700005,700007,700008,700009]`. Une tentative d'INSERT supplémentaire de la même paire est rejetée par PostgreSQL avec `unique_violation` (SQLSTATE 23505) et le nom exact `uk_school_rates_user_school` ; les autres erreurs ne sont pas avalées. `pg_constraint` confirme `UNIQUE (user_id, school_id)`.

Enfin l'application Spring démarre sur cette même base réconciliée avec `ddl-auto=validate`, affiche `MS110_UPGRADED_SCHEMA_VALIDATED`, puis ferme proprement le contexte et le serveur à port éphémère. Logs : `/tmp/ms110-migration-before.log`, `/tmp/ms110-migration-flyway.log`, `/tmp/ms110-migration-after.log`, `/tmp/ms110-upgraded-validate.log`. Fixtures/assertions temporaires : `/tmp/ms110-fixtures.sql`, `/tmp/ms110-assert-migration.sql` ; lanceurs `/tmp/Ms110Migrate.java` et `/tmp/Ms110Validate.java`.

Flyway 10.20.1 avertit que PostgreSQL 18.6 est plus récent que sa version testée (17) ; migration, validation et HTTP ont effectivement réussi. Deux erreurs de lancement corrigées ne sont pas des rouges produit : option Surefire initiale de sélection sans `surefire.failIfNoSpecifiedTests=false`, puis DevTools incompatible avec le lanceur Java source temporaire (relancé avec `-Dspring.devtools.restart.enabled=false`, sans changement de configuration du dépôt).

## Livraison et nettoyage

`docker compose -p ms110 -f /tmp/ms110-compose.yml down -v` a supprimé le conteneur et réseau #110. L'inventaire Docker filtré sur le projet `ms110` est vide. Les conteneurs préexistants et les autres worktrees sont préservés. Aucun SMTP réel, déploiement ou promotion main.

Revues indépendantes Standards/Spec, CI finale API/web/backoffice, PR vers dev et squash restent à exécuter et à relier par l'orchestrateur. Le suivi reste unchecked tant que les vérifications requises ne sont pas toutes acquises.
