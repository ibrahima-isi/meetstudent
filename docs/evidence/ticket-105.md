# Ticket #105 — erreurs utiles des mutations

Base de travail : `origin/dev` à `ab5d08bfb9c437c420e2db995b991e5068c99157`, worktree isolé `meetstudent-ticket-105`.

## Contrats vérifiés

- Tags : doublon 500 → 409, nom absent 500 → 400 `{name}`, retrait d’un tag utilisé 500 → 409 avec tag conservé.
- Accréditations : code déjà pris 500 → 409, nom absent 201 → 400 `{name}`, retrait d’une accréditation liée 500 → 409 avec accréditation conservée ; la mise à jour avec son propre code reste 200.
- Programmes et cours : codes dupliqués 500 → 409 ; références `schoolId`/`programId` invalides renvoient 400 avec le champ concerné. Un lien programme/accréditation répété renvoie 409 au lieu de 201.
- Rôles : nom absent/trop long renvoie 400, suppression d’un rôle attribué renvoie 409 ; V22 avance l’identité PostgreSQL sans modifier les rôles existants.
- Backoffice : 409 affiche le conflit, y compris la liaison répétée programme/accréditation, et garde la saisie/la liste ; une vraie panne 500 garde un message de panne, sans prétendre que le code est dupliqué. Les erreurs 500 de l’API ne divulguent plus les messages internes.

Chaque cas initial indiqué ci-dessus a été observé rouge au point d’entrée HTTP ou dans le rendu Angular avant sa correction, puis vert au test ciblé.

## Vérifications locales des 2026-10-14 et 2026-10-15

- `apps/api`: `./mvnw clean verify -q` sur le code corrigé après revue — 226 tests unitaires et 325 intégrations, 0 échec.
- `apps/backoffice`: nouveau test de liaison 409 observé rouge (`L'association a échoué`), puis vert ; `npm test -- --no-watch --browsers=ChromeHeadless` — 289 tests verts ; `npx tsc -p tsconfig.app.json --noEmit` et `npm run build` verts (Node 22.21.1).
- `apps/web`: 940 tests verts, types et build SSR verts (Node 22.21.1), sans modification web.
- PostgreSQL 18 jetable `meetstudent-105-postgres`, Flyway explicitement sur `src/main/resources/db/migration`, `ddl-auto=validate` : V1–V22 appliquées et application démarrée. Insertion de rôle après V22 : ID 5 ; rôles seedés 1/3/4 conservés. Deux tests HTTP sur PostgreSQL migré verts : conflit de tag et refus de suppression d’accréditation liée.

Revues indépendantes Sol : Standards a signalé la transaction de suppression de rôle et les réponses Swagger 409 manquantes ; Spec a signalé le message de liaison 409 du backoffice. Les trois constats ont été corrigés ; relectures Standards et Spec du commit `abbd474` : aucun écart restant. [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38751277592) sur `abbd4745da8c082906a405383edba0780ca6a893`. PostgreSQL jetable arrêté et supprimé. [PR #136](https://github.com/ibrahima-isi/meetstudent/pull/136) en brouillon ; CI du closeout documentaire et squash en attente. La désassociation reste au ticket #106 ; aucune promotion de `main`.
