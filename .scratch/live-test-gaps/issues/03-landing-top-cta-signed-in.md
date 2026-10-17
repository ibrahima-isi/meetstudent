# 03: CTA « Créer un compte gratuit » masqué une fois connecté

**What to build:** Sur la page d'accueil, le bouton « Créer un compte gratuit » du haut (hero) n'est pas affiché à un utilisateur connecté (même règle que le bloc du bas, traité par #115), sans clignotement tant que l'état est inconnu côté navigateur.

**Blocked by:** None (can start immediately).

**Status:** needs-triage

- [ ] Visiteur : le CTA reste visible et mène à l'inscription ; connecté : il est absent (ou remplacé par une action utile décidée au ticket).
- [ ] Pas de clignotement SSR/hydratation (comportement aligné sur le footer et le bloc du bas).
- [ ] fr/en, clair/sombre, mobile ; pas de régression de la mise en page.
- [ ] Spec en échec d'abord, puis verte ; suite web et build verts.
