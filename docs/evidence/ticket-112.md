# #112 — une note par utilisateur et cours

Ticket : [#112](https://github.com/ibrahima-isi/meetstudent/issues/112), spécification [#99](https://github.com/ibrahima-isi/meetstudent/issues/99). Base immuable `66f2ca52817054c63de31f9195f26b5a3438d12a`, branche `test/course-rating-uniqueness-112`. Qualification du 2026-10-16 avec Java 21.0.11, manifeste de quatre fichiers autorisé par le propriétaire.

## Portée et résultats observables

PR #142 avait déjà livré l'upsert, le 409 et V24 pour les trois types de notes. Aucune migration n'a été modifiée ; un seul fichier produit a changé, `CourseRateDTO` (voir le trou `courseId` plus bas). Les nouveaux tests qualifient ce comportement existant et sont verts sans correctif : aucun rouge métier artificiel n'est revendiqué.

- HTTP : deux EXPERT notent le même cours (2 et 3), le premier renote 5 : 200, même identifiant, deux notes, moyenne 4. La note indépendante reste 3 ; la note du premier sur un autre cours reste 1, moyenne 1.
- HTTP : STUDENT et ADMIN reçoivent 403 ; anonyme reçoit 401. Après chaque refus, GET confirme l'unique note de l'expert (identifiant, auteur, valeur 4) et la moyenne inchangée à 4.
- Concurrence : deux requêtes EXPERT du même auteur atteignent les véritables INSERT `course_rates` après avoir trouvé la paire absente (barrière JDBC, sans mock de service/dépôt). Résultat exactement 201/409 structuré ; une seule note et la moyenne du gagnant ; retry : 200, même identifiant, une note, moyenne 4. La classe tourne aussi sur H2 dans la suite complète, sans skip.

## Trou `courseId` nul : constaté puis fermé (décision propriétaire)

`CourseRateDTO.courseId` n'avait pas de `@NotNull` et le contrôleur ne renseigne la cible que si `courseId != null`. Sonde sur H2 : deux POST EXPERT sans `courseId` renvoient chacun 201 avec `"courseId":null`; `UNIQUE (user_id, course_id)` ne les arrête pas (NULL n'est jamais égal à NULL).

TDD : test `testThatCreateCourseRateWithoutCourseIdIsRejectedWith400` ajouté d'abord. **Rouge réel** : `Status expected:<400> but was:<201>` (1 test, 1 échec). Correctif minimal : `@NotNull(message = "Course ID is required")` sur `CourseRateDTO.courseId` (même style que `SchoolRateDTO`, sans `@Positive`). **Vert** : le test attend 400 avec `$.courseId = "Course ID is required"` sur deux POST, puis zéro note en base. Ciblé : `CourseRateControllerIntegrationTests` 14, `CourseRatePostgresConcurrencyIntegrationTests` 1, `RateValidationIntegrationTests` 5, `RateRaceConflictIntegrationTests` 3, `CourseControllerIntegrationTests` 15, `CourseRateControllerTest` 2, zéro échec/erreur/skip.

Suivi hors périmètre, non traité : `ProgramRateDTO.programId` n'a pas de `@NotNull` (même trou probable, non sondé). `SchoolRateDTO.schoolId` est déjà `@NotNull`.

## Commandes et tests

Depuis `apps/api`, passer `JAVA_HOME=/Users/ibrahimadiallo/.sdkman/candidates/java/21.0.11-tem` au seul processus Maven :

```sh
./mvnw verify -Dit.test=CourseRateControllerIntegrationTests,CourseRatePostgresConcurrencyIntegrationTests -Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false
./mvnw verify -Dit.test='Course*IntegrationTests,ProgramRate*IntegrationTests,SchoolRate*IntegrationTests,RateValidationIntegrationTests,RateRaceConflictIntegrationTests' -Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false
./mvnw clean verify
```

H2 ciblé : `CourseRateControllerIntegrationTests` 13 tests (11 existants + 2 nouveaux), `CourseRatePostgresConcurrencyIntegrationTests` 1 test. Famille cours et notes : `CourseControllerIntegrationTests` 15, programme 13+1, établissement 13+1, validation 5, course critique 3. Zéro échec/erreur/skip partout.

