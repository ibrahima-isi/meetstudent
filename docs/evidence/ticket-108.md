# Ticket #108 — spécialité enseignant et erreurs de champ

Base isolée : squash #107 sur `dev` à `745ada7278e29864945a56ce9c275043660400cb`, branche `feat/teacher-specialty-108`.

## TDD et comportement

- Tests HTTP rouges : l’API acceptait un enseignant sans qualification et la longueur 101 dépassait la colonne sans erreur de champ. Le PATCH de profil renvoyait une erreur générique pour la même limite. Correction : `ROLE_EXPERT` exige une qualification non blanche ; l’inscription et la mise à jour du profil nettoient et limitent la qualification à 100 caractères, avec réponse 400 sous `qualification`. Étudiant sans spécialité accepté.
- Tests web rouges : la spécialité enseignant n’était pas envoyée dans `qualification`, la limite de 100 caractères n’était pas validée et l’erreur API n’était pas affichée près du champ. Correction : validation requise et longueur maximale selon le rôle, messages locaux fr/en et erreur serveur sous la spécialité, effacée à la modification ; un retour au choix étudiant ne transmet pas l’ancienne spécialité.
- Tests de frontière ciblés verts : qualification absente, blanche, 100 et 101 caractères à l’inscription, 101 caractères en PATCH ; champ absent, trop long, valide, changement de rôle et erreur serveur côté formulaire. Les parcours de connexion automatique existants sont conservés.

## Vérifications locales

- Web : 948 tests headless, contrôle TypeScript et build SSR verts.
- API : 564 tests, zéro échec, après validation partagée entre inscription et PATCH de profil.
- Backoffice inchangé : 300 tests headless, contrôle TypeScript et build de production verts.
- PostgreSQL 18 jetable neuf : Flyway V1–V23, schéma validé et cinq tests HTTP ciblés verts (qualification absente, 100/101 caractères, mise à jour de profil, étudiant sans spécialité). SQL : version 23, un enseignant avec spécialité de 100 caractères et un étudiant sans spécialité ; aucune inscription invalide enregistrée. Conteneur arrêté et supprimé.

Revues Sol Standards/Spec finales sans écart après regroupement du rendu d’erreur, puis 948 tests web/types/build SSR verts. [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38840438774) sur `cce4c21`. Le présent closeout documentaire doit passer les trois CI sur son head final avant squash de la [PR #139](https://github.com/ibrahima-isi/meetstudent/pull/139) vers `dev`.
