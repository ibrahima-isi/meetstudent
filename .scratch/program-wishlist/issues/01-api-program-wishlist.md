# 01: API de la liste de souhaits des formations

**What to build:** Un étudiant ajoute, retire et lit les formations de sa liste de souhaits via l'API.

**Blocked by:** None (can start immediately)

**Status:** resolved

**Worktree:** `program-wishlist-01`

- [x] Migration : table de jointure utilisateur-formation, cascades sur suppression de formation et de compte
- [x] POST/DELETE sous la liste de l'utilisateur, lecture des formations ; ajout idempotent ; formation inconnue 404
- [x] STUDENT seulement et propriétaire seulement : autre utilisateur 403, expert et anonyme refusés
- [x] Tests d'intégration, rouge d'abord
