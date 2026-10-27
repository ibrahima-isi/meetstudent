# 03: Service de sauvegarde chiffrée hors site

**What to build:** Un service `backup` dans la stack Coolify qui exporte la base et les deux volumes de fichiers, les chiffre et les envoie sur un S3 hors du VPS, avec une commande de restauration qui redonne une stack fonctionnelle.

**Blocked by:** [coolify-deploy/01](01-ci-images-ghcr.md), [coolify-deploy/02](02-coolify-stack-and-proxy.md)

**Status:** ready-for-agent

**Worktree:** `coolify-deploy-03` — branche `feat/coolify-backup`

Spec : [coolify-deploy](../spec.md), user stories 22 à 29 ; [ADR 0004](../../../docs/adr/0004-encrypted-offsite-backups-r2-eu.md) ; détail : [plan, phase 3b](../../../docs/plans/2026-10-26-deploy-hetzner-coolify.md).

- [ ] `infra/backup/` : Dockerfile (`postgres:18-alpine` + `rclone`), `backup.sh`, `restore.sh`
- [ ] Image construite et publiée par la CI comme les trois autres (`meetstudent-backup`, mêmes tags), pour que le VPS ne compile rien (user story 3) ; ajout au build sur PR et au push du job `deploy` de [coolify-deploy/01](01-ci-images-ghcr.md)
- [ ] Service `backup` dans `compose.coolify.yml` : réseau `backend` seulement, volumes `api-uploads` et `api-private` en lecture seule, `sleep infinity` pour la tâche planifiée Coolify
- [ ] `backup.sh` : `pg_dump` format custom + archive de chaque volume → `rclone copy` vers un remote `crypt` qui enveloppe un remote S3 → suppression de la copie locale ; code de sortie non nul à la moindre erreur
- [ ] rclone configuré uniquement par variables d'environnement (S3 R2 juridiction EU, mot de passe et sel `crypt`) ; rétention 30 jours = règle de cycle de vie du bucket, pas de logique dans le script
- [ ] `scripts/backup.sh` et `restore.sh` restent dédiés à `compose.prod.yml`
- [ ] Test « point 2 » de la spec avec MinIO en S3 local : stack peuplée (comptes, une école, un document privé) → sauvegarde → objets illisibles en clair → restauration dans une stack vide → connexion d'un compte, école présente, document privé identique octet pour octet ; S3 injoignable → code non nul
- [ ] `docker compose down -v` après le test

## Comments
