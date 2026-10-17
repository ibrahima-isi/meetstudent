# 04: Extraire la logique hydrated/signedIn partagée

**What to build:** Le trio `hydrated` / `signedIn` / `afterNextRender` existe en 4 copies (dock-navbar, site-footer, cta-band, landing-hero). Extraire une fonction partagée (`@shared`), sans changer le comportement SSR/hydratation. Plus de 5 fichiers : accord du propriétaire requis au moment de le faire.

**Blocked by:** None (à faire après vérification manuelle des tickets live-test-gaps).

**Status:** needs-triage

- [ ] Une seule implémentation, utilisée par les 4 composants.
- [ ] Aucun changement visible ni d'erreur d'hydratation ; specs existantes vertes.
- [ ] Suite web et build verts.
