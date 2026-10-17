# 04: Extraire la logique hydrated/signedIn partagée

**What to build:** Le trio `hydrated` / `signedIn` / `afterNextRender` existe en 4 copies (dock-navbar, site-footer, cta-band, landing-hero). Extraire une fonction partagée (`@shared`), sans changer le comportement SSR/hydratation. Plus de 5 fichiers : accord du propriétaire requis au moment de le faire.

**Blocked by:** None (à faire après vérification manuelle des tickets live-test-gaps).

**Status:** resolved

- [x] Une seule implémentation, utilisée par les 4 composants.
- [x] Aucun changement visible ni d'erreur d'hydratation ; specs existantes vertes.
- [x] Suite web et build verts.

## Comments

2026-10-17 : liste de fichiers validée par le propriétaire (dock-navbar, site-footer, cta-band, landing-hero, plus la fonction partagée et sa spec). `injectHydratedSession()` dans `@shared/hydrated-session` renvoie `{ hydrated, signedIn }` ; les 4 composants l'utilisent, leurs specs n'ont pas changé. Pas 5 copies mais 4 (le ticket disait 5 fichiers au total).
