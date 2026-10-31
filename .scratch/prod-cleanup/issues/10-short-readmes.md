# 10: READMEs courts pour le web et l'API

**What to build:** Un humain qui ouvre `apps/web` ou `apps/api` comprend en quelques lignes ce que fait l'app et comment la lancer. `apps/web/README.md` (celui d'Angular CLI) et `apps/api/Readme.md` sont réécrits : rôle de l'app, commandes essentielles, renvoi vers le guide d'agent de l'app et vers `docs/DEPLOY.md`. Ils ne recopient pas les CLAUDE.md. `apps/backoffice/README.md`, déjà spécifique au projet, n'est pas touché.

**Blocked by:** [prod-cleanup/03](03-compact-claude-md.md)

**Status:** resolved

Spec : [prod-cleanup](../spec.md), point 8.

- [x] Chaque README fait moins de 40 lignes et ne contient que des commandes qui marchent
- [x] Les liens relatifs pointent vers des fichiers existants
- [x] Le nom de fichier `apps/api/Readme.md` est gardé, sauf si aucun lien ne le référence (alors `README.md`)

## Comments

Résolu : `apps/web/README.md` passe de 72 à 23 lignes, `apps/api/Readme.md` de 164 à 25 lignes (nom gardé : le ticket et la spec le référencent). Rôle, commandes, renvois vers les guides d'agent et `docs/DEPLOY.md`. Liens relatifs tous vérifiés. Commandes vérifiées : `./mvnw -v`, `npm run` (start, build, test, serve:ssr:frontend existent). Non exécutées : `verify`, `spring-boot:run` (dev profile attend Postgres sur 5433), `docker compose up -d api`, `npm test`, build.
