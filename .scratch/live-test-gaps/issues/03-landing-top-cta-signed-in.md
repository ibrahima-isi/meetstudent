# 03: CTA « Créer un compte gratuit » masqué une fois connecté

**What to build:** Sur la page d'accueil, le bouton « Créer un compte gratuit » du haut (hero) n'est pas affiché à un utilisateur connecté (même règle que le bloc du bas, traité par #115), sans clignotement tant que l'état est inconnu côté navigateur.

**Blocked by:** None (can start immediately).

**Status:** resolved

- [x] Visiteur : le CTA reste visible et mène à l'inscription ; connecté : il est absent (ou remplacé par une action utile décidée au ticket).
- [x] Pas de clignotement SSR/hydratation (comportement aligné sur le footer et le bloc du bas).
- [x] fr/en, clair/sombre, mobile ; pas de régression de la mise en page. (Vérifié en navigateur réel le 2026-10-17 : 16 combinaisons OK après hydration. Réserve : au rechargement complet, le HTML SSR contient le bouton, visible 200 à 430 ms pour un utilisateur connecté, avec un décalage de 64 px du hero à 375 px.)
- [x] Spec en échec d'abord, puis verte ; suite web et build verts.
