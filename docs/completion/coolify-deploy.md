# Suivi : coolify-deploy

Spec : [.scratch/coolify-deploy/spec.md](../../.scratch/coolify-deploy/spec.md) — décisions : [ADR 0003](../adr/0003-coolify-raw-compose-ghcr.md), [ADR 0004](../adr/0004-encrypted-offsite-backups-r2-eu.md).

- [ ] [coolify-deploy/01](../../.scratch/coolify-deploy/issues/01-ci-images-ghcr.md) : Images construites par la CI et publiées sur GHCR. État : resolved (implémentation), PR à ouvrir (branche `ci/coolify-images`, commit `e69797b`). Preuves : `infra/tests/ci-images.test.sh` → 7/7 ok (rouge → vert consigné dans le ticket) ; build local des trois images avec `docker buildx bake -f docker-bake.hcl` → exit 0, aucun fichier secret dans les images. Reste avant de cocher : run CI vert sur la PR avec le job `images / build` ; push GHCR et environnement `production` vérifiés seulement au premier push sur `main` (coolify-deploy/05).
- [ ] [coolify-deploy/02](../../.scratch/coolify-deploy/issues/02-coolify-stack-and-proxy.md) : Stack Coolify et protections du proxy. État : ready-for-agent.
- [ ] [coolify-deploy/03](../../.scratch/coolify-deploy/issues/03-encrypted-offsite-backup.md) : Service de sauvegarde chiffrée hors site. État : ready-for-agent.
- [ ] [coolify-deploy/04](../../.scratch/coolify-deploy/issues/04-deploy-webhook-and-docs.md) : Webhook de déploiement Coolify et guide. État : ready-for-agent.
- [ ] [coolify-deploy/05](../../.scratch/coolify-deploy/issues/05-first-production-deploy.md) : Premier déploiement réel et vérifications. État : ready-for-human.
