# 04: Webhook de déploiement Coolify et guide

**What to build:** Une fois les images poussées, le job `deploy` demande à Coolify de déployer ; le guide de déploiement explique comment installer, déployer, revenir en arrière et restaurer avec Coolify.

**Blocked by:** [coolify-deploy/01](01-ci-images-ghcr.md), [coolify-deploy/02](02-coolify-stack-and-proxy.md), [coolify-deploy/03](03-encrypted-offsite-backup.md)

**Status:** ready-for-agent

**Worktree:** `coolify-deploy-04` — branche `ci/coolify-deploy-webhook`

Spec : [coolify-deploy](../spec.md), user stories 1, 5, 9, 10, 31 ; [ADR 0003](../../../docs/adr/0003-coolify-raw-compose-ghcr.md) ; détail : [plan, phases 4 et 6](../../../docs/plans/2026-10-26-deploy-hetzner-coolify.md).

- [ ] Dernière étape du job `deploy`, après le push des images : `curl -fsS -H "Authorization: Bearer $COOLIFY_TOKEN" "$COOLIFY_WEBHOOK"` ; secrets lus depuis l'environnement GitHub `production` ; un échec du webhook rend le run rouge
- [ ] Rien d'autre ne déclenche un déploiement : pas sur PR ni sur `dev`, et le runbook impose l'auto-déploiement Coolify désactivé
- [ ] `docs/DEPLOY.md` : section Coolify (ressource Compose en mode Raw, variables, fichier de configuration dynamique du proxy, tâche planifiée `backup`, webhook et secrets GitHub, passage des paquets GHCR en public, retour arrière par `IMAGE_TAG=sha-<commit>` puis remise à `latest`, restauration)
- [ ] `apps/api/docs/production.md` : variables de sauvegarde (R2, `crypt`)
- [ ] Preuve : CI verte sur la PR (le job `deploy` ne s'y exécute pas) ; l'appel réel du webhook se vérifie dans [coolify-deploy/05](05-first-production-deploy.md)

## Comments
