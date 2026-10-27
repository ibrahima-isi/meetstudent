# 03: Service de sauvegarde chiffrée hors site

**What to build:** Un service `backup` dans la stack Coolify qui exporte la base et les deux volumes de fichiers, les chiffre et les envoie sur un S3 hors du VPS, avec une commande de restauration qui redonne une stack fonctionnelle.

**Blocked by:** [coolify-deploy/01](01-ci-images-ghcr.md), [coolify-deploy/02](02-coolify-stack-and-proxy.md)

**Status:** resolved

**Worktree:** `coolify-deploy-03` — branche `feat/coolify-backup`

Spec : [coolify-deploy](../spec.md), user stories 22 à 29 ; [ADR 0004](../../../docs/adr/0004-encrypted-offsite-backups-r2-eu.md) ; détail : [plan, phase 3b](../../../docs/plans/2026-10-26-deploy-hetzner-coolify.md).

- [x] `infra/backup/` : Dockerfile (`postgres:18-alpine` + `rclone`), `backup.sh`, `restore.sh`
- [x] Image construite et publiée par la CI comme les trois autres (`meetstudent-backup`, mêmes tags), pour que le VPS ne compile rien (user story 3) ; ajout au build sur PR et au push du job `deploy` de [coolify-deploy/01](01-ci-images-ghcr.md)
- [x] Service `backup` dans `compose.coolify.yml` : réseau `backend` pour la base, plus un réseau `egress` (non interne) réservé à `backup` pour joindre R2, accordé par le propriétaire le 2026-10-27 (voir Comments) ; volumes `api-uploads` et `api-private` en lecture seule, `sleep infinity` pour la tâche planifiée Coolify
- [x] `backup.sh` : `pg_dump` format custom + archive de chaque volume → `rclone copy` vers un remote `crypt` qui enveloppe un remote S3 → suppression de la copie locale ; code de sortie non nul à la moindre erreur
- [x] rclone configuré uniquement par variables d'environnement (S3 R2 juridiction EU, mot de passe et sel `crypt`) ; rétention 30 jours = règle de cycle de vie du bucket, pas de logique dans le script
- [x] `scripts/backup.sh` et `restore.sh` restent dédiés à `compose.prod.yml`
- [x] Test « point 2 » de la spec avec MinIO en S3 local : stack peuplée (comptes, une école, un document privé) → sauvegarde → objets illisibles en clair → restauration dans une stack vide → connexion d'un compte, école présente, document privé identique octet pour octet ; S3 injoignable → code non nul
- [x] `docker compose down -v` après le test

## Comments

### 2026-10-27 — avancement (branche `feat/coolify-backup`, commit `eda7066`), en attente d'accord

Fait (5 fichiers) : `infra/backup/{Dockerfile,backup.sh,restore.sh}`, service `backup` dans `compose.coolify.yml`, test `infra/tests/backup.test.sh`.

- Écart avec le ticket : le service `backup` est sur `backend` **et** sur un nouveau réseau `egress` (non interne). `backend` est `internal: true`, donc sans sortie : sur `backend` seul, R2 serait injoignable (sonde : un conteneur sur un réseau interne n'atteint pas `https://cloudflare.com`, code `000` ; sur un réseau normal, `301`). Postgres reste seul sur `backend` avec l'API et la sauvegarde ; `egress` ne porte que `backup`.
- rclone par variables d'environnement : remote `r2` (s3, provider Cloudflare, `R2_ENDPOINT`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `no_check_bucket`) enveloppé par `r2crypt` (crypt sur `r2:${R2_BUCKET}`). `BACKUP_CRYPT_PASSWORD` et `BACKUP_CRYPT_SALT` portent les originaux (ceux du gestionnaire de mots de passe) ; les scripts les passent à rclone obscurcis (`rclone obscure -`, par l'entrée standard). Variables non exigées au rendu du compose (sinon le test statique et la CI de 02 casseraient) : les scripts refusent de tourner sans elles (code 2).
- Restauration : les volumes sont `:ro` sur `/data` ; `restore.sh` écrit dans `/restore/{uploads,private}`, montés par un `docker compose run --rm -v api-uploads:/restore/uploads -v api-private:/restore/private backup restore.sh latest --yes` (un `-v` sur la même cible `/data/...` ne lève pas le `:ro`, vérifié). Rien n'est effacé avant téléchargement et vérification des sommes.

