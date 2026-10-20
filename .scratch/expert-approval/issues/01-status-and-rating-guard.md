# 01: Statut d'approbation et blocage de la notation

**What to build:** Un enseignant qui s'inscrit est « en attente » ; tant qu'il n'est pas approuvé il ne peut pas noter formations ni cours, et il voit son statut dans son profil. Les experts existants sont approuvés.

**Blocked by:** None (can start immediately)

**Status:** resolved

**Worktree:** `expert-approval-01`

- [x] Migration : statut, motif de rejet (≤255) et date de décision sur l'utilisateur ; les experts existants passent « approuvé », les étudiants n'ont pas de statut
- [x] Inscription enseignant : statut « en attente » ; inscription étudiant inchangée
- [x] Notation de formation et de cours : 403 pour un expert en attente ou rejeté, accepté pour un approuvé (côté serveur, `WebSecurityConfig` et `@PreAuthorize` vérifiés)
- [x] Règle « expert approuvé » exposée en un point unique réutilisable par le forum
- [x] Le statut et le motif sont renvoyés à l'intéressé seulement
- [x] Web : profil affiche le statut ; les écrans de notation expliquent le blocage ; clés i18n fr et en
- [x] Tests d'intégration des contrôleurs, test Postgres de la migration, specs web, rouge d'abord
- [x] Glossaire : « Expert (enseignant) » et « Statut d'approbation »
