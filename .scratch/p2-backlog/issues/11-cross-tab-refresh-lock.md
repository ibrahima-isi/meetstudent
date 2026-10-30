# 11: Verrou de rafraîchissement partagé entre onglets (web et back-office)

**What to build:** Quand plusieurs onglets veulent rafraîchir la Session en même temps (par exemple à la restauration d'une session de navigateur), un seul appelle `/auth/refresh` ; les autres attendent puis utilisent les jetons obtenus. Plus aucun onglet n'est déconnecté parce que sa réponse 401 est arrivée avant l'écriture des nouveaux jetons. Sans `navigator.locks` (ou côté SSR), le comportement actuel s'applique.

**Blocked by:** [p2-backlog/10](10-backoffice-tab-sync.md)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (onglets). Limite connue laissée par #241, que ce ticket ferme.

- [ ] Specs (web et back-office) : deux rafraîchissements simultanés → un seul appel réseau, les deux requêtes rejouées avec les nouveaux jetons
- [ ] Sans `navigator.locks` : repli testé, aucun crash SSR
- [ ] Parcours Playwright : deux onglets à jeton d'accès expiré rechargés ensemble → aucun des deux n'est déconnecté

## Comments