Preuves (Docker 29.6.1, Compose v5.3.0 ; MinIO réel `bitnamilegacy/minio:2025.5.24` à la place de R2, `minio/minio` et `quay.io/minio/minio` refusés au pull ; image API locale `sha-c57b8c3…` retaguée `backup-test`) :
- Rouge : sans `infra/backup` → `FAIL the backup image builds` ; sans service → `FAIL the stack with its backup service starts` ; sans `restore.sh` → `exec: restore.sh: not found` + 4 échecs.
- Mutations vues rouges : remote non chiffré (`r2:` au lieu de `r2crypt:`) → `FAIL no object name gives away…` et `FAIL no object is readable in clear (4 readable)` ; `rclone copy … || true` sans nettoyage → `FAIL an unreachable S3 fails the backup (exit 0)` + copies locales restantes.
- Vert : `IMAGE_TAG=backup-test infra/tests/backup.test.sh` → 20 ok, `all passed`, exit 0 : sauvegarde exit 0, aucune copie locale, 4 objets aux noms chiffrés, aucun lisible en clair (ni gzip, ni `PGDMP`, ni somme), restauration dans une stack vide → connexion de l'étudiante, école présente, document privé identique (SHA-256), mauvaise clé → échec, configuration incomplète → exit 2, S3 arrêté → exit 1 sans copie locale ; écriture refusée sur les volumes. `infra/tests/coolify-stack.test.sh static` → all passed ; `infra/tests/ci-images.test.sh` → all passed.
- Nettoyage : `down -v` des deux stacks, MinIO et réseau de test retirés par le `trap`.

Reste (2 fichiers de plus, accord du propriétaire demandé) : ligne `meetstudent-backup` dans `infra/docker-bake.hcl` et dans `infra/tests/ci-images.test.sh` (qui exige aujourd'hui exactement trois images).

### 2026-10-27 — résolu (branche `feat/coolify-backup`, commits `eda7066`, `491c37f`)

Décisions du propriétaire (réponses du 2026-10-27) :
1. Accord pour les deux fichiers au-delà de cinq : `infra/docker-bake.hcl` (ligne `meetstudent-backup`, contexte `infra/backup`) et `infra/tests/ci-images.test.sh` (quatre images).
2. Réseau `egress` dédié, non interne, pour `backup` seul : accepté (écart avec « `backend` seulement », sans quoi R2 est injoignable).
3. Mot de passe et sel `crypt` : les originaux, en clair dans les secrets Coolify ; les scripts les obscurcissent pour rclone.
4. Variables de sauvegarde non exigées au rendu du compose ; les scripts refusent de tourner sans elles (code 2).

Après fusion de `origin/feat/coolify-deploy` (`4e4e5cd`) dans la branche (`fb1e53c`) :
- Rouge : `infra/tests/ci-images.test.sh` avec quatre images attendues → `FAIL images built` (`meetstudent-backup` absent), tags, plateforme et contexte manquants (4 assertions).
- Vert : après la ligne de matrice → `infra/tests/ci-images.test.sh` 8/8 ok, exit 0 ; `infra/tests/coolify-stack.test.sh static` → all passed ; `docker buildx bake -f infra/docker-bake.hcl meetstudent-backup` construit l'image `linux/amd64` (émulée sur arm64 ; rclone v1.74.1, pg_dump 18.6, `backup.sh` et `restore.sh` exécutables) ; `IMAGE_TAG=backup-test infra/tests/backup.test.sh` relancé → 20 ok, all passed.
- `scripts/backup.sh` et `scripts/restore.sh` inchangés (`git diff origin/feat/coolify-deploy -- scripts/` vide).
- Nettoyage : `down -v` des deux stacks par le `trap`, MinIO et réseau de test retirés ; images tirées ou construites pour le test supprimées.

Non vérifié :
- R2 réel : endpoint EU, jeton limité au bucket avec `no_check_bucket`, règle de cycle de vie de 30 jours (phase 5, propriétaire), rclone `provider=Cloudflare` seulement éprouvé contre MinIO.
- Tâche planifiée Coolify et sa notification d'échec ; restauration sur le VPS (nom de projet Coolify à documenter, ticket 04).
- Build de l'image sur PR et publication GHCR par la CI (aucun run GitHub encore ; push au premier déploiement, ticket 05).
- Documentation des variables (`R2_*`, `BACKUP_CRYPT_*`) et de la commande de restauration : ticket 04.
- À savoir : l'image `postgres` déclare un `VOLUME`, chaque conteneur `backup` reçoit donc un volume anonyme ; rclone affiche un avis inoffensif « config file not found ».
