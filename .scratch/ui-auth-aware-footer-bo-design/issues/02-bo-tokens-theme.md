# 02: Back office, jetons et thème alignés sur le web

**What to build:** Le back office reprend les mêmes couleurs, boutons et thème clair/sombre que le site public, avec une bascule de thème fonctionnelle. La sidebar d'administration et les écrans de modération restent inchangés dans leur structure.

**Blocked by:** None (can start immediately). Lire d'abord le plan de réécriture Vite du back office pour éviter un travail jeté.

**Status:** resolved (PR A + PR B fusionnées dans `dev` : `f41c53b`, `0b17bc4`)

- [x] Les écrans du back office utilisent les mêmes jetons sémantiques et styles de boutons que le web. PR B (`feat/backoffice-token-migration`) : les 17 gabarits migrés vers `bg-card`, `text-foreground`, `border-border`, `.btn*`, `.field-input` ; les couleurs de statut restantes (succès, avertissement) portent toutes une paire `dark:`. Le shell est migré par la PR A.
- [x] Le thème clair/sombre est appliqué avant le premier affichage et peut être basculé.
- [x] Aucune régression : non-admin toujours refusé, sidebar et modération intactes.
- [x] Specs du shell et du thème en échec d'abord, puis vertes.

## Résultat PR B

- Spec garde `shared/dark-mode.guard.spec.ts` (17 gabarits hors shell, lecture des sources servies par une entrée `assets` de la cible `test` d'`angular.json`) : rouge d'abord (17 échecs, classes de palette sans `dark:`), puis vert après migration.
- `npm test` ChromeHeadless : 328/328 verts (18 specs ajoutées, aucune spec existante modifiée). `npx ng build` OK.
- Non vérifié : rendu visuel clair/sombre en navigateur réel ; la densité des boutons de tableau est conservée via `h-8`/`h-9` sur `.btn`.
