# 02: Back office, jetons et thème alignés sur le web

**What to build:** Le back office reprend les mêmes couleurs, boutons et thème clair/sombre que le site public, avec une bascule de thème fonctionnelle. La sidebar d'administration et les écrans de modération restent inchangés dans leur structure.

**Blocked by:** None (can start immediately). Lire d'abord le plan de réécriture Vite du back office pour éviter un travail jeté.

**Status:** in-progress (partiel : PR A livrée, PR B à faire)

- [ ] Les écrans du back office utilisent les mêmes jetons sémantiques et styles de boutons que le web. **En attente (PR B)** : seuls le shell (sidebar, en-tête) et les jetons/`.btn` sont faits ; 17 gabarits utilisent encore des classes de palette brutes (slate/indigo/white…).
- [x] Le thème clair/sombre est appliqué avant le premier affichage et peut être basculé.
- [x] Aucune régression : non-admin toujours refusé, sidebar et modération intactes.
- [x] Specs du shell et du thème en échec d'abord, puis vertes.
