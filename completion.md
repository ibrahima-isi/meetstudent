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

## Dernier ticket traité : #102

- [x] Spec #99, ticket et dépendance #100 relus ; base dev `b56e384`.
- [x] Worktree isolé, branche `fix/email-confirmation-session-revocation`.
- [x] TDD HTTP/Angular ; anciennes sessions et identité fr/en révoquées après confirmation seulement.
- [x] Compatibilité historique des trois rôles, courses JDBC et synchronisation inter-onglets vérifiées.
- [x] API 226 unitaires + 294 intégrations ; PostgreSQL 20 migrations et 39 tests HTTP/courses.
- [x] Web 930 tests, build SSR et types ; backoffice 275 tests et build.
- [x] Revues Sol Standards/Spec ; P2 inter-onglets corrigé, aucun écart restant.
- [x] Ressources PostgreSQL du seul ticket #102 supprimées.
- [x] [PR #133](https://github.com/ibrahima-isi/meetstudent/pull/133) vers dev ; [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38528795149) sur `48256a3`.
- [ ] CI du closeout documentaire avant squash.
- [ ] Squash et nettoyage du seul worktree/branche #102.

Preuves : [docs/evidence/ticket-102.md](docs/evidence/ticket-102.md). Arrêt après #102, sans commencer #103. La copie locale exportée recevra le bilan définitif de livraison.

## Livraison des 30 tickets

- [x] [#100](https://github.com/ibrahima-isi/meetstudent/issues/100) — [01/30] Changer son mot de passe et révoquer les anciennes sessions
- [x] [#101](https://github.com/ibrahima-isi/meetstudent/issues/101) — [02/30] Réinitialiser par email le mot de passe de tous les rôles, admins inclus
- [x] [#102](https://github.com/ibrahima-isi/meetstudent/issues/102) — [03/30] Révoquer les sessions après confirmation du nouvel email
- [x] [#103](https://github.com/ibrahima-isi/meetstudent/issues/103) — [04/30] Faire expirer chaque session après 7 jours d’inactivité
- [x] [#104](https://github.com/ibrahima-isi/meetstudent/issues/104) — [05/30] Supprimer un compte avec confirmation et nettoyage fiable
- [x] [#105](https://github.com/ibrahima-isi/meetstudent/issues/105) — [06/30] Retourner des erreurs API utiles sur les mutations et conflits
- [x] [#106](https://github.com/ibrahima-isi/meetstudent/issues/106) — [07/30] Désassocier une accréditation sans détruire la donnée partagée
- [x] [#107](https://github.com/ibrahima-isi/meetstudent/issues/107) — [08/30] Choisir étudiant ou enseignant à l’inscription
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

### #103 — sessions par appareil (2026-10-14)

- [x] [#103](https://github.com/ibrahima-isi/meetstudent/issues/103) : implémentation et vérifications locales requises terminées. Base isolée `40802dd`, branche `feat/session-inactivity-103` ; PR/CI/fusion suivies séparément.

Sessions UUID indépendantes, rotation stable, inactivité serveur de sept jours au seuil exact, refresh/passivité exclus, activité après interaction explicite ou écriture réussie, révocation globale et grâce historique V21. Choix d’activité confirmé par le propriétaire le 2026-10-14. Web/backoffice : purge et invitation à se reconnecter, sans boucle ni accès SSR au stockage.

Preuves : [ticket-103.md](docs/evidence/ticket-103.md). API finale : 226 unitaires + 302 intégrations ; PostgreSQL V20→V21 et 61 HTTP/courses verts sur code final, plus anciens credentials via HTTP réel sur schéma migré ; web 940 et backoffice 286 tests, types et builds verts. Revues Sol finales : Standards 0 / Spec 0 (hypothèse OSIV retirée après test et vérification de l’ordre réel des contextes).

Livraison : [PR #134](https://github.com/ibrahima-isi/meetstudent/pull/134) vers dev ; [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38570741641) sur `b74a6f63af2e2dc00db1eba1d33cbeef171dcdd5` (API 2m27s, web 1m11s, backoffice 34s). Le closeout documentaire passe aussi les trois CI avant squash ; le statut définitif de fusion est celui de la PR, et le bilan final de fusion/nettoyage est conservé dans la copie locale exportée. API/PostgreSQL jetables arrêtés/supprimés. Arrêt après #103 ; #104 non commencé.

### #104 — suppression de compte (2026-10-14)

- [x] [#104](https://github.com/ibrahima-isi/meetstudent/issues/104) : implémentation et vérifications locales requises terminées. Base `e84ce2b`, branche `feat/account-deletion-104` ; livraison PR/CI/fusion suivie séparément.

Mot de passe actuel requis pour soi (administrateur inclus), exemption admin uniquement sur autrui, 204 vide et erreurs 400/401/403/404. Sessions et trois types de notes nettoyés sous verrou du compte ; documents/wishlist supprimés, autres comptes/notes/catalogue préservés. Jetons d'action orphelins inutilisables, budget d'adresse et rétention existants conservés. Aucun nouveau parcours client.

Preuves : [docs/evidence/ticket-104.md](docs/evidence/ticket-104.md). API complète **226 unitaires + 308 intégrations**, PostgreSQL **35 HTTP/courses** avec V1–V21 et schéma validé, web **940** et backoffice **286** tests, types et builds verts. Revues Sol : Standards **0 violation ferme**, une répétition JPA facultative conservée ; Spec **0 écart**. PostgreSQL jetable nettoyé et inventaire vide vérifié. Livraison : [PR #135](https://github.com/ibrahima-isi/meetstudent/pull/135) vers dev ; [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38584026934) sur `8b8a202f26891279d69f99de06c1c328f708f0ee` (API 3m33s, web 1m05s, backoffice 33s). Le présent closeout documentaire doit à son tour passer les trois CI sur son head final avant squash ; statut définitif sur la PR, bilan de fusion/nettoyage conservé dans la copie locale exportée. Arrêt après #104, sans commencer #105.

### #105 — erreurs de mutations (2026-10-15)

- [x] [#105](https://github.com/ibrahima-isi/meetstudent/issues/105) : implémentation et validation locales terminées. Worktree isolé `meetstudent-ticket-105` depuis `ab5d08b`, branche `fix/api-mutation-errors-105`.

Preuves locales : [docs/evidence/ticket-105.md](docs/evidence/ticket-105.md). API 226 unitaires + 325 intégrations ; PostgreSQL V1–V22 et deux cas HTTP verts ; backoffice 289 tests/types/build, web 940 tests/types/build SSR. Revues Sol Standards/Spec finales : 0 écart. [PR #136](https://github.com/ibrahima-isi/meetstudent/pull/136) squashée dans dev à `93635e0` après [CI finale API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38752013959) sur `c5b86a4` ; issue #105 fermée, PostgreSQL jetable nettoyé et worktree archivé. #106 suit.

### #106 — désassociation d’accréditation (2026-10-15)

- [x] [#106](https://github.com/ibrahima-isi/meetstudent/issues/106) : implémentation et validation terminées dans `fix/accreditation-unlink-106` depuis le squash #105 `93635e0`.

[Preuves](docs/evidence/ticket-106.md) : retrait du seul lien programme/accréditation et nouveau lien direct cours/accréditation sur choix explicite du propriétaire ; donnée et autres liens conservés, erreurs 403/404/409. API 558 tests, backoffice 300 tests/types/build, web 940 tests/types/build SSR, PostgreSQL V1–V23 avec schéma validé et HTTP de partage cours/programme vert. Revues Sol Standards/Spec sans écart. [PR #137](https://github.com/ibrahima-isi/meetstudent/pull/137) squashée dans dev à `4ed101e` après [CI finale API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38792763175) sur `a4006f1` ; issue #106 fermée, contenu du squash vérifié, worktree archivé. #107 suit.

### #107 — choix étudiant ou enseignant (2026-10-15)

- [x] [#107](https://github.com/ibrahima-isi/meetstudent/issues/107) : implémentation et validation terminées dans `feat/registration-role-107` depuis le squash #106 `4ed101e`.

[Preuves](docs/evidence/ticket-107.md) : tests HTTP et formulaire rouges puis verts pour le choix enseignant, le défaut étudiant, le refus des rôles interdits, l’erreur de champ et l’auto-connexion enseignant en anglais. API 561 tests, web 943 tests/types/build SSR, backoffice 300 tests/types/build verts ; PostgreSQL V1–V23 et trois tests HTTP verts. Revues Sol Standards/Spec sans écart. [PR #138](https://github.com/ibrahima-isi/meetstudent/pull/138) squashée dans dev à `745ada7` après [CI finale API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38797021310) sur `7e23cfa` ; issue #107 fermée, contenu du squash vérifié, worktree archivé. #108 suit.

### #108 — spécialité enseignant et erreurs de champ (2026-10-15)

- [ ] [#108](https://github.com/ibrahima-isi/meetstudent/issues/108) : issue assignée, branche isolée `feat/teacher-specialty-108` depuis le squash #107 `745ada7`.

[Preuves provisoires](docs/evidence/ticket-108.md) : tests HTTP et formulaire rouges puis verts pour spécialité absente/blanche, 100/101 caractères, affichage local/API et changement enseignant → étudiant. API 564 tests, web 948 tests/types/build SSR, backoffice 300 tests/types/build verts ; PostgreSQL V1–V23 et cinq HTTP verts. Revue Spec sans écart, remarque Standards corrigée ; relecture finale, CI et squash de la [PR #139](https://github.com/ibrahima-isi/meetstudent/pull/139) à venir. #109 attend la livraison de #108.
