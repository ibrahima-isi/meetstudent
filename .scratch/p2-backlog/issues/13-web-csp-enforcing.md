# 13: CSP du web bloquante avec nonce SSR et endpoint de rapport

**What to build:** Le web envoie une `Content-Security-Policy` bloquante, générée par le serveur SSR avec un nonce par réponse, appliqué aux scripts et styles inline (dont le script d'amorçage d'Angular qui change à chaque page). Traefik n'écrase plus cette CSP. Les violations sont envoyées à un endpoint de rapport de l'API, limité en débit, qui les journalise. Une injection de script est bloquée, l'application fonctionne normalement.

**Blocked by:** [p2-backlog/01](01-e2e-harness.md)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (CSP web). Suite de #231/#238 (Report-Only aujourd'hui).

- [ ] Chaque réponse SSR porte une CSP bloquante avec un nonce unique ; les scripts inline portent ce nonce (test serveur)
- [ ] Middleware Traefik du web sans CSP propre (variantes Coolify et `compose.prod.yml` identiques) ; `infra/tests` mis à jour, rouge puis vert
- [ ] Endpoint de rapport : accepte un rapport CSP, journalise sans données sensibles, refuse en 429 au-delà de la limite (tests d'intégration)
- [ ] Parcours Playwright : landing, catalogue, connexion, forum sans aucune violation ; un script injecté est bloqué et rapporté
- [ ] `docs/DEPLOY.md` : recopier le fichier dynamique avant le déploiement

## Comments
