# 06: API : montées patch/minor

**What to build:** L'API passe sur les dernières versions patch/minor compatibles : parent `spring-boot-starter-parent` en dernière 3.5.x, et les versions épinglées dans `apps/api/pom.xml` (Flyway 10.x, modelmapper, java-jwt, springdoc 2.8.x, datafaker, plugins surefire/failsafe) en dernière version sans changement de major. Une version épinglée que Spring Boot gère déjà passe sous la gestion du parent quand c'est sans risque.

**Blocked by:** [prod-cleanup/05](05-remove-unused-deps.md)

**Status:** resolved

Spec : [prod-cleanup](../spec.md), point 6 et « pas de version majeure avant la prod ».

- [x] Tableau avant/après des versions dans la PR
- [x] `./mvnw clean verify` passe (unit + intégration, Postgres réel du job CI)
- [x] Aucune migration Flyway modifiée ou ajoutée
- [x] Aucune montée de major

## Comments

| Dependency | Before | After |
|---|---|---|
| spring-boot-starter-parent | 3.5.13 | 3.5.16 |
| flyway-core / flyway-database-postgresql | 10.20.1 | 10.22.0 |
| modelmapper | 3.2.0 | 3.2.8 |
| java-jwt | 4.5.1 | 4.6.2 |
| springdoc-openapi-starter-webmvc-ui | 2.8.16 | 2.8.17 |
| datafaker | 2.3.0 | 2.7.0 |
| maven-surefire-plugin / maven-failsafe-plugin | 3.5.2 | 3.6.1 |

Results: `./mvnw clean verify` before and after: BUILD SUCCESS, 1331 tests run, 0 failures, 0 errors, 8 skipped (unit + integration, H2 in-process). No Flyway migration touched. No major bump; nothing reverted. Flyway stays pinned at 10.x because the Boot 3.5 parent manages 11.7.2 (a different major). Surefire/failsafe stay pinned because the parent manages older versions; no pin moved under parent management.
