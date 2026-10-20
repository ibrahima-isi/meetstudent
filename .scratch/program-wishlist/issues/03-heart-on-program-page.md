# 03: Cœur sur la page formation et lien « voir plus »

**What to build:** l'étudiant ajoute ou retire une formation de sa liste de souhaits depuis la page `/:lang/programs/:id`, et le lien « voir plus » d'une formation du panier et du profil mène à cette page.

**Blocked by:** None (program-wishlist/02 et program-details/03 sont fusionnés dans `dev`).

**Status:** ready-for-agent

**Worktree:** `program-wishlist-03`

- [ ] `<app-wishlist-heart>` dans l'en-tête de la page formation ; masqué aux experts ; visiteur anonyme renvoyé à la connexion avec `returnUrl` ; l'état suit la liste (appel `wishlist.load()` pour un étudiant)
- [ ] Le lien « voir plus » des formations du panier et du profil pointe vers `/:lang/programs/:id` (et non plus vers la fiche école)
- [ ] fr/en synchronisés, mode sombre (garde `dark-mode.guard.spec`), 375 px sans débordement
- [ ] Specs Karma rouges d'abord (cœur sur la page, lien du panier et du profil)
