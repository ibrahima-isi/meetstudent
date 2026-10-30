# Spec : prod-cleanup

Status: ready-for-agent

Origine : séance de grilling du 2026-10-30, avant le premier déploiement en production (`coolify-deploy/05`). Inventaire en lecture seule fait sur `main` @ `0863a41`.

## Problem Statement

Le dépôt arrive en production avec du poids mort et une mémoire dispersée :

- **Des fichiers que rien n'utilise** : le prototype React `apps/web/meetstudent/` (70 fichiers, aucun build ni job CI ne le référence), des tests manuels de l'API (`dummy.txt`, `seed_and_test.js`, `api_tests.robot`), des dossiers locaux vides (`shared/`, `target/`).
- **Une documentation historique terminée** : `docs/completion.md` (0 ticket ouvert), `completion.md` à la racine (doublon), `docs/evidence/`, `docs/superpowers/`, `apps/api/docs/superpowers/`, `apps/web/docs/plans/`, et le plan de déploiement du 2026-10-26, remplacé par les ADR 0003/0004, `docs/DEPLOY.md` et la spec `coolify-deploy`.
- **Des CLAUDE.md trop lourds** : 23 Ko à la racine et 37 Ko dans `apps/api`. Ils mêlent règles stables, état daté (hash de commit, « Project state ») et faits répétés à plusieurs endroits.
- **Une mémoire d'agent hors du dépôt** : les notes de `~/.claude/projects/…/memory/` répètent le dépôt ou contiennent des instantanés qui vieillissent.
- **Des dépendances inutiles** (`spring-boot-starter-oauth2-resource-server`, `karma-coverage`, `karma-jasmine-html-reporter`) et des versions patch/minor en retard. Dependabot ne surveille que GitHub Actions.
- **Des READMEs génériques ou périmés** (`apps/web/README.md` est celui d'Angular CLI ; `apps/api/Readme.md` est à revérifier).

## Solution

Une PR par thème vers `dev`, toutes fusionnées avant `coolify-deploy/05` :

1. Supprimer les fichiers morts.
2. Supprimer la documentation historique (Git garde l'historique).
3. Compacter les CLAUDE.md : règles stables et renvois. Chaque fait à un seul endroit, plus d'état daté.
4. Faire du dépôt la seule source de vérité : migrer les faits uniques de la mémoire perso, puis la vider.
5. Retirer les dépendances inutiles.
6. Monter en patch/minor l'API, le web et le back-office, chacun dans sa PR.
7. Étendre Dependabot à npm et Maven.
8. Réécrire en court les READMEs du web et de l'API.

## Implementation Decisions

- **Le dépôt est la source de vérité de la mémoire du projet.** La mémoire perso `~/.claude` ne garde que des préférences personnelles (note de signature 1Password).
- **Pas de version majeure avant la prod.** Angular reste en 20.3.x, Spring Boot en 3.5.x, lucide-angular en 0.x, Flyway en 10.x. Les majors (Angular 22, Spring Boot 4, lucide 1.0) feront l'objet de tickets séparés après le déploiement.
- **Supprimer plutôt qu'archiver** : pas de `docs/archive/`, l'historique Git suffit.
- **À conserver** : `docs/plans/2026-10-10-backoffice-vite-rewrite.md` (travail à venir), les ADR, `GLOSSARY.md`, `docs/DEPLOY.md`, `docs/MONOREPO.md`, `apps/api/docs/production.md`, `apps/web/docs/backend-api-integration.md`, `docs/deploy/coolify-roadmap.html`, les specs `.scratch/` ouvertes.
- **Ne pas toucher** au worktree `.claude/worktrees/deploy-job-failure-3fc07f` : une session l'utilise encore.
- Exécution : un ticket = une branche = un worktree, confié à un subagent Sonnet ; relecture du diff avant la PR.
- Aucun job CI requis (`api`, `web`) n'est renommé ni retiré.

## Testing Decisions

- Les tickets qui touchent des dépendances passent `./mvnw clean verify` (api) ou `npm run build` + `npm test -- --no-watch --browsers=ChromeHeadless` (web, back-office), avant et après le changement. Le résultat réel est noté dans le ticket.
- Les tickets de suppression vérifient avec `git grep` qu'aucun fichier restant ne référence un chemin supprimé, puis que les builds passent.
- Les tickets de doc vérifient que chaque lien relatif modifié pointe vers un fichier existant, et que `infra/tests/deploy-docs.test.sh` passe.

## Out of Scope

- Les montées de version majeures.
- La réécriture du back-office en Vite.
- Les tickets `p2-backlog`. Exception : `prod-cleanup/03` remplace la partie CLAUDE.md de `p2-backlog/15`, ce qui est noté dans le ticket.
- La suppression des branches distantes : le propriétaire la fait (HTTP 403 depuis les sessions d'agents).
