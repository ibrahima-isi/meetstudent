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
- [ ] 8. Four footer product destinations (#118–120, #122).
- [ ] 9. School/programme/course comparison (#123–125).
- [ ] 10. Search entry across three resource types (#121).
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

- [ ] [#110](https://github.com/ibrahima-isi/meetstudent/issues/110) : claimed dans `fix/school-rating-uniqueness-110`, base `557adcd`. Qualification des garanties déjà présentes terminée : 14 tests HTTP H2 ciblés, 3 HTTP PostgreSQL, V23→V24 sur doublons historiques puis schéma validé, API complète 608 tests verts. Aucun code métier ni migration modifiés ; nouveaux tests verts sur la base existante, sans faux rouge. Stack jetable supprimée. Revues indépendantes Standards et Spec sur `24afa44` : zéro constat sur chaque axe. Livraison PR/CI finale/squash en attente. [Preuves](evidence/ticket-110.md).

- [x] #110 — implémentation existante et qualification HTTP/PostgreSQL validées.
- [x] #110 — tests affectés et revues indépendantes Standards/Spec terminés, sans constat.
- [ ] #110 — PR vers `dev`, trois CI vertes sur le head documentaire final, squash et clôture à relier par l’orchestrateur.
