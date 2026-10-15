# Ticket #107 — choix du type de compte à l’inscription

Base isolée : squash #106 sur `dev` à `4ed101e0c6d08f6627de855557743fee204c6a31`, branche `feat/registration-role-107`.

## Contrat et TDD

- Avant correction, les tests HTTP échouaient : un rôle `ROLE_EXPERT` demandait 201 mais recevait un compte `ROLE_STUDENT` ; `ROLE_ADMIN` demandait 400 et recevait 201. Le formulaire n’envoyait aucun rôle et n’affichait pas l’erreur de champ `role`.
- `RegisterRequest.role` accepte seulement `ROLE_STUDENT` et `ROLE_EXPERT` ; rôle absent → étudiant. Le service vérifie aussi cette liste avant toute sauvegarde, indépendamment de la validation HTTP. Les rôles administrateur et inconnu produisent 400 avec champ `role`, sans créer de compte.
- Le choix existant du formulaire envoie le rôle correspondant ; les deux boutons exposent `aria-pressed`, l’erreur API de rôle apparaît près du choix puis disparaît après changement. Un test de parcours enseignant couvre aussi l’auto-connexion et la navigation en anglais ; les parcours fr/en existants restent couverts.

## Vérifications locales

- API : suite complète 561 tests, zéro échec, après simplification du service ; tests ciblés des rôles verts.
- Web : 943 tests headless, contrôle TypeScript et build SSR verts.
- Backoffice inchangé : 300 tests headless, contrôle TypeScript et build de production verts.
- PostgreSQL 18 jetable : Flyway V1–V23, schéma validé et trois tests HTTP ciblés verts. Requête SQL : un compte `ROLE_EXPERT`, un `ROLE_STUDENT` et aucun compte issu des demandes de rôles interdits. Conteneur arrêté et supprimé.

Les revues Sol Standards et Spec n’ont aucun constat actionnable après suppression de la validation de rôle dupliquée dans le DTO. [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38795669488) sur `6488b54`. Le présent closeout documentaire doit passer les trois CI sur son head final avant squash de la [PR #138](https://github.com/ibrahima-isi/meetstudent/pull/138) vers `dev`.
