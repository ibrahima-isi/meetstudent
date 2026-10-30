# 05: Retirer les dépendances inutiles

**What to build:** Les builds ne tirent plus de paquets sans usage. On retire `spring-boot-starter-oauth2-resource-server` (scope test, aucun import `oauth2`) de `apps/api/pom.xml`, puis `karma-coverage` et `karma-jasmine-html-reporter` de `apps/web` et `apps/backoffice`, avec leurs références dans `karma.conf.js` ou `angular.json` s'il y en a. Une dépendance encore réellement utilisée (référence trouvée, ou build ou test qui casse) est gardée, avec la raison notée dans le ticket.

**Blocked by:** None (can start immediately)

**Status:** resolved

Spec : [prod-cleanup](../spec.md), point 5. Vérifier au passage que `spring-boot-devtools` est exclu du jar de production (exclusion dans `spring-boot-maven-plugin`) ; rien à changer si c'est le cas.

- [ ] `./mvnw clean verify` passe dans `apps/api` sans oauth2-resource-server
- [ ] `npm ci && npm run build && npm test -- --no-watch --browsers=ChromeHeadless` passe dans `apps/web` et `apps/backoffice`
- [ ] Les lockfiles sont régénérés par `npm install`, pas édités à la main
- [ ] Le résultat de la vérification devtools est noté

## Comments

**Resultat (2026-10-30)**

- `spring-boot-starter-oauth2-resource-server` retire de `apps/api/pom.xml` (aucun import `oauth2` dans `apps/api/src`).
- `karma-coverage` et `karma-jasmine-html-reporter` GARDES dans `apps/web` et `apps/backoffice`. Il n'y a ni `karma.conf.js` ni reference dans `angular.json` : le builder `@angular/build:karma` charge sa config Karma par defaut, qui exige ces deux plugins. Apres retrait, `npm test` echoue : `Cannot find module 'karma-jasmine-html-reporter'` (puis `'karma-coverage'` une fois le premier remis). Dependances reellement utilisees, donc conservees. Package.json et lockfiles inchanges.
- Verification : meme commande avant et apres, tout vert.
  - Avant (baseline) : `./mvnw clean verify` exit 0 ; web build OK + 1685/1685 tests ; backoffice build OK + 498/498 tests.
  - Apres : `./mvnw clean verify` exit 0 (sans oauth2-resource-server) ; web et backoffice inchanges (retrait des deux paquets annule), donc resultats identiques a la baseline.
- devtools : `spring-boot-devtools` est en scope `runtime` et `spring-boot-maven-plugin` l'exclut du jar repackage par defaut (`excludeDevtools=true`). Verifie : `unzip -l apps/api/target/meetstudent.jar | grep -ci devtools` = 0. Rien a changer.
