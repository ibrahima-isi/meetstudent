# 12: Un logout avec un ancien refresh token ferme la Session

**What to build:** `POST /api/v1/auth/logout` retrouve aussi la Session à partir d'un refresh token déjà remplacé (historique V37) et la ferme. Une victime qui se déconnecte avec son ancien jeton expulse donc le voleur qui détient le jeton courant. La réponse reste identique dans tous les cas : jeton courant, ancien ou inconnu.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (logout). Respecte [ADR 0001](../../../docs/adr/0001-credential-session-version.md) et [ADR 0002](../../../docs/adr/0002-session-inactivity.md).

- [ ] Logout avec l'ancien secret → la Session est fermée ; le jeton courant et le jeton d'accès sont ensuite refusés (test d'intégration sans `@Transactional`, rouge vu)
- [ ] Logout avec le jeton courant : inchangé ; avec un jeton inconnu : rien n'est fermé, même réponse
- [ ] Les autres Sessions du même utilisateur restent ouvertes

## Comments
