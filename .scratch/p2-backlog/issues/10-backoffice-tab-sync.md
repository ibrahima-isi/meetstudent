# 10: Back-office : synchronisation des jetons entre onglets

**What to build:** Un admin avec plusieurs onglets du back-office ne se fait plus déconnecter par une fausse détection de rejeu : chaque onglet reprend les jetons renouvelés par un autre, relit le stockage avant de rafraîchir et, si un autre onglet a déjà fait la rotation, réutilise ses jetons au lieu de rejouer l'ancien. Une déconnexion dans un onglet se propage aux autres. Même mécanisme que celui livré dans le web par #241.

**Blocked by:** [p2-backlog/01](01-e2e-harness.md)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (onglets).

- [ ] Specs : jetons écrits par un autre onglet adoptés ; rotation déjà faite ailleurs → requête rejouée sans appel de refresh ni logout ; 401 réel → fin de session comme aujourd'hui
- [ ] Déconnexion dans un onglet → les autres se vident
- [ ] Parcours Playwright à deux onglets : un onglet rafraîchit, l'autre continue sans être déconnecté

## Comments