Suite API finale (après correctif `courseId`) : **615 tests (239 unitaires + 376 intégration), zéro échec/erreur/skip**, BUILD SUCCESS, 2 min 18 s.

## PostgreSQL

Conteneur jetable `ms112-pg` (`postgres:18`, 18.6, port hôte 55451 lié à 127.0.0.1, tmpfs, mot de passe aléatoire non journalisé), supprimé ensuite par `docker rm -f ms112-pg`. Même recette que #111 : répertoire de migrations de production, `-Dspring.flyway.locations=filesystem:src/main/resources/db/migration`, `-Dspring.jpa.hibernate.ddl-auto=validate`, `-Dspring.jpa.show-sql=false`, `-Dspring.datasource.driver-class-name=org.postgresql.Driver`.

1. V1–V23 : `-Dspring.flyway.target=23`, sélection `CourseRateControllerIntegrationTests#testThatStudentCannotCreateCourseRate`. Résultat : 1 test vert ; `flyway_schema_history` finit à `23 | t`.
2. Fixtures V23 dans la base `ms112` (neuf lignes `course_rates`, deux utilisateurs 600001–600002, quatre cours 600001–600004) :

```sql
INSERT INTO course_rates(id,user_id,course_id,created_at,note,comment) VALUES
 (700001,600001,600001,'2026-01-03',5,'latest timestamp wins'),
 (700002,600001,600001,'2026-01-01',1,'higher id older'),
 (700003,600001,600001,NULL,2,'null loses to dated'),
 (700004,600001,600002,'2026-01-02',2,'tie lower id'),
 (700005,600001,600002,'2026-01-02',4,'tie higher id wins'),
 (700006,600001,600003,NULL,1,'null lower id'),
 (700007,600001,600003,NULL,3,'null higher id wins'),
 (700008,600002,600001,'2026-01-01',2,'other user preserved'),
 (700009,600001,600004,'2026-01-01',1,'other target preserved');
```

3. V24 appliqué seul par un lanceur Flyway (`Flyway.configure()...migrate(); validate();`) : `Migrating schema "public" to version "24 - unique rate per user and target"`, puis `Successfully applied 1 migration ... now at version v24` et `Successfully validated 24 migrations`.
4. Survivants constatés : **700001, 700005, 700007, 700008, 700009**, notes **5, 4, 3, 2, 1**. La date la plus récente gagne même face à un identifiant supérieur ; date NULL perd face à une date ; égalité de dates ou deux NULL : identifiant supérieur. Autre auteur et autre cible conservés.
5. Contrainte : un INSERT doublon dans un bloc `DO` est réellement rejeté (`unique_violation`, `CONSTRAINT_NAME = uk_course_rates_user_course`) ; `pg_get_constraintdef` renvoie `UNIQUE (user_id, course_id)`.
6. Hibernate : application Spring réelle (`ddl-auto=validate`, Flyway sur la base réconciliée, port éphémère) imprime `MS112_UPGRADED_SCHEMA_VALIDATED` et se ferme proprement. Flyway 10.20.1 avertit que PostgreSQL 18.6 dépasse sa version testée 17 ; migration, validation et HTTP ont effectivement réussi.
7. Tests HTTP sur bases fraîches (`createdb`), Flyway V1 à V24 depuis zéro, `ddl-auto=validate` : `ms112race` → `CourseRatePostgresConcurrencyIntegrationTests` 1 vert (201/409 réel, une ligne, retry 200 même identifiant) ; `ms112aggregate` → `reRatingChangesOnlyTheAuthorsCourseRatingAndRecomputesTheAverage` 1 vert ; `ms112roles` → `unauthorizedRolesAndAnonymousCannotCreateOrReplaceACourseRating` 1 vert. Zéro échec/erreur/skip.

Les identités MockMvc testent l'autorisation du rating ; ces tests ne prétendent pas qualifier l'émission des JWT.

## Nettoyage

Conteneur `ms112-pg` supprimé ; aucun conteneur ni volume `ms112` restant. Aucun autre conteneur touché. Aucun SMTP, déploiement ni promotion main. Revues indépendantes et PR vers `dev` : en attente.
