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
- [x] [#108](https://github.com/ibrahima-isi/meetstudent/issues/108) — [09/30] Valider la spécialité enseignant et afficher les erreurs au bon champ
- [x] [#109](https://github.com/ibrahima-isi/meetstudent/issues/109) — [10/30] Masquer les favoris étudiants pour les enseignants
- [x] [#110](https://github.com/ibrahima-isi/meetstudent/issues/110) — [11/30] Conserver une seule note par utilisateur et établissement — livré par [PR #144](https://github.com/ibrahima-isi/meetstudent/pull/144), squash signé `e18edf8` ; [preuves](docs/evidence/ticket-110.md).
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
- [x] [#121](https://github.com/ibrahima-isi/meetstudent/issues/121) — [22/30] Relier Cherchez aux recherches établissement, programme et cours — implémentation et validations locales terminées : 1070 tests web, 630 API, 300 backoffice, builds/types et navigateur réels ; revues finales Standards 0 / Spec 0. Première CI complète verte ; livraison et CI définitives dans la [PR #157](https://github.com/ibrahima-isi/meetstudent/pull/157), clôture/nettoyage conservés dans le bilan exporté. [Preuves](docs/evidence/ticket-121.md).
- [x] [#122](https://github.com/ibrahima-isi/meetstudent/issues/122) — [23/30] Compléter les quatre liens Produit dans le footer — implémentation et validations terminées : 33 tests ciblés, 1075 web, types/build SSR verts, 96 parcours Produit et 48 compte réels fr/en/bureau/mobile/clair/sombre, revues Standards 0 / Spec 0. Smoke nettoyé. [PR #158](https://github.com/ibrahima-isi/meetstudent/pull/158) vers dev ; CI du head définitif requise avant squash, livraison/clôture/nettoyage conservés dans le bilan exporté après fusion. [Preuves](docs/evidence/ticket-122.md).
- [x] [#123](https://github.com/ibrahima-isi/meetstudent/issues/123) — [24/30] Comparer des établissements de bout en bout — livré : [PR #159](https://github.com/ibrahima-isi/meetstudent/pull/159) squashée dans `dev` (`9dc763b`), ticket clôturé.
- [x] [#124](https://github.com/ibrahima-isi/meetstudent/issues/124) — [25/30] Comparer des programmes — livré : [PR #160](https://github.com/ibrahima-isi/meetstudent/pull/160) squashée dans `dev` (`66ea034`), ticket clôturé.
- [x] [#125](https://github.com/ibrahima-isi/meetstudent/issues/125) — [26/30] Comparer des cours — livré : [PR #161](https://github.com/ibrahima-isi/meetstudent/pull/161) squashée dans `dev` (`764c0a2`), ticket clôturé.
- [x] [#126](https://github.com/ibrahima-isi/meetstudent/issues/126) — [27/30] Ouvrir le classement des établissements et son top 10
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

- [x] [#108](https://github.com/ibrahima-isi/meetstudent/issues/108) : implémentation et validation terminées dans `feat/teacher-specialty-108` depuis le squash #107 `745ada7`.

[Preuves](docs/evidence/ticket-108.md) : tests HTTP et formulaire rouges puis verts pour spécialité absente/blanche, 100/101 caractères, affichage local/API et changement enseignant → étudiant. API 564 tests, web 948 tests/types/build SSR, backoffice 300 tests/types/build verts ; PostgreSQL V1–V23 et cinq HTTP verts. Revues Sol Standards/Spec sans écart. [PR #139](https://github.com/ibrahima-isi/meetstudent/pull/139) squashée dans dev à `502f489` après [CI finale API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38842602920) sur `6a5c1fc` ; issue #108 fermée, contenu du squash vérifié, worktree archivé. #109 suit.

### #109 — favoris étudiants masqués aux enseignants (2026-10-16)

- [x] [#109](https://github.com/ibrahima-isi/meetstudent/issues/109) : implémentation et validation terminées dans `fix/hide-teacher-wishlist-109` depuis le squash #108 `502f489`.

[Preuves](docs/evidence/ticket-109.md) : huit tests rouges puis verts sur service, frontière réseau, accueil, fiche établissement, profil et navigation. Web 957 tests/types/build SSR verts ; revues Sol Standards/Spec sans écart. [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38890633643) sur `71515c8` ; le présent closeout documentaire doit passer les trois CI sur son head final avant squash de la [PR #140](https://github.com/ibrahima-isi/meetstudent/pull/140). Arrêt après #109, avant #110.


### #110 — une note par utilisateur et établissement (2026-10-16)

- [x] [#110](https://github.com/ibrahima-isi/meetstudent/issues/110) : livré dans `fix/school-rating-uniqueness-110`, base `557adcd`. Qualification des garanties déjà présentes terminée : 14 tests HTTP H2 ciblés, 3 HTTP PostgreSQL, V23→V24 sur doublons historiques puis schéma validé, API complète 608 tests verts. Aucun code métier ni migration modifiés ; nouveaux tests verts sur la base existante, sans faux rouge. Stack jetable supprimée. Revues indépendantes Standards et Spec sur `24afa44` : zéro constat sur chaque axe. [PR #144](https://github.com/ibrahima-isi/meetstudent/pull/144) fusionnée dans `dev` ; trois CI finales vertes sur `d3b4f1388d610cfdbb64664cd64f624c69a7abe9` ([run](https://github.com/ibrahima-isi/meetstudent/actions/runs/38940528079)), squash signé vérifié `e18edf84e5a0f8df105febe6563681dcf43d1453`, arbre identique au head final ; ticket clôturé et worktree/branche locale #110 nettoyés. [Preuves](docs/evidence/ticket-110.md).

- [x] #110 — implémentation existante et qualification HTTP/PostgreSQL validées.
- [x] #110 — tests affectés et revues indépendantes Standards/Spec terminés, sans constat.
- [x] #110 — [PR #144](https://github.com/ibrahima-isi/meetstudent/pull/144) fusionnée ; trois CI vertes sur le head final `d3b4f13`, squash signé `e18edf8`, ticket clôturé et nettoyage ciblé terminé.

### #111 — une note par utilisateur et programme (2026-10-16)

- [x] [#111](https://github.com/ibrahima-isi/meetstudent/issues/111) : qualification et vérifications locales terminées dans `test/program-rating-uniqueness-111`, base immuable `e18edf84e5a0f8df105febe6563681dcf43d1453`. Manifeste de cinq fichiers autorisé ; qualification HTTP/agrégat/rôles/concurrence et migration PostgreSQL V23→V24 terminée. API complète 611 tests verts (239 unitaires +372 intégration), trois HTTP PostgreSQL verts, survivants déterministes et SQLSTATE23505 vérifiés, stack ms111 supprimée. Aucun code métier/migration modifié, aucun faux rouge. [Preuves](docs/evidence/ticket-111.md). Revues indépendantes sur `22668c6` : Standards zéro violation certaine (deux remarques facultatives conservées), Spec zéro constat ; 14 HTTP H2 relancés par le reviewer, verts. Aucun blocage ; [PR #145](https://github.com/ibrahima-isi/meetstudent/pull/145) ouverte vers `dev` ; livraison suivie séparément.

- [x] #111 — comportement existant et qualification HTTP/PostgreSQL vérifiés ; suite API entière verte.
- [x] #111 — revues Standards/Spec indépendantes terminées ; aucun correctif requis.
- [ ] #111 — [PR #145](https://github.com/ibrahima-isi/meetstudent/pull/145) vers `dev` ouverte ; trois CI sur le présent head documentaire final, squash et clôture encore requis. Le bilan définitif sera ajouté au suivi local après fusion.

### #125 — comparer des cours (2026-10-16)

- [x] [#125](https://github.com/ibrahima-isi/meetstudent/issues/125) : implémentation et validations locales terminées dans `feat/courses-comparison`, base `66ea034`. Sélection `?compare=` (max 3, 4e refusé, région `role="status"` permanente, barre Comparer 2–3, Vider) sur le catalogue des cours, puis page publique `/{lang}/courses/compare?ids=` (SSR, déclarée avant tout `courses/:id`) : lignes Cours, Code, Formation (null → « Non renseigné », sans lien) et Note moyenne (null → « Non noté », jamais 0), tri par note décroissante, sans note à la fin, puis nom, puis id ; 404 partiel avec lien vers la sélection restante, erreur avec réessai, états invalide et chargement. Aucune donnée inventée (ni niveau, ni description, ni établissement).

Preuves : rouge réel sur assertions pour la sélection (8 échecs sur 22 tests de `courses-page`, boîtes et barre absentes), pour la page de comparaison (spec contre une coquille vide : colonnes/lignes absentes, états invalide/chargement/erreur manquants) et pour la route serveur (`:lang/courses/compare` absente). Les tests de lecture `getCourse` et la garde mode sombre passent d'emblée (caractérisation d'un comportement déjà présent). Revue Standards/Spec indépendante : deux défauts du helper partagé `compare-selection` corrigés en red → green (2 rouges réels) — ids > 2^31-1 désormais ignorés (l'API répondait 400 et la page restait sur un réessai inutile), nom absent (colonne nullable) trié comme vide au lieu de faire planter le tri ; la correction vaut aussi pour établissements et formations. Résultats finaux réels : **1205** tests web verts (référence `origin/dev` `66ea034` : **1162**, aucune régression), `npx ng build` vert. Smoke navigateur réel (SSR local + fausse API Node, fr/en, bureau et mobile 375 px, clair/sombre) : sélection 1→3, 4e refusée et décochée avec message dans la région `role="status"`, page de comparaison rendue côté serveur (`ng-server-context="ssr"`), tri 4.3 > 3.5 (Algèbre/Analyse à égalité à 3.5, départagés par le nom) > Non noté, « Non renseigné » pour code/formation absents, 404 partiel avec retour `?compare=` restant, 500 avec réessai, 4 ids → état invalide, id 2147483648 ignoré, tableau défilant dans sa région sans défilement horizontal de page ; aucune erreur JS hors réponses HTTP volontaires. Livré : [PR #161](https://github.com/ibrahima-isi/meetstudent/pull/161) squashée dans `dev` (`764c0a2`), ticket clôturé ; #123 et #124 aussi (PR #159 et #160), donc le point 9 de #99 est coché.

### #126 — classement des établissements et top 10 (2026-10-17)

- [x] [#126](https://github.com/ibrahima-isi/meetstudent/issues/126) : implémentation et validations locales terminées dans `feat/schools-ranking`, base `764c0a2`. Page publique `/{lang}/rankings/schools` (dans la shell, SSR explicite avant `**`, titre `pageTitle.rankings`) qui appelle `SchoolService.getSchools(page, 10, 'most')` (aucun nouvel endpoint, aucun tri client) : dix premiers avec rang, compteur réel `totalElements` (pluriel fr/en), « Voir plus » = page serveur suivante (dédoublonnage par id, rang d'origine conservé), « Voir tout » = enchaîne les pages jusqu'à `last` avec état occupé et boutons inactifs, erreur de continuation conservant le déjà chargé avec réessai qui reprend à la page échouée, états chargement / vide / moins de dix (aucun bouton) / erreur de première page (`error-state` + réessai), « Non noté » jamais 0, nom lié à `/{lang}/schools/:id` comme la page de comparaison. Lien « Classement » / « Rankings » dans la barre desktop et le menu mobile, sur sa propre ligne. Moyenne brute, sans seuil d'avis.

Preuves : rouge réel sur assertions pour la page (spec contre une coquille vide : 13 échecs sur 13), pour la route (page absente du routeur → 404, route serveur absente : 2 échecs) et pour la barre (2 échecs : lien absent, liste des href du menu à 7 au lieu de 8). Le lien supplémentaire a fait déborder la barre à 1200 px (test de non-débordement existant rouge : « the bar is wider than the screen », fr connecté) : le seuil du menu complet passe de 68rem à 73rem (rem = 17 px dans cette appli), vérifié sans débordement à chaque pixel de 1100 à 1400 px, fr/en, anonyme/connecté, et le test « grand écran à 1280 px » reste vert. Côté API, aucun changement de code : le nouveau test `SchoolRankingPostgresIntegrationTests` (8 établissements, pages de 3, moyennes 5 / 4,0 ×4 dont une moyenne de 5 et 3 / 2 / deux sans note) qualifie le comportement existant (vert d'emblée) ; pour prouver qu'il détecte bien, deux mutations temporaires, annulées, ont été jouées sur PostgreSQL 18 réel (V1–V24, `ddl-auto=validate`) : retrait de `NULLS LAST` → rouge (« nothing rated follows an unrated school ») ; tri par défaut `id` seul au lieu de `name,id` → rouge (`[30, 27, 28, 29, 26, 31]` attendu, `[30, 26, 27, 28, 29, 31]` obtenu). Limite connue : H2 supprime `NULLS LAST`, donc « sans note à la fin » n'est assertée que sur PostgreSQL ; sur H2 le test vérifie l'ordre relatif, le total exact et la pagination. Conteneur jetable `ms126-pg` supprimé.

Résultats avant revue : **1226** tests web verts (référence `origin/dev` : **1205**, +21, aucune régression), `npx ng build` vert ; API `./mvnw clean verify` : **631** tests (239 unitaires + 392 intégration), zéro échec/erreur/skip, BUILD SUCCESS ; test de classement vert sur H2 puis deux fois de suite sur PostgreSQL (rejouable sur base persistante).

Revue Standards/Spec/sécurité indépendante : trois défauts d'accessibilité confirmés dans le navigateur puis corrigés en red → green (5 rouges réels) — après « Voir plus » le focus clavier tombait sur `<body>` (bouton désactivé pendant le chargement, puis retiré à la dernière page) : il va désormais au premier établissement ajouté, ou au bouton Réessayer si la suite échoue ; le rang et la moyenne étaient des `aria-label` sur des `<span>` génériques, ce qui masquait la valeur de la note dans l'arbre d'accessibilité (« Note moyenne » sans « 4.8 ») : texte masqué visuellement à la place ; le message de chargement de la suite est une région `role="status"` toujours présente, sans `aria-busy`. Aucun changement d'API. Résultats finaux réels : **1231** tests web verts (+5), `npx ng build` vert. Smoke navigateur réel (SSR local port 4610 + fausse API Node paginée port 4611, fr/en, 1280 et 1100 px et mobile 375 px, clair/sombre) : page rendue côté serveur (`ng-server-context="ssr"`, 10 lignes), 23 établissements, ordre note décroissante puis nom puis id (deux « Alpha École » à 4 départagés par l'id), trois « Non noté » en fin, nom `<script>` affiché comme texte, « Voir plus » au clavier → 20 lignes et focus sur le 11e, « Voir tout » → 23 lignes sans doublon et boutons retirés, échec de la page 3 pendant « Voir tout » → 20 lignes conservées, focus sur Réessayer, réessai → 23 ; erreur de première page avec réessai, vide, 4 résultats sans bouton ; navigation pendant « Voir tout » → requête annulée (`ERR_ABORTED`), aucune page suivante demandée ; lien Classement/Rankings dans la barre à 1280 px (fr/en, sans débordement) et dans le menu à 1100 et 375 px. [PR #164](https://github.com/ibrahima-isi/meetstudent/pull/164) ouverte vers `dev` ; reprise autorisée par le propriétaire le 2026-10-17 après garde anti-doublon. Requalification actuelle : web **1231** tests et build SSR, API **631** tests (239 unitaires + 392 intégration), un test de classement sur PostgreSQL **18.6**, V1–V24 et `ddl-auto=validate`, tous verts sur `86af9b0`. Navigateur avec API réelle et PostgreSQL : fr/en, SSR 10 résultats puis 20 puis 23, sans doublon, focus au 11e établissement, sans débordement à 1280/1100/375 px ; ressources dédiées supprimées. Après intégration concurrente de `dev`, web **1236** tests verts sur `b36019a`. Les rouges historiques restent des preuves consignées par la session initiale, sans journaux bruts retrouvés pendant cette reprise. Revues finales indépendantes sur `b36019a` : **Standards 0 / Spec 0**. Trois CI vertes sur ce head ([run 39021812061](https://github.com/ibrahima-isi/meetstudent/actions/runs/39021812061)) ; après le présent suivi et la mise à jour depuis `dev`, trois CI du head définitif restent requises avant squash. Fusion et clôture vérifiées séparément dans le bilan final de livraison. Le point 11 reste partiel : #127 n’est pas autorisé dans cette session.


Reprise #126 — CI après mise à jour de `dev` : deux tests du parcours footer dépendaient d’une session persistante. Reproduction réelle : 21 succès / 2 échecs avec `auth_token` préchargé ; avec une fixture explicitement anonyme, les mêmes 23 tests passent. La sonde temporaire a été retirée. Correction limitée au test, aucun comportement produit modifié. Les trois CI sont relancées sur le head final après intégration du dernier `dev`.

### #128 — navigation « Découvrir » / « Discover » (2026-10-17)

- [x] [#128](https://github.com/ibrahima-isi/meetstudent/issues/128) : implémentation et validations locales terminées dans `feat/discover-navigation`, base `c26bb99`. Dans la barre desktop, le lien « Comment ça marche » est remplacé par un bouton « Découvrir » / « Discover » (disclosure : `aria-expanded`, `aria-controls` posé seulement panneau ouvert, ni `role="menu"` ni piégeage du focus) qui ouvre un panneau : « Comment ça marche » / « How it works » (ancre `/{lang}#how-it-works`, fragment conservé), Formations / Programmes (`/{lang}/programs`), Cours / Courses (`/{lang}/courses`), Accréditations / Accreditations (`/{lang}/accreditations`). Clés dédiées `nav.discover`, `nav.discoverPrograms`, `nav.discoverCourses`, `nav.discoverAccreditations` (fr et en ensemble) ; `nav.howItWorks` et `lp.steps.title` inchangés. Fermeture : Échap (focus rendu au bouton si le focus était dans le panneau), clic extérieur, navigation, déconnexion ; ouvrir le menu mobile, le compte, les souhaits ou la langue le ferme, et inversement ; son état compte dans `menuOpen` de `update()` (la barre ne se cache pas ouverte). Panneau mobile : liens à plat « Découvrir » (ancre) puis les trois destinations, localisés. Le reste de la barre est inchangé.

Preuves : rouge réel avant le code, `npm test -- --include='**/dock-navbar.component.spec.ts'` : 16 échecs sur 86 (bouton absent, `tagName` d'un élément nul, liste des href du menu mobile à 8 au lieu de 11, libellés anglais absents, exclusion mutuelle). Vert après le code : 86/86. Puis suite complète : **1255** tests web verts (référence `origin/dev` : 1231+, aucune régression), `npx ng build` vert. Le test de non-débordement 320–1440 px (fr/en, anonyme/connecté) mesure désormais aussi le bouton ; il reste vert, la barre ne gagne qu'un bouton plus court que l'ancien lien, et le seuil de 73rem comme les marges ne sont pas modifiés. Un test vérifie que le panneau reste dans l'écran à 1280 px. Écart : le budget CSS du composant (avertissement, pas une erreur) passe de 4,04 kB à 5,09 kB. Non vérifié : rendu visuel en navigateur réel et CI Linux (polices plus larges) ; la touche Entrée/Espace repose sur le comportement natif du `<button>`. Le point 12 de #99 reste décoché jusqu'à la fusion.
