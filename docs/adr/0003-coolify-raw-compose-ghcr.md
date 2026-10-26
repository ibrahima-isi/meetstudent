# Production sur Coolify en Compose « Raw », images construites par la CI

La production tourne sur un VPS Hetzner unique piloté par Coolify. Les images de l'API, du web et du back-office sont construites et testées par GitHub Actions, publiées sur GHCR (publiques, taguées `sha-<commit>` et `latest`), puis un webhook Coolify authentifié déclenche le déploiement quand `main` bouge. Le VPS ne compile jamais : Maven et le build Angular SSR pèseraient sur la machine qui sert le trafic, et un tag par commit permet de revenir à une version précise.

La stack est déclarée dans `compose.coolify.yml` et déployée en mode **Raw**, avec nos propres labels Traefik branchés sur le proxy Traefik de Coolify. Le mode géré, où Coolify génère les labels depuis les domaines saisis dans l'interface, a été écarté : il ne sait pas exprimer le rate limit par chemin (`/api/v1/auth`), la limite de 10 Mo sur les uploads, le retrait de l'en-tête `Forwarded` ni les en-têtes de sécurité que `compose.prod.yml` applique déjà et qui ont été validés en smoke test. Ne pas « simplifier » vers le mode géré sans reporter ces protections.

## Considered Options

- **Build par Coolify sur le VPS** : plus simple à brancher, rejeté pour la charge et l'absence de tags par commit.
- **Mode Compose géré** : rejeté pour la perte des middlewares décrits ci-dessus.
- **Remplacer `compose.prod.yml`** : rejeté ; il reste la voie de déploiement sans Coolify (son propre Traefik) et la référence testée.

## Consequences

- L'auto-déploiement de Coolify sur push Git reste désactivé : seul le webhook appelé par la CI, une fois les images poussées, déclenche un déploiement.
- Un retour arrière se fait en fixant `IMAGE_TAG=sha-<commit>` dans Coolify puis en redéployant ; tant que ce tag est fixé, les déploiements suivants restent sur cette version.
