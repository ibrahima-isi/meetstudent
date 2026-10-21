# 01: API des fils et des réponses

**What to build:** Via l'API, tout le monde lit les fils ; étudiants et experts approuvés créent des fils et répondent ; l'auteur modifie et supprime ses messages.

**Blocked by:** [expert-approval/01](../../expert-approval/issues/01-status-and-rating-guard.md)

**Status:** resolved

**Worktree:** `forum-01`

- [x] Migration : fils (titre ≤150, message ≤5000, établissement facultatif, verrouillé), réponses, masqué ; auteur supprimé → null affiché « Utilisateur supprimé »
- [x] Lecture publique paginée (≤100, récents d'abord), filtre par établissement ; DTO sans e-mail ni identifiant interne
- [x] Écriture : STUDENT ou expert approuvé ; expert en attente/rejeté 403 ; anonyme 401 ; fil verrouillé refuse les réponses
- [x] Modifier/supprimer : auteur seulement (autrui 403)
- [x] Texte brut stocké et renvoyé tel quel (une charge `<script>` n'est pas interprétée) ; contrôles de caractères invisibles refusés
- [x] Limite de 5 messages par minute et par utilisateur : 429 + `Retry-After`
- [x] Tests d'intégration des contrôleurs, rouge d'abord ; glossaire : Forum, Fil, Réponse

## Résultat

Branche `feat/forum-api`, migration V33. Preuves réelles :
- `./mvnw -o clean verify` : 385 tests unitaires + 567 d'intégration, 0 échec, 0 ignoré avec une base jetable (2 sinon) (avec `MIGRATION_PG_*` pointant une base jetable ; sans elle les 2 tests de migration Postgres sont ignorés, comme en CI).
- Rouge d'abord : 41 des 45 tests d'intégration forum en échec avant le code ; après les revues, 5 cas de `SafeTextTest` (remplissages invisibles), 10 des 12 tests de prénom et 1 test de budget des modifications vus rouges avant correction.
- V33 rejouée sur PostgreSQL 18 jetable (conteneur supprimé) : limites 150/5000, `ON DELETE SET NULL` auteur et établissement, cascade des réponses, index `(school_id, created_at DESC, id DESC)`. Application démarrée en `ddl-auto: validate` (première version de V33), GET public 200 et POST anonyme 401 en HTTP réel.

Décisions :
- Endpoints `/api/v1/forum` : `GET|POST /threads`, `GET|PUT|DELETE /threads/{id}`, `GET|POST /threads/{id}/replies`, `PUT|DELETE /replies/{id}`. Lecture publique ; écriture : étudiant ou expert approuvé (anonyme 401, expert en attente/rejeté et admin 403).
- Ordre fixe récent d'abord, `?sort=` ignoré, taille <= 100, `?schoolId=` inconnu = page vide, `schoolId` inconnu à la création = 400.
- Fil verrouillé : réponse refusée en 409 ; modifier/supprimer ses propres messages reste permis.
- Message ou fil masqué : 404 pour les non-admins (y compris l'auteur) ; l'admin le voit avec `hidden: true`.
- Autrui ou admin qui modifie/supprime : 403. Supprimer un fil supprime ses réponses.
- Limite : 5 écritures par minute et par utilisateur (créations et modifications ensemble, après validation ; les suppressions ne consomment rien) : 429 + `Retry-After`.
- Texte brut stocké tel quel (espaces de bord retirés), `SafeText` (contrôles, invisibles, bidi, remplissages U+3164/2800/115F/1160/FFA0/180E) ; prénom `@Size(100)` + `@SafeName` à l'inscription et à la mise à jour du profil.
- DTO publics : prénom seul, `authorRole`, `authorDeleted`, `mine` ; ni e-mail, ni nom, ni id utilisateur.
- Suppression de compte/établissement : clés étrangères `SET NULL`, messages conservés.

Non vérifié : course verrou/réponse (reportée à forum/03), limiteur en mémoire (une seule instance API), aucun rendu web.

