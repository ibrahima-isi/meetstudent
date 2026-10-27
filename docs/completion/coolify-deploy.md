# Suivi : coolify-deploy

Spec : [.scratch/coolify-deploy/spec.md](../../.scratch/coolify-deploy/spec.md) — décisions : [ADR 0003](../adr/0003-coolify-raw-compose-ghcr.md), [ADR 0004](../adr/0004-encrypted-offsite-backups-r2-eu.md).

- [ ] [coolify-deploy/01](../../.scratch/coolify-deploy/issues/01-ci-images-ghcr.md) : Images construites par la CI et publiées sur GHCR. État : ready-for-agent.
- [x] [coolify-deploy/02](../../.scratch/coolify-deploy/issues/02-coolify-stack-and-proxy.md) : Stack Coolify et protections du proxy. État : resolved, implémentation et vérification locale terminées ; branche `ci/coolify-stack` poussée (commits `d0b4f86`, `cda8468`), PR vers `dev` pas encore ouverte. Preuves : `infra/tests/coolify-stack.test.sh static` rouge sans le fichier puis vert ; `infra/tests/coolify-stack.test.sh live` rouge (17 échecs sans configuration dynamique, collision `api`/`db` démontrée) puis 35 ok derrière une réplique du Traefik de Coolify : single-origin, back-office sur son domaine, 301 vers HTTPS, HSTS et nosniff, rafale → `4 401 16 429`, 11 Mo → 413, SSR avec une école réelle, Postgres injoignable ; `down -v` à chaque fois. Non vérifié : job CI sur GitHub, vrai Traefik de Coolify (noms, mode Raw, Let's Encrypt), retrait de `Forwarded`. Détail dans le ticket.
- [ ] [coolify-deploy/03](../../.scratch/coolify-deploy/issues/03-encrypted-offsite-backup.md) : Service de sauvegarde chiffrée hors site. État : ready-for-agent.
- [ ] [coolify-deploy/04](../../.scratch/coolify-deploy/issues/04-deploy-webhook-and-docs.md) : Webhook de déploiement Coolify et guide. État : ready-for-agent.
- [ ] [coolify-deploy/05](../../.scratch/coolify-deploy/issues/05-first-production-deploy.md) : Premier déploiement réel et vérifications. État : ready-for-human.
