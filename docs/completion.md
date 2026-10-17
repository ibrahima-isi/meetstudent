# Implementation completion

## Hide missing programme duration

- [x] Hide the whole duration row for missing, null, zero or negative duration; preserve positive-duration translations.
- [x] Align Program.duration typing with the nullable, optional API value.
- [x] Verify failing regression first, then full ChromeHeadless tests and Angular build.
- [x] Open [PR #97](https://github.com/ibrahima-isi/meetstudent/pull/97) against dev. CI and merge status are tracked on the PR.

Verification: before the fix, all four regression cases failed (43 existing specs passed). After the fix, `npm test -- --no-watch --browsers=ChromeHeadless` passed all 924 tests and `npx ng build` succeeded. Existing French/English singular and plural checks passed.

Handoff: implementation and local verification are complete. Check PR #97 for delivery status before continuing with the next ticket. The working branch is `fix/hide-missing-programme-duration`; squash merge is gated by green CI, followed by local branch deletion.


## Spec #99 — persistent workstream checklist

Track individual deliveries in [../completion.md](../completion.md). Preserve this workstream list until all its child tickets pass their own verification; implementing #100 alone does not complete point 1.

- [ ] 1. Credential/session security (#100–104).
- [x] 2. Reliable API mutations and backoffice behavior (#105–106, deletion in #104).
- [ ] 3. Student/teacher registration, wishlist and unique ratings (#107–112).
- [ ] 4. Engineering-skills worktree cleanup (#113).
- [ ] 5. Safe auto-login worktree cleanup (#114).
- [ ] 6. Hide authenticated landing signup band (#115).
- [ ] 7. Footer account buttons (#117).
- [x] 8. Four footer product destinations (#118–120, #122).
- [x] 9. School/programme/course comparison (#123–125).
- [x] 10. Search entry across three resource types (#121).
- [ ] 11. School/programme rankings (#126–127).
- [ ] 12. Découvrir / Discover navigation (#128).
- [ ] 13. Exactly one About footer (#116).
- [ ] 14. Exactly one Contact footer (#116).

### #100 — password change and session revocation (2026-10-13)

Implementation: current password required on self-service PUT/PATCH, administrator exemption only for another account; monotone persisted credential version; access/refresh revocation including the caller; reset/email-change links consumed; historical version-zero compatibility; serialized login/refresh; translated profile errors and re-login notice. ADR: [0001-credential-session-version.md](adr/0001-credential-session-version.md).

Tests use the approved HTTP and rendered Angular form boundaries. The two race cases use actual committed requests and a JDBC read barrier, not timing-only concurrent starts. Removing the login version check causes the regression test to fail; restoring it passes.

Local verification complete. Detailed commands and regression evidence: [ticket-100.md](evidence/ticket-100.md).

Evidence:

| Check | Before | After |
| --- | --- | --- |
| Missing current password | HTTP 200 instead of 400 | 400; profile/session preserved |
| Old access token after password change | HTTP 200 instead of 401 | 401; old refresh rejected; new login works |
| Pending email-change link | HTTP 204 instead of 400 | 400, as for old reset links |
| Form current-password requirement | Unexpected PATCH | No request; inline validation |
| Successful form change | Session retained | Session cleared; localized login navigation |
| Login notice / accessible field error | Missing notice / missing associated error | Both translated notice and associated error rendered |
| Angular full suite | — | 927 tests passed; production SSR build passed |
| PostgreSQL migration | V19 with legacy user/refresh rows | V20 applied; both rows retain version zero |

Final local checks: API 226 unit + 273 integration tests passed; web 927 and backoffice 271 tests passed; both production builds and TypeScript checking passed. PostgreSQL: V19 → V20 plus 15 HTTP/race tests passed with schema validation. Standards/Spec review completed; the email-link race finding was reproduced and corrected. Disposable PostgreSQL removed.

- [x] [#100 — Changer son mot de passe et révoquer les anciennes sessions](https://github.com/ibrahima-isi/meetstudent/issues/100): implementation and required verification complete; [PR #131](https://github.com/ibrahima-isi/meetstudent/pull/131) targets dev; CI/merge status tracked there.

Delivery: [PR #131](https://github.com/ibrahima-isi/meetstudent/pull/131). Local verification and review are complete. [API/web/backoffice CI passed](https://github.com/ibrahima-isi/meetstudent/actions/runs/38413822495) on `ccb655a`. Squash merge status is authoritative on the PR; the documentation-only closeout commit is gated by a fresh green CI before merge. No main promotion or unrelated worktree cleanup is part of this ticket (#129 owns the final promotion). Existing source branches/worktrees and their uncommitted files are preserved.

### #101 — récupération de tous les rôles (2026-10-14)

- [x] [#101](https://github.com/ibrahima-isi/meetstudent/issues/101) : implémentation et vérification terminées ; [PR #132](https://github.com/ibrahima-isi/meetstudent/pull/132) vers dev.

Reset étudiant/expert/admin : version des identifiants incrémentée atomiquement, anciennes sessions access/refresh et liens reset/email-change invalidés. Bootstrap admin : initialisation sûre, mot de passe existant conservé au redémarrage, neutralisation du seed avec révocation ; aucune promotion d’un compte étudiant/expert. Backoffice : accès au parcours web français par origine configurable ; parcours fr/en conservés.

Preuves : [ticket-101.md](evidence/ticket-101.md). API : 226 unitaires + 286 intégrations ; PostgreSQL : 20 migrations et 38 tests HTTP/courses ; web : 927 tests et build SSR ; backoffice : 275 tests et build. Redémarrage complet après reset admin et navigateur desktop/mobile vérifiés. Revues Standards/Spec terminées ; contradiction documentaire corrigée. Ressources jetables arrêtées/supprimées.

Livraison : [PR #132](https://github.com/ibrahima-isi/meetstudent/pull/132). [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38525554851) sur `b408ac0` : API 2m58s, web 1m3s, backoffice 34s. Le commit de closeout documentaire est soumis à une nouvelle CI avant squash. Le statut définitif de fusion est celui de la PR ; le bilan de squash/nettoyage est conservé dans la copie locale exportée après livraison. Les tickets #102–#129 restent décochés ; cette session s’arrête après #101.

### #102 — confirmation du nouvel email (2026-10-14)

- [x] [#102](https://github.com/ibrahima-isi/meetstudent/issues/102) : implémentation et vérification terminées, base isolée `b56e384`.

Confirmation atomique : nouvelle adresse vérifiée, version des identifiants incrémentée, anciens access/refresh refusés. Demande avec mot de passe actuel, limites et liens à usage unique conservés. Web : cache/session effacés après succès, synchronisation inter-onglets, reconnexion fr/en ; vérification email ordinaire inchangée.

Preuves : [ticket-102.md](evidence/ticket-102.md). API : 226 unitaires + 294 intégrations ; PostgreSQL : 20 migrations et 39 HTTP/courses ; web : 930 tests, build SSR et types ; backoffice : 275 tests et build. Revues Sol Standards/Spec : défaut inter-onglets reproduit et corrigé, aucun écart restant. Ressources PostgreSQL supprimées.

Livraison : [PR #133](https://github.com/ibrahima-isi/meetstudent/pull/133) vers dev ; [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38528795149) sur `48256a3`. Le closeout documentaire doit aussi passer les trois CI avant squash. Le statut définitif de fusion reste celui de la PR. Le bilan définitif de fusion/nettoyage sera conservé dans la copie locale exportée. Arrêt après #102 ; #103 non commencé.

### #103 — sessions par appareil (2026-10-14)

- [x] [#103](https://github.com/ibrahima-isi/meetstudent/issues/103) : implémentation et vérifications locales requises terminées. Base isolée `40802dd`, branche `feat/session-inactivity-103` ; PR/CI/fusion suivies séparément.

Sessions UUID indépendantes, rotation stable, inactivité serveur de sept jours au seuil exact, refresh/passivité exclus, activité après interaction explicite ou écriture réussie, révocation globale et grâce historique V21. Choix d’activité confirmé par le propriétaire le 2026-10-14. Web/backoffice : purge et invitation à se reconnecter, sans boucle ni accès SSR au stockage.

Preuves : [ticket-103.md](evidence/ticket-103.md). API finale : 226 unitaires + 302 intégrations ; PostgreSQL V20→V21 et 61 HTTP/courses verts sur code final, plus anciens credentials via HTTP réel sur schéma migré ; web 940 et backoffice 286 tests, types et builds verts. Revues Sol finales : Standards 0 / Spec 0 (hypothèse OSIV retirée après test et vérification de l’ordre réel des contextes).

Livraison : [PR #134](https://github.com/ibrahima-isi/meetstudent/pull/134) vers dev ; [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38570741641) sur `b74a6f63af2e2dc00db1eba1d33cbeef171dcdd5` (API 2m27s, web 1m11s, backoffice 34s). Le closeout documentaire passe aussi les trois CI avant squash ; le statut définitif de fusion est celui de la PR, et le bilan final de fusion/nettoyage est conservé dans la copie locale exportée. API/PostgreSQL jetables arrêtés/supprimés. Arrêt après #103 ; #104 non commencé.

### #104 — suppression de compte (2026-10-14)

- [x] [#104](https://github.com/ibrahima-isi/meetstudent/issues/104) : implémentation et vérifications locales requises terminées. Base `e84ce2b`, branche `feat/account-deletion-104` ; livraison PR/CI/fusion suivie séparément.

Mot de passe actuel requis pour soi (administrateur inclus), exemption admin uniquement sur autrui, 204 vide et erreurs 400/401/403/404. Sessions et trois types de notes nettoyés sous verrou du compte ; documents/wishlist supprimés, autres comptes/notes/catalogue préservés. Jetons d'action orphelins inutilisables, budget d'adresse et rétention existants conservés. Aucun nouveau parcours client.

Preuves : [ticket-104.md](evidence/ticket-104.md). API complète **226 unitaires + 308 intégrations**, PostgreSQL **35 HTTP/courses** avec V1–V21 et schéma validé, web **940** et backoffice **286** tests, types et builds verts. Revues Sol : Standards **0 violation ferme**, une répétition JPA facultative conservée ; Spec **0 écart**. PostgreSQL jetable nettoyé et inventaire vide vérifié. Livraison : [PR #135](https://github.com/ibrahima-isi/meetstudent/pull/135) vers dev ; [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38584026934) sur `8b8a202f26891279d69f99de06c1c328f708f0ee` (API 3m33s, web 1m05s, backoffice 33s). Le présent closeout documentaire doit à son tour passer les trois CI sur son head final avant squash ; statut définitif sur la PR, bilan de fusion/nettoyage conservé dans la copie locale exportée. Arrêt après #104, sans commencer #105.

### #105 — erreurs API de mutations et conflits (2026-10-15)

- [x] [#105](https://github.com/ibrahima-isi/meetstudent/issues/105) : implémentation et validation locales terminées, base isolée `ab5d08b`, branche `fix/api-mutation-errors-105`.

Contrats HTTP 400/404/409 des tags, accréditations, programmes/cours et rôles, validation des références, conservation des relations à supprimer en cas de conflit, backoffice adapté aux 409 et migration V22 de l’identité des rôles. [Preuves locales](evidence/ticket-105.md) : API 226 unitaires + 325 intégrations ; PostgreSQL V1–V22 et deux HTTP ; backoffice 289 tests/types/build, web 940 tests/types/build. Revues Sol Standards/Spec finales : 0 écart. [PR #136](https://github.com/ibrahima-isi/meetstudent/pull/136) squashée dans dev à `93635e0` après [CI finale API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38752013959) sur `c5b86a4` ; issue #105 fermée, PostgreSQL jetable nettoyé et worktree archivé. #106 reste distinct.

### #106 — désassociation d’accréditation (2026-10-15)

- [x] [#106](https://github.com/ibrahima-isi/meetstudent/issues/106) : implémentation et validation terminées dans la branche isolée `fix/accreditation-unlink-106` depuis `93635e0` ; lien programme corrigé et lien direct cours/accréditation ajouté sur choix explicite du propriétaire.

[Preuves](evidence/ticket-106.md) : HTTP et UI rouges → verts ; API complète 558 tests, backoffice 300 tests/types/build, web 940 tests/types/build SSR verts ; PostgreSQL V1–V23 et HTTP de partage cours/programme verts avec schéma validé. Revues Sol Standards/Spec sans écart. [PR #137](https://github.com/ibrahima-isi/meetstudent/pull/137) squashée dans dev à `4ed101e` après [CI finale API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38792763175) sur `a4006f1` ; issue #106 fermée, contenu du squash vérifié, worktree archivé. #107 suit.

### #107 — choix étudiant ou enseignant à l’inscription (2026-10-15)

- [x] [#107](https://github.com/ibrahima-isi/meetstudent/issues/107) : implémentation et validation terminées dans `feat/registration-role-107` depuis le squash #106 `4ed101e`.

[Preuves](evidence/ticket-107.md) : tests HTTP et formulaire rouges puis verts pour `ROLE_EXPERT`, défaut étudiant, rejet des rôles interdits, erreur de champ et auto-connexion enseignant en anglais. API 561 tests, web 943 tests/types/build SSR, backoffice 300 tests/types/build verts ; PostgreSQL V1–V23 et trois tests HTTP verts. Revues Sol Standards/Spec sans écart. [PR #138](https://github.com/ibrahima-isi/meetstudent/pull/138) squashée dans dev à `745ada7` après [CI finale API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38797021310) sur `7e23cfa` ; issue #107 fermée, contenu du squash vérifié, worktree archivé. #108 suit.

### #108 — spécialité enseignant et erreurs de champ (2026-10-15)

- [x] [#108](https://github.com/ibrahima-isi/meetstudent/issues/108) : implémentation et validation terminées dans `feat/teacher-specialty-108` depuis le squash #107 `745ada7`.

[Preuves](evidence/ticket-108.md) : tests HTTP et formulaire rouges puis verts pour spécialité absente/blanche, 100/101 caractères, affichage local/API et changement enseignant → étudiant. API 564 tests, web 948 tests/types/build SSR, backoffice 300 tests/types/build verts ; PostgreSQL V1–V23 et cinq HTTP verts. Revues Sol Standards/Spec sans écart. [PR #139](https://github.com/ibrahima-isi/meetstudent/pull/139) squashée dans dev à `502f489` après [CI finale API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38842602920) sur `6a5c1fc` ; issue #108 fermée, contenu du squash vérifié, worktree archivé. #109 suit.

### #109 — favoris étudiants masqués aux enseignants (2026-10-16)

- [x] [#109](https://github.com/ibrahima-isi/meetstudent/issues/109) : implémentation et validation terminées dans `fix/hide-teacher-wishlist-109` depuis le squash #108 `502f489`.

[Preuves](evidence/ticket-109.md) : huit tests rouges puis verts sur service, frontière réseau, accueil, fiche établissement, profil et navigation. Web 957 tests/types/build SSR verts ; revues Sol Standards/Spec sans écart. [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38890633643) sur `71515c8` ; le présent closeout documentaire doit passer les trois CI sur son head final avant squash de la [PR #140](https://github.com/ibrahima-isi/meetstudent/pull/140). Arrêt après #109, avant #110.


### #110 — une note par utilisateur et établissement (2026-10-16)

- [x] [#110](https://github.com/ibrahima-isi/meetstudent/issues/110) : livré dans `fix/school-rating-uniqueness-110`, base `557adcd`. Qualification des garanties déjà présentes terminée : 14 tests HTTP H2 ciblés, 3 HTTP PostgreSQL, V23→V24 sur doublons historiques puis schéma validé, API complète 608 tests verts. Aucun code métier ni migration modifiés ; nouveaux tests verts sur la base existante, sans faux rouge. Stack jetable supprimée. Revues indépendantes Standards et Spec sur `24afa44` : zéro constat sur chaque axe. [PR #144](https://github.com/ibrahima-isi/meetstudent/pull/144) fusionnée dans `dev` ; trois CI finales vertes sur `d3b4f1388d610cfdbb64664cd64f624c69a7abe9` ([run](https://github.com/ibrahima-isi/meetstudent/actions/runs/38940528079)), squash signé vérifié `e18edf84e5a0f8df105febe6563681dcf43d1453`, arbre identique au head final ; ticket clôturé et worktree/branche locale #110 nettoyés. [Preuves](evidence/ticket-110.md).

- [x] #110 — implémentation existante et qualification HTTP/PostgreSQL validées.
- [x] #110 — tests affectés et revues indépendantes Standards/Spec terminés, sans constat.
- [x] #110 — [PR #144](https://github.com/ibrahima-isi/meetstudent/pull/144) fusionnée ; trois CI vertes sur le head final `d3b4f13`, squash signé `e18edf8`, ticket clôturé et nettoyage ciblé terminé.

### #111 — une note par utilisateur et programme (2026-10-16)

- [x] [#111](https://github.com/ibrahima-isi/meetstudent/issues/111) : qualification et vérifications locales terminées dans `test/program-rating-uniqueness-111`, base immuable `e18edf84e5a0f8df105febe6563681dcf43d1453`. Manifeste de cinq fichiers autorisé ; qualification HTTP/agrégat/rôles/concurrence et migration PostgreSQL V23→V24 terminée. API complète 611 tests verts (239 unitaires +372 intégration), trois HTTP PostgreSQL verts, survivants déterministes et SQLSTATE23505 vérifiés, stack ms111 supprimée. Aucun code métier/migration modifié, aucun faux rouge. [Preuves](evidence/ticket-111.md). Revues indépendantes sur `22668c6` : Standards zéro violation certaine (deux remarques facultatives conservées), Spec zéro constat ; 14 HTTP H2 relancés par le reviewer, verts. Aucun blocage ; [PR #145](https://github.com/ibrahima-isi/meetstudent/pull/145) ouverte vers `dev` ; livraison suivie séparément.

- [x] #111 — comportement existant et qualification HTTP/PostgreSQL vérifiés ; suite API entière verte.
- [x] #111 — revues Standards/Spec indépendantes terminées ; aucun correctif requis.
- [ ] #111 — [PR #145](https://github.com/ibrahima-isi/meetstudent/pull/145) vers `dev` ouverte ; trois CI sur le présent head documentaire final, squash et clôture encore requis. Le bilan définitif sera ajouté au suivi local après fusion.

### #112 — une note par utilisateur et cours (2026-10-16)

- [x] [#112](https://github.com/ibrahima-isi/meetstudent/issues/112) : qualification et vérifications locales terminées dans `test/course-rating-uniqueness-112`, base immuable `66f2ca52817054c63de31f9195f26b5a3438d12a`. Manifeste de quatre fichiers puis `CourseRateDTO.java` autorisés ; qualification HTTP (renote, agrégat, rôles) et concurrence 201/409, migration PostgreSQL V23→V24 (survivants 700001/700005/700007/700008/700009, SQLSTATE 23505, Hibernate validate). API complète 615 tests verts (239 unitaires + 376 intégration), trois HTTP PostgreSQL verts, conteneur ms112-pg supprimé. Aucune migration modifiée ; qualification sans faux rouge. Trou `courseId` nul : rouge réel (201 au lieu de 400) puis fermé par `@NotNull` sur `CourseRateDTO`. [Preuves](evidence/ticket-112.md). Suivi hors périmètre : `ProgramRateDTO.programId` sans `@NotNull` (non sondé) ; l'école est déjà couverte. Revues indépendantes sur `3d4af30` : Standards deux violations confirmées (ligne #111 réécrite par erreur, lien PR factice) et Spec deux incohérences de preuve (13 au lieu de 14 tests, manifeste de quatre fichiers) ; corrigées, test `courseId` complété (statut du GET final) et rouge rejoué (201 au lieu de 400). [PR #146](https://github.com/ibrahima-isi/meetstudent/pull/146) vers `dev` ouverte.

- [x] #112 — comportement existant et qualification HTTP/PostgreSQL vérifiés ; suite API entière verte.
- [x] #112 — revues Standards/Spec indépendantes terminées ; constats confirmés corrigés, API complète relancée verte (615 tests).
- [ ] #112 — PR vers `dev`, trois CI, squash et clôture encore requis ; suivi programme à décider.


### #121 — entrée Cherchez vers les trois catalogues (2026-10-16)

- [x] [#121](https://github.com/ibrahima-isi/meetstudent/issues/121) : implémentation, validations locales et première CI complète terminées ; état définitif de livraison dans la [PR #157](https://github.com/ibrahima-isi/meetstudent/pull/157) vers `dev`.

Base isolée `d73428d`, actualisée sur `b0dbba8`, branche `feat/search-entry-121`. Formulaire accessible : type établissement/formation/cours, nom, bouton Cherchez/Search, langue et `q` conservés. Les catalogues et leurs états HTTP existants sont réutilisés. Les termes incompatibles avec leurs contrats sont signalés avant navigation ; saisie/type/focus et associations ARIA conservés, erreur effacée après correction.

[Preuves](evidence/ticket-121.md) : deux rouges réels (formulaire absent puis faux vide sur terme non transportable), **29** tests ciblés, **1070** tests web, types et build SSR verts ; API **630** tests, backoffice **300** tests et build verts. PostgreSQL 18 : V1–V24 réussies et schéma validé ; navigateur SSR production : **24** parcours réels fr/en/mobile/desktop/clair/sombre, trois vides, trois erreurs/réessais, session authentifiée/paysage/rechargement, puis **12** validations/récupérations après correctif. Revues indépendantes finales : **Standards 0 / Spec 0** sur `83af31f` après correction du P2. Ressources smoke propres à #121 supprimées ; stack partagée préservée. [Première CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38973287376) sur `83af31f4778201dd336523caf03aef50dfc52085`. Le head documentaire final doit aussi obtenir les trois checks verts avant squash ; état définitif de CI/fusion sur la PR, clôture et archivage dans le bilan exporté après fusion.

### #122 — quatre liens Produit du footer (2026-10-16)

- [x] [#122](https://github.com/ibrahima-isi/meetstudent/issues/122) : implémentation et validations terminées dans `feat/footer-product-122`, base isolée `7b90310` ; quatre destinations réelles fr/en, cibles tactiles 44 × 44 et focus clavier explicite. [Preuves](evidence/ticket-122.md) : trois rouges réels, **33** tests ciblés et **1075** web, types app/spec et build SSR verts ; **96** parcours Produit et **48** parcours compte réels (Accueil/About/Contact, fr/en, bureau/mobile, clair/sombre), PostgreSQL 18 V1–V24 et JPA validate, aucune erreur JS. Revues indépendantes **Standards 0 / Spec 0** ; ressources smoke propres nettoyées, partagé préservé. [PR #158](https://github.com/ibrahima-isi/meetstudent/pull/158) vers dev ; trois CI du head documentaire définitif requises avant squash. Livraison/clôture/nettoyage définitifs sur la PR et dans le bilan exporté après fusion. Aucun blocage connu ; prochaine action : CI finale, squash, clôture et archivage ciblé, puis nouvelle session #123.

### #125 — comparer des cours (2026-10-16)

- [x] [#125](https://github.com/ibrahima-isi/meetstudent/issues/125) : implémentation et validations locales terminées dans `feat/courses-comparison`, base `66ea034`. Sélection `?compare=` (max 3, 4e refusé, région `role="status"` permanente, barre Comparer 2–3, Vider) sur le catalogue des cours, puis page publique `/{lang}/courses/compare?ids=` (SSR, déclarée avant tout `courses/:id`) : lignes Cours, Code, Formation (null → « Non renseigné », sans lien) et Note moyenne (null → « Non noté », jamais 0), tri par note décroissante, sans note à la fin, puis nom, puis id ; 404 partiel avec lien vers la sélection restante, erreur avec réessai, états invalide et chargement. Aucune donnée inventée (ni niveau, ni description, ni établissement).

Preuves : rouge réel sur assertions pour la sélection (8 échecs sur 22 tests de `courses-page`, boîtes et barre absentes), pour la page de comparaison (spec contre une coquille vide : colonnes/lignes absentes, états invalide/chargement/erreur manquants) et pour la route serveur (`:lang/courses/compare` absente). Les tests de lecture `getCourse` et la garde mode sombre passent d'emblée (caractérisation d'un comportement déjà présent). Revue Standards/Spec indépendante : deux défauts du helper partagé `compare-selection` corrigés en red → green (2 rouges réels) — ids > 2^31-1 désormais ignorés (l'API répondait 400 et la page restait sur un réessai inutile), nom absent (colonne nullable) trié comme vide au lieu de faire planter le tri ; la correction vaut aussi pour établissements et formations. Résultats finaux réels : **1205** tests web verts (référence `origin/dev` `66ea034` : **1162**, aucune régression), `npx ng build` vert. Smoke navigateur réel (SSR local + fausse API Node, fr/en, bureau et mobile 375 px, clair/sombre) : sélection 1→3, 4e refusée et décochée avec message dans la région `role="status"`, page de comparaison rendue côté serveur (`ng-server-context="ssr"`), tri 4.3 > 3.5 (Algèbre/Analyse à égalité à 3.5, départagés par le nom) > Non noté, « Non renseigné » pour code/formation absents, 404 partiel avec retour `?compare=` restant, 500 avec réessai, 4 ids → état invalide, id 2147483648 ignoré, tableau défilant dans sa région sans défilement horizontal de page ; aucune erreur JS hors réponses HTTP volontaires. Livré : [PR #161](https://github.com/ibrahima-isi/meetstudent/pull/161) squashée dans `dev` (`764c0a2`), ticket clôturé ; #123 et #124 aussi (PR #159 et #160), donc le point 9 de #99 est coché.

### #126 — classement des établissements et top 10 (2026-10-17)

- [x] [#126](https://github.com/ibrahima-isi/meetstudent/issues/126) : implémentation et validations locales terminées dans `feat/schools-ranking`, base `764c0a2`. Page publique `/{lang}/rankings/schools` (dans la shell, SSR explicite avant `**`, titre `pageTitle.rankings`) qui appelle `SchoolService.getSchools(page, 10, 'most')` (aucun nouvel endpoint, aucun tri client) : dix premiers avec rang, compteur réel `totalElements` (pluriel fr/en), « Voir plus » = page serveur suivante (dédoublonnage par id, rang d'origine conservé), « Voir tout » = enchaîne les pages jusqu'à `last` avec état occupé et boutons inactifs, erreur de continuation conservant le déjà chargé avec réessai qui reprend à la page échouée, états chargement / vide / moins de dix (aucun bouton) / erreur de première page (`error-state` + réessai), « Non noté » jamais 0, nom lié à `/{lang}/schools/:id` comme la page de comparaison. Lien « Classement » / « Rankings » dans la barre desktop et le menu mobile, sur sa propre ligne. Moyenne brute, sans seuil d'avis.

Preuves : rouge réel sur assertions pour la page (spec contre une coquille vide : 13 échecs sur 13), pour la route (page absente du routeur → 404, route serveur absente : 2 échecs) et pour la barre (2 échecs : lien absent, liste des href du menu à 7 au lieu de 8). Le lien supplémentaire a fait déborder la barre à 1200 px (test de non-débordement existant rouge : « the bar is wider than the screen », fr connecté) : le seuil du menu complet passe de 68rem à 73rem (rem = 17 px dans cette appli), vérifié sans débordement à chaque pixel de 1100 à 1400 px, fr/en, anonyme/connecté, et le test « grand écran à 1280 px » reste vert. Côté API, aucun changement de code : le nouveau test `SchoolRankingPostgresIntegrationTests` (8 établissements, pages de 3, moyennes 5 / 4,0 ×4 dont une moyenne de 5 et 3 / 2 / deux sans note) qualifie le comportement existant (vert d'emblée) ; pour prouver qu'il détecte bien, deux mutations temporaires, annulées, ont été jouées sur PostgreSQL 18 réel (V1–V24, `ddl-auto=validate`) : retrait de `NULLS LAST` → rouge (« nothing rated follows an unrated school ») ; tri par défaut `id` seul au lieu de `name,id` → rouge (`[30, 27, 28, 29, 26, 31]` attendu, `[30, 26, 27, 28, 29, 31]` obtenu). Limite connue : H2 supprime `NULLS LAST`, donc « sans note à la fin » n'est assertée que sur PostgreSQL ; sur H2 le test vérifie l'ordre relatif, le total exact et la pagination. Conteneur jetable `ms126-pg` supprimé.

Résultats avant revue : **1226** tests web verts (référence `origin/dev` : **1205**, +21, aucune régression), `npx ng build` vert ; API `./mvnw clean verify` : **631** tests (239 unitaires + 392 intégration), zéro échec/erreur/skip, BUILD SUCCESS ; test de classement vert sur H2 puis deux fois de suite sur PostgreSQL (rejouable sur base persistante).

Revue Standards/Spec/sécurité indépendante : trois défauts d'accessibilité confirmés dans le navigateur puis corrigés en red → green (5 rouges réels) — après « Voir plus » le focus clavier tombait sur `<body>` (bouton désactivé pendant le chargement, puis retiré à la dernière page) : il va désormais au premier établissement ajouté, ou au bouton Réessayer si la suite échoue ; le rang et la moyenne étaient des `aria-label` sur des `<span>` génériques, ce qui masquait la valeur de la note dans l'arbre d'accessibilité (« Note moyenne » sans « 4.8 ») : texte masqué visuellement à la place ; le message de chargement de la suite est une région `role="status"` toujours présente, sans `aria-busy`. Aucun changement d'API. Résultats finaux réels : **1231** tests web verts (+5), `npx ng build` vert. Smoke navigateur réel (SSR local port 4610 + fausse API Node paginée port 4611, fr/en, 1280 et 1100 px et mobile 375 px, clair/sombre) : page rendue côté serveur (`ng-server-context="ssr"`, 10 lignes), 23 établissements, ordre note décroissante puis nom puis id (deux « Alpha École » à 4 départagés par l'id), trois « Non noté » en fin, nom `<script>` affiché comme texte, « Voir plus » au clavier → 20 lignes et focus sur le 11e, « Voir tout » → 23 lignes sans doublon et boutons retirés, échec de la page 3 pendant « Voir tout » → 20 lignes conservées, focus sur Réessayer, réessai → 23 ; erreur de première page avec réessai, vide, 4 résultats sans bouton ; navigation pendant « Voir tout » → requête annulée (`ERR_ABORTED`), aucune page suivante demandée ; lien Classement/Rankings dans la barre à 1280 px (fr/en, sans débordement) et dans le menu à 1100 et 375 px. Pas de CI à ce stade, pas de push ni de PR ; le point 11 de #99 reste décoché jusqu'à la fusion de la PR.

## ui-auth-aware-footer-bo-design

Spec : [.scratch/ui-auth-aware-footer-bo-design/spec.md](../.scratch/ui-auth-aware-footer-bo-design/spec.md).

- [x] [ui-auth-aware-footer-bo-design/01](../.scratch/ui-auth-aware-footer-bo-design/issues/01-footer-web-auth-aware.md) : footer web sensible à l'auth. Implémenté sur `feat/footer-account-auth-aware` : 5 specs ajoutées, rouges d'abord (4 échecs) puis `npm test` ChromeHeadless 1210/1210 verts et `ng build` OK. Le parcours Playwright n'a pas été exécuté. PR vers `dev` ouverte ; fusion suivie sur la PR.
- [ ] [ui-auth-aware-footer-bo-design/02](../.scratch/ui-auth-aware-footer-bo-design/issues/02-bo-tokens-theme.md) : back office, jetons et thème alignés sur le web. État initial : ready-for-agent.
- [ ] [ui-auth-aware-footer-bo-design/03](../.scratch/ui-auth-aware-footer-bo-design/issues/03-bo-navbar-footer-forum.md) : back office, navbar, footer et entrée Forum. Bloqué par 02. État initial : ready-for-agent.
- [ ] [ui-auth-aware-footer-bo-design/04](../.scratch/ui-auth-aware-footer-bo-design/issues/04-web-forum-coming-soon.md) : web, entrée Forum « Bientôt ». État initial : ready-for-agent.
