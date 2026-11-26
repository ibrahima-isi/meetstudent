# 03 · Sauvegarder et restaurer PostgreSQL et les médias

Blocked by: [portable-media-storage/02](02-s3-compatible-media-storage.md)
Status: resolved

## What to build

L’exploitant peut produire une archive chiffrée avec la base et les fichiers, puis restaurer ensemble les deux dans un environnement isolé, en mode disque ou S3.

## Acceptance criteria

- [x] Le dump et l’inventaire des clés partagent un instantané PostgreSQL. L’archive couvre les fichiers référencés et les anciens fichiers publics servis.
- [x] Le job vérifie les sommes de contrôle et la complétude, sérialise les exécutions et retente une sauvegarde incohérente. Une exécution incomplète laisse la dernière archive complète utilisable.
- [x] Les archives historiques restent restaurables. Un test restaure la base et les fichiers vers une destination vide et vérifie les URL et droits d’accès.
- [x] La sauvegarde chiffrée conserve une rétention de 30 jours en Europe chez un fournisseur indépendant du VPS et du stockage actif.

## Comments

Intégré à `main` via la [PR #293](https://github.com/ibrahima-isi/meetstudent/pull/293). La PR #292 conserve la restauration des archives JSON et l’accès à `awscrypt:`, ainsi que les garanties de vérification avant publication et de restauration vers un bucket vide. Le manifeste SHA-256 par fichier et la destination S3 générique restent disponibles.

- `infra/backup/backup.sh`, `infra/backup/media-manifest.sh`, `infra/backup/restore.sh`, `infra/backup/backup-env.sh`, `infra/backup/Dockerfile` :
  - Instantané PostgreSQL unique partagé (`pg_export_snapshot()` + `pg_dump --snapshot`) entre `db.dump` et l’inventaire `media`, plus collecte des anciens chemins `public/**`, en ignorant `.media-*.tmp`.
  - Support complet de `MEDIA_STORAGE_BACKEND=disk` et `MEDIA_STORAGE_BACKEND=s3` (`MEDIA_S3_*`) en sauvegarde et en restauration.
  - Vérification des tailles et sommes SHA-256 par fichier (`media-manifest.tsv`), sérialisation par `flock`, réessai automatique sur incohérence transitoire, et publication atomique de `SHA256SUMS` en dernier pour préserver la dernière archive complète.
  - Restauration rétrocompatible des archives historiques (sans `media-manifest.tsv`) et des nouvelles archives vers des volumes disque vides ou un bucket S3 vide.
  - Conservation du chiffrement `rclone crypt`, de la juridiction EU et de la rétention 30 jours, avec support d’une destination S3 EU indépendante (`BACKUP_S3_*`) et refus des destinations Hetzner ou colocalisées avec `MEDIA_S3_*`, tout en gardant les archives `r2crypt:` lisibles (`restore.sh --remote r2crypt:`).
- `compose.coolify.yml`, `docs/DEPLOY.md`, `apps/api/docs/production.md` : câblage et documentation des variables `MEDIA_*` et `BACKUP_S3_*`/`BACKUP_REMOTE` sur le service `backup`.
- Vérification après le dernier changement :
  - `TEST_PREFIX=ms-t03-backup IMAGE_TAG=ms-t03 ./infra/tests/backup.test.sh all` : réussi (tous les cas `scripts` et `roundtrip` verts : instantané PG 18 partagé sous mutations concurrentes, verrou, réessai, interruption, restauration disque et S3 d’archives nouvelles et historiques avec vérification des URL publiques/historiques, accès privé propriétaire/admin et refus 401/403, et destination S3 EU indépendante).
  - `./infra/tests/deploy-docs.test.sh` : 5/5 réussis.
  - `./infra/tests/coolify-stack.test.sh static` : tous les contrôles statiques et CSP réussis.
