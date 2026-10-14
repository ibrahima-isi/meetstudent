# Suivi local des tickets MeetStudent

Spec : [#99](https://github.com/ibrahima-isi/meetstudent/issues/99). Mettre à jour ce fichier après chaque ticket ; les preuves détaillées restent dans [docs/completion.md](docs/completion.md). Cocher l’implémentation après vérification ; suivre séparément la fusion et le nettoyage.

## Ticket précédent : #100

- [x] Spec et dépendances lues : aucun blocage GitHub.
- [x] Worktree isolé depuis `origin/dev` (`b236bc1`), branche `fix/password-session-revocation`.
- [x] Régressions HTTP et formulaire observées rouges, puis vertes.
- [x] Courses login/refresh déterministes ; suppression expérimentale du contrôle de version détectée.
- [x] Migration PostgreSQL V19 → V20 avec lignes historiques conservées en version zéro.
- [x] Web : 927 tests et build SSR ; backoffice : 271 tests et build réussis.
- [x] API : 226 tests unitaires + 273 intégrations ; PostgreSQL : 15 tests HTTP/courses.
- [x] Revue Standards / Spec terminée ; course email corrigée et retestée.
- [x] [PR #131](https://github.com/ibrahima-isi/meetstudent/pull/131) ouverte vers dev.
- [x] [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38413822495) sur `ccb655a`.
- [x] Squash `5b37b2a` via PR #131 et nettoyage réalisés (passation vérifiée : #100 fermé).

## Dernier ticket traité : #101

- [x] Spec #99 et dépendance #100 relues ; #100 fermé, base `5b37b2a`.
- [x] Worktree isolé `f21e`, branche `fix/password-reset-all-roles`.
- [x] Régressions HTTP et backoffice observées rouges puis vertes.
- [x] Reset de tous les rôles, révocation access/refresh et liens sensibles vérifiés.
- [x] Bootstrap sûr et redémarrage complet après reset admin vérifiés.
- [x] API : 226 unitaires + 286 intégrations ; PostgreSQL : 20 migrations, 38 tests HTTP/courses.
- [x] Web : 927 tests et build SSR ; backoffice : 275 tests et build.
- [x] Parcours fr/en et lien backoffice vérifiés au navigateur, desktop/mobile.
- [x] Revues Standards/Spec terminées ; documentation de révocation corrigée.
- [x] PostgreSQL et serveurs navigateur jetables arrêtés/supprimés.
- [x] [PR #132](https://github.com/ibrahima-isi/meetstudent/pull/132) vers dev ; [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38525554851) sur `b408ac0`.

Fusion : consulter le statut définitif de la PR #132. Le closeout documentaire doit aussi passer les trois CI avant squash ; la copie locale exportée conserve le bilan de fusion et du nettoyage après livraison.

Preuves : [docs/evidence/ticket-101.md](docs/evidence/ticket-101.md). Arrêt après #101 ; aucun ticket suivant commencé.

## Livraison des 30 tickets

- [x] [#100](https://github.com/ibrahima-isi/meetstudent/issues/100) — [01/30] Changer son mot de passe et révoquer les anciennes sessions
- [x] [#101](https://github.com/ibrahima-isi/meetstudent/issues/101) — [02/30] Réinitialiser par email le mot de passe de tous les rôles, admins inclus
- [ ] [#102](https://github.com/ibrahima-isi/meetstudent/issues/102) — [03/30] Révoquer les sessions après confirmation du nouvel email
- [ ] [#103](https://github.com/ibrahima-isi/meetstudent/issues/103) — [04/30] Faire expirer chaque session après 7 jours d’inactivité
- [ ] [#104](https://github.com/ibrahima-isi/meetstudent/issues/104) — [05/30] Supprimer un compte avec confirmation et nettoyage fiable
- [ ] [#105](https://github.com/ibrahima-isi/meetstudent/issues/105) — [06/30] Retourner des erreurs API utiles sur les mutations et conflits
- [ ] [#106](https://github.com/ibrahima-isi/meetstudent/issues/106) — [07/30] Désassocier une accréditation sans détruire la donnée partagée
- [ ] [#107](https://github.com/ibrahima-isi/meetstudent/issues/107) — [08/30] Choisir étudiant ou enseignant à l’inscription
- [ ] [#108](https://github.com/ibrahima-isi/meetstudent/issues/108) — [09/30] Valider la spécialité enseignant et afficher les erreurs au bon champ
- [ ] [#109](https://github.com/ibrahima-isi/meetstudent/issues/109) — [10/30] Masquer les favoris étudiants pour les enseignants
- [ ] [#110](https://github.com/ibrahima-isi/meetstudent/issues/110) — [11/30] Conserver une seule note par utilisateur et établissement
- [ ] [#111](https://github.com/ibrahima-isi/meetstudent/issues/111) — [12/30] Conserver une seule note par utilisateur et programme
- [ ] [#112](https://github.com/ibrahima-isi/meetstudent/issues/112) — [13/30] Conserver une seule note par utilisateur et cours
- [ ] [#113](https://github.com/ibrahima-isi/meetstudent/issues/113) — [14/30] Archiver le worktree de configuration des skills
- [ ] [#114](https://github.com/ibrahima-isi/meetstudent/issues/114) — [15/30] Nettoyer le worktree auto-login après libération sûre de sa session
- [ ] [#115](https://github.com/ibrahima-isi/meetstudent/issues/115) — [16/30] Masquer le bloc de création de compte pour les utilisateurs connectés
- [ ] [#116](https://github.com/ibrahima-isi/meetstudent/issues/116) — [17/30] Afficher un footer unique sur À propos et Contact
- [ ] [#117](https://github.com/ibrahima-isi/meetstudent/issues/117) — [18/30] Présenter connexion et inscription du footer comme des boutons
- [ ] [#118](https://github.com/ibrahima-isi/meetstudent/issues/118) — [19/30] Découvrir et rechercher les programmes
- [ ] [#119](https://github.com/ibrahima-isi/meetstudent/issues/119) — [20/30] Découvrir et rechercher les cours
- [ ] [#120](https://github.com/ibrahima-isi/meetstudent/issues/120) — [21/30] Découvrir les accréditations dans un catalogue fonctionnel
- [ ] [#121](https://github.com/ibrahima-isi/meetstudent/issues/121) — [22/30] Relier Cherchez aux recherches établissement, programme et cours
- [ ] [#122](https://github.com/ibrahima-isi/meetstudent/issues/122) — [23/30] Compléter les quatre liens Produit dans le footer
- [ ] [#123](https://github.com/ibrahima-isi/meetstudent/issues/123) — [24/30] Comparer des établissements de bout en bout
- [ ] [#124](https://github.com/ibrahima-isi/meetstudent/issues/124) — [25/30] Comparer des programmes
- [ ] [#125](https://github.com/ibrahima-isi/meetstudent/issues/125) — [26/30] Comparer des cours
- [ ] [#126](https://github.com/ibrahima-isi/meetstudent/issues/126) — [27/30] Ouvrir le classement des établissements et son top 10
- [ ] [#127](https://github.com/ibrahima-isi/meetstudent/issues/127) — [28/30] Ajouter le classement des programmes
- [ ] [#128](https://github.com/ibrahima-isi/meetstudent/issues/128) — [29/30] Renommer la navigation en Découvrir / Discover
- [ ] [#129](https://github.com/ibrahima-isi/meetstudent/issues/129) — [30/30] Vérifier l’ensemble, promouvoir main et terminer le nettoyage
