# #111 — une note par utilisateur et programme

Ticket : [#111](https://github.com/ibrahima-isi/meetstudent/issues/111), spécification [#99](https://github.com/ibrahima-isi/meetstudent/issues/99). Base immuable `e18edf84e5a0f8df105febe6563681dcf43d1453`, branche `test/program-rating-uniqueness-111`. Qualification du 2026-10-16 avec Java 21.0.11, manifeste de cinq fichiers autorisé par le propriétaire.

## Portée et résultats observables

PR #142 avait déjà livré les upserts, contraintes et V24 pour les trois types de notes. Aucun fichier produit ni migration n'a été modifié. Les nouveaux tests qualifient ce comportement existant et sont verts sans correctif : aucun rouge métier artificiel n'est revendiqué. #112 demeure séparé.

- HTTP : deux EXPERT notent le même programme (2 et 3), le premier renote 5 : même identifiant, deux notes, moyenne 4. La note indépendante reste 3 ; la note du premier sur un autre programme reste 1, moyenne 1.
- HTTP : STUDENT et ADMIN reçoivent 403 ; anonyme reçoit 401. Après chaque refus, GET confirme l'unique note de l'expert, son auteur, son identifiant et sa valeur 4 ; moyenne inchangée à 4. Aucune capacité nouvelle. Les tests existants continuent de couvrir usurpation, identifiant client hostile, commentaires et répétition.
- Concurrence : deux requêtes EXPERT du même auteur atteignent les véritables INSERT après avoir trouvé la paire absente. La barrière JDBC les libère ensemble, sans mock de service/dépôt ni exception fabriquée. Résultat exactement 201/409 structuré ; GET montre une seule note et la moyenne du gagnant. Retry : 200, même identifiant, une note et moyenne 4. La classe `ProgramRatePostgresConcurrencyIntegrationTests` tourne aussi sur H2 en CI sans skip.

## Commandes et tests

Depuis `apps/api`, passer `JAVA_HOME=/Users/ibrahimadiallo/.sdkman/candidates/java/21.0.11-tem` au seul processus Maven :

```sh
./mvnw verify -Dit.test=ProgramRatePostgresConcurrencyIntegrationTests -Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false
./mvnw verify -Dit.test=ProgramRateControllerIntegrationTests,ProgramRatePostgresConcurrencyIntegrationTests -Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false
./mvnw clean verify
```

Première tranche course H2 : 1 test vert (`/tmp/ms111-race-h2.log`). Tranche agrégat H2 : 1 test vert (`/tmp/ms111-aggregate-h2.log`). Ciblés avant ajout du test des rôles : 13 tests verts (`/tmp/ms111-targeted.log`). Le test des rôles ajouté ensuite est vérifié sur PostgreSQL puis dans la suite API finale.

Suite API finale : **611 tests (239 unitaires +372 intégration), zéro échec/erreur/skip**, BUILD SUCCESS, 2 min18s (`/tmp/ms111-api-full.log`). `git diff --check` vert.

PostgreSQL 18.6 : **3 tests HTTP verts, zéro échec/erreur/skip**. Chaque test utilise une base fraîche, respectivement `ms111race`, `ms111aggregate`, `ms111roles`, créée par `docker compose -p ms111 -f /tmp/ms111-compose.yml exec -T postgres createdb -U ms111 BASE`. Exécuter les trois sélections suivantes séparément avec les options communes ci-dessous :

```sh
-Dit.test=ProgramRatePostgresConcurrencyIntegrationTests
-Dit.test=ProgramRateControllerIntegrationTests#reRatingChangesOnlyTheAuthorsProgramRatingAndRecomputesTheAverage
-Dit.test=ProgramRateControllerIntegrationTests#unauthorizedRolesAndAnonymousCannotCreateOrReplaceAProgramRating
```

```sh
-Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false -Dspring.datasource.url=jdbc:postgresql://localhost:55441/BASE -Dspring.datasource.username=ms111 -Dspring.datasource.password=disposable-ms111 -Dspring.datasource.driver-class-name=org.postgresql.Driver -Dspring.flyway.enabled=true -Dspring.flyway.locations=filesystem:src/main/resources/db/migration -Dspring.jpa.hibernate.ddl-auto=validate -Dspring.jpa.show-sql=false
```

Logs : `/tmp/ms111-postgres-race.log`, `/tmp/ms111-postgres-aggregate.log`, `/tmp/ms111-postgres-roles.log`. Flyway V1–V24 et Hibernate validés. Les identités MockMvc testent l'autorisation du rating ; ces tests ne prétendent pas qualifier l'émission des JWT.

## Migration PostgreSQL reproductible

Stack créée uniquement pour #111, port55441, stockage tmpfs. Recréer `/tmp/ms111-compose.yml` avec :

```yaml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: ms111
      POSTGRES_USER: ms111
      POSTGRES_PASSWORD: disposable-ms111
    ports: ["127.0.0.1:55441:5432"]
    tmpfs: [/var/lib/postgresql]
```

Démarrer avec `docker compose -p ms111 -f /tmp/ms111-compose.yml up -d`. Sur la base `ms111`, exécuter la sélection `ProgramRateControllerIntegrationTests#testThatStudentCannotCreateProgramRate` avec les options PostgreSQL ci-dessus et `-Dspring.flyway.target=23`. Résultat : V1–V23 appliquées, schéma validé, 1 test HTTP vert (`/tmp/ms111-postgres-v23.log`). Utiliser le répertoire des migrations de production : les ressources de test contiennent un V1 H2 concurrent.

Injecter les fixtures via `docker compose -p ms111 -f /tmp/ms111-compose.yml exec -T postgres psql -U ms111 -d ms111 < /tmp/ms111-fixtures.sql` :

```sql
\set ON_ERROR_STOP on
INSERT INTO users(id,email,created_at,modified_at) VALUES (600001,'migration-one@example.com',now(),now()),(600002,'migration-two@example.com',now(),now());
INSERT INTO programs(id,code,name) VALUES (600001,'F01','Latest'),(600002,'F02','Tie'),(600003,'F03','Null'),(600004,'F04','Other');
INSERT INTO program_rates(id,user_id,program_id,created_at,note,comment) VALUES
 (700001,600001,600001,'2026-01-03',5,'latest timestamp wins'),
 (700002,600001,600001,'2026-01-01',1,'higher id older'),
 (700003,600001,600001,NULL,2,'null loses to dated'),
 (700004,600001,600002,'2026-01-02',2,'tie lower id'),
 (700005,600001,600002,'2026-01-02',4,'tie higher id wins'),
 (700006,600001,600003,NULL,1,'null lower id'),
 (700007,600001,600003,NULL,3,'null higher id wins'),
 (700008,600002,600001,'2026-01-01',2,'other user preserved'),
 (700009,600001,600004,'2026-01-01',1,'other target preserved');
SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
SELECT id,user_id,program_id,created_at,note FROM program_rates WHERE id>=700001 ORDER BY id;
```

Appliquer ensuite uniquement V24 et vérifier les checksums des 24 migrations avec ce lanceur `/tmp/Ms111Migrate.java` :

```java
import org.flywaydb.core.Flyway;
public class Ms111Migrate {
  public static void main(String[] args) {
    var flyway = Flyway.configure().dataSource("jdbc:postgresql://localhost:55441/ms111", "ms111", "disposable-ms111")
      .locations("filesystem:src/main/resources/db/migration").load();
    flyway.migrate();
    flyway.validate();
  }
}
```

```sh
./mvnw dependency:build-classpath -Dmdep.outputFile=/tmp/ms111-classpath.txt
/Users/ibrahimadiallo/.sdkman/candidates/java/21.0.11-tem/bin/java -cp "target/classes:$(cat /tmp/ms111-classpath.txt)" /tmp/Ms111Migrate.java
```

Assertions via la même commande `psql`, entrée `/tmp/ms111-assert-migration.sql` :

```sql
\set ON_ERROR_STOP on
DO $$
DECLARE actual integer[]; violated text;
BEGIN
 SELECT array_agg(id ORDER BY id) INTO actual FROM program_rates WHERE id>=700001;
 IF actual <> ARRAY[700001,700005,700007,700008,700009] THEN RAISE EXCEPTION 'Unexpected survivors: %',actual; END IF;
 BEGIN
  INSERT INTO program_rates(user_id,program_id,note) VALUES(600001,600001,1);
  RAISE EXCEPTION 'Unique constraint did not reject duplicate';
 EXCEPTION WHEN unique_violation THEN
  GET STACKED DIAGNOSTICS violated = CONSTRAINT_NAME;
  IF violated <> 'uk_program_rates_user_program' THEN RAISE EXCEPTION 'Wrong constraint: %',violated; END IF;
 END;
END $$;
SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
SELECT id,user_id,program_id,created_at,note FROM program_rates WHERE id>=700001 ORDER BY id;
SELECT conname,pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='uk_program_rates_user_program';
```

Survivants constatés : **700001,700005,700007,700008,700009**, notes **5,4,3,2,1**. La date la plus récente gagne même face à un ID supérieur ; date NULL perd face à une date ; égalité de dates ou deux NULL : ID supérieur. Autre auteur et autre cible conservés. L'INSERT doublon est réellement rejeté avec SQLSTATE **23505**, contrainte **uk_program_rates_user_program** ; `pg_constraint` confirme `UNIQUE (user_id, program_id)`. Logs avant/Flyway/après : `/tmp/ms111-migration-before.log`, `/tmp/ms111-migration-flyway.log`, `/tmp/ms111-migration-after.log`.

Enfin démarrer Spring sur cette même base réconciliée avec ce lanceur `/tmp/Ms111Validate.java`, classpath ci-dessus et `-Dspring.devtools.restart.enabled=false` :

```java
import org.springframework.boot.SpringApplication;
import com.bowe.meetstudent.MeetstudentApplication;
public class Ms111Validate {
 public static void main(String[] args) {
  try (var context = SpringApplication.run(MeetstudentApplication.class,
   "--spring.profiles.active=test", "--server.port=0",
   "--spring.datasource.url=jdbc:postgresql://localhost:55441/ms111",
   "--spring.datasource.username=ms111", "--spring.datasource.password=disposable-ms111",
   "--spring.datasource.driver-class-name=org.postgresql.Driver", "--spring.flyway.enabled=true",
   "--spring.flyway.locations=filesystem:src/main/resources/db/migration",
   "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.show-sql=false", "--app.mail.enabled=false")) {
   System.out.println("MS111_UPGRADED_SCHEMA_VALIDATED");
  }
 }
}
```

Résultat : `MS111_UPGRADED_SCHEMA_VALIDATED`, serveur sur port éphémère, fermeture propre (`/tmp/ms111-upgraded-validate.log`). Flyway 10.20.1 avertit que PostgreSQL18.6 dépasse sa version testée17 ; migration, validation et HTTP ont effectivement réussi.

## Revues indépendantes

Base immuable : `e18edf84e5a0f8df105febe6563681dcf43d1453`. Commit examiné : `22668c6ee20732d13b7016644a8f0465cb2793b0` — `test(api): qualify program rating uniqueness (#111)`. Diff : `git diff e18edf84e5a0f8df105febe6563681dcf43d1453...HEAD`. Deux agents enfants distincts ont examiné ce même diff en parallèle, après la fin des phases lecture et implémentation.

### Standards

**Zéro violation certaine ; deux remarques facultatives.** Sources : `CLAUDE.md`, `apps/api/CLAUDE.md`, `docs/agents/issue-tracker.md`, `docs/MONOREPO.md` ; aucun `CODING_STANDARDS.md` ni `CONTRIBUTING.md` trouvé. Baseline Fowler examinée : Mysterious Name, Duplicated Code, Feature Envy, Data Clumps, Primitive Obsession, Repeated Switches, Shotgun Surgery, Divergent Change, Speculative Generality, Message Chains, Middle Man, Refused Bequest ; priorité aux règles documentées et exclusion des contrôles déjà imposés par les outils.

- Injection par champs dans `ProgramRatePostgresConcurrencyIntegrationTests:45–51` : la règle constructeur/Lombok de la section Architecture du guide API vise les couches de production ; les fixtures voisines utilisent déjà les champs. Portée ambiguë, aucune violation dure retenue.
- Possible Duplicated Code dans les observations HTTP note/cardinalité/moyenne (`ProgramRatePostgresConcurrencyIntegrationTests:75–90`, `ProgramRateControllerIntegrationTests:359–367`). Les assertions courtes exposent les états vérifiés ; extraction facultative, aucun défaut fonctionnel établi.

Arbitrage reviewer : conserver ces fixtures et assertions lisibles, conformément à la portée autorisée et à la règle de modifications chirurgicales ; aucun correctif requis.

### Spec

**Zéro constat.** Sources GitHub : [#111](https://github.com/ibrahima-isi/meetstudent/issues/111) et [parent #99](https://github.com/ibrahima-isi/meetstudent/issues/99), relus pendant la revue. Répétition avec identifiant conservé, indépendance auteurs/cibles, agrégats, refus STUDENT/ADMIN/anonyme sans effet, vraie course INSERT 201/409 puis retry 200 : couverts par les tests HTTP. Les journaux corroborent V23→V24, cinq survivants déterministes, SQLSTATE23505, contrainte exacte et démarrage Hibernate validate. Aucun manque, dépassement de portée ou comportement incorrect relevé. Qualification honnête du comportement existant de PR #142 ; livraison encore en attente.

Les deux agents ont effectué une revue statique en lecture seule ; l'axe Spec a aussi recoupé les journaux existants. Ils n'ont pas relancé les tests. Le reviewer a relancé séparément les 14 tests HTTP programme H2, zéro échec/erreur/skip (`/tmp/ms111-review-targeted.log`). Aucun changement de code depuis la suite API complète et les preuves PostgreSQL ; le présent ajout est documentaire.

## Livraison et nettoyage

`docker compose -p ms111 -f /tmp/ms111-compose.yml down -v` effectué ; inventaires containers/volumes/networks filtrés par `com.docker.compose.project=ms111` vides. Ressources préexistantes préservées. Aucun SMTP réel, déploiement ni promotion main.

Qualification et revues indépendantes terminées ; PR vers dev, trois CI sur head final, squash et clôture restent à effectuer par l'orchestrateur. Le suivi reste unchecked pour la livraison.
