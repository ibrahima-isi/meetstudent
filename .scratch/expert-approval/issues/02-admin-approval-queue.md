# 02: File d'approbation des experts au back office

**What to build:** L'administrateur voit les experts en attente, les approuve ou les rejette avec un motif facultatif, et la décision s'applique sans reconnexion de l'expert.

**Blocked by:** [01](01-status-and-rating-guard.md)

**Status:** resolved

**Worktree:** `expert-approval-02`

- [ ] API admin : liste paginée (taille ≤100, tri fixe par date d'inscription décroissante) filtrable par statut ; approuver ; rejeter avec motif ≤255 ; ré-approuver un rejeté
- [ ] Refus (400/409) si le compte n'est pas un expert ; 401 anonyme, 403 non-admin
- [ ] Le changement de statut prend effet immédiatement sur les droits (cohérent avec l'ADR 0001)
- [ ] Back office : entrée « Experts », onglets par statut, actions avec confirmation, états vide/chargement/erreur avec nouvel essai
- [ ] Tests d'intégration et specs de page, rouge d'abord
