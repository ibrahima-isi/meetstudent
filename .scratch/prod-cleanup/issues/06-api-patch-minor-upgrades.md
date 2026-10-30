# 06: API : montées patch/minor

**What to build:** L'API passe sur les dernières versions patch/minor compatibles : parent `spring-boot-starter-parent` en dernière 3.5.x, et les versions épinglées dans `apps/api/pom.xml` (Flyway 10.x, modelmapper, java-jwt, springdoc 2.8.x, datafaker, plugins surefire/failsafe) en dernière version sans changement de major. Une version épinglée que Spring Boot gère déjà passe sous la gestion du parent quand c'est sans risque.

**Blocked by:** [prod-cleanup/05](05-remove-unused-deps.md)

**Status:** ready-for-agent

Spec : [prod-cleanup](../spec.md), point 6 et « pas de version majeure avant la prod ».

- [ ] Tableau avant/après des versions dans la PR
- [ ] `./mvnw clean verify` passe (unit + intégration, Postgres réel du job CI)
- [ ] Aucune migration Flyway modifiée ou ajoutée
- [ ] Aucune montée de major

## Comments
