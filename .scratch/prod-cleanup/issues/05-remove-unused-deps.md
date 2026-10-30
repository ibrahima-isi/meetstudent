# 05: Retirer les dépendances inutiles

**What to build:** Les builds ne tirent plus de paquets sans usage. On retire `spring-boot-starter-oauth2-resource-server` (scope test, aucun import `oauth2`) de `apps/api/pom.xml`, puis `karma-coverage` et `karma-jasmine-html-reporter` de `apps/web` et `apps/backoffice`, avec leurs références dans `karma.conf.js` ou `angular.json` s'il y en a. Une dépendance encore réellement utilisée (référence trouvée, ou build ou test qui casse) est gardée, avec la raison notée dans le ticket.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [prod-cleanup](../spec.md), point 5. Vérifier au passage que `spring-boot-devtools` est exclu du jar de production (exclusion dans `spring-boot-maven-plugin`) ; rien à changer si c'est le cas.

- [ ] `./mvnw clean verify` passe dans `apps/api` sans oauth2-resource-server
- [ ] `npm ci && npm run build && npm test -- --no-watch --browsers=ChromeHeadless` passe dans `apps/web` et `apps/backoffice`
- [ ] Les lockfiles sont régénérés par `npm install`, pas édités à la main
- [ ] Le résultat de la vérification devtools est noté

## Comments
