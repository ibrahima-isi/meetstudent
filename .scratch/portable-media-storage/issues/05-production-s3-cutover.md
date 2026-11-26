# 05 · Préparer la bascule des médias de production vers S3

Blocked by: [portable-media-storage/03](03-s3-media-backup-restore.md), [portable-media-storage/04](04-rehearse-media-transfer-rollback.md)
Status: resolved

## What to build

L’exploitant dispose d’une procédure répétée et d’un outillage pour migrer la production vers le bucket privé compatible S3 de son choix. La procédure décrit la courte pause des modifications et les vérifications avant réouverture des écritures.

## Acceptance criteria

- [x] L’endpoint, le bucket, la région de signature et le mode d’adressage se configurent au déploiement. La préparation fonctionne avec des buckets privés compatibles S3 à des endpoints différents.
- [x] Une répétition avec une destination S3 isolée couvre la sauvegarde, la copie initiale, le drainage des écritures, la validation de l’inventaire final et l’activation. Elle consigne la durée de la pause et le point de restauration.
- [x] Avant la réouverture, la répétition vérifie les lectures publiques et privées et les refus d’accès. Après réouverture, elle vérifie un dépôt, une suppression et une sauvegarde depuis S3.
- [x] La procédure décrit la qualification du bucket de l’exploitant, la sauvegarde indépendante et le retour au stockage source avec les fichiers créés ou supprimés après activation.
- [x] Le suivi distingue la répétition isolée et l’activation réelle dans Coolify.

## Comments

- Implémenté sur `feature/portable-media-storage-05` :
  - **Configuration au déploiement & qualification multi-endpoints** :
    - `apps/api/src/test/java/com/bowe/meetstudent/unit/config/S3MediaStorageConfigurationTest.java` : couverture explicite de profils S3 distincts (AWS S3 régional `eu-west-3` en `virtual-hosted` + `session-token`, Cloudflare R2 EU `auto` en `path-style`, Hetzner `fsn1` en `virtual-hosted`, et rejet des endpoints invalides).
    - `infra/media/media_transfer.py` : commande `qualify-bucket` (`qualify_bucket`, `check_backup_independence`, `S3Endpoint.head_object_status` / `read_range` / `anonymous_get`, durcissement validation URL) contrôlant l’accès privé uniquement (`401`/`403` sur `public/`, `private/` et listing), `404` sur clé absente (`s3:ListBucket`), lecture/écriture/plage `206`/liste/suppression, `--require-https`, et l’indépendance EU de la destination de sauvegarde vis-à-vis du VPS et de `MEDIA_S3_*`.
    - `ApiClient.verify_reads` / `verify-api` / `cutover --verify-reads` enrichis pour vérifier avant réouverture : lectures publiques (`/uploads/public/**` + chemin historique + `Range: bytes=0-3` `206`), lectures privées **propriétaire ET admin** (`GET /api/v1/media/{id}` -> `200`), refus anonymes (`401`), non-propriétaires (`403`), lecture anonyme directe S3 refusée (`403`), et écritures toujours pausées (`503` + `Retry-After`).
  - **Répétition isolée complète** (`infra/tests/s3-cutover-rehearsal.test.sh` avec PostgreSQL 18 + `meetstudent-api` + `infra/backup` + 3 endpoints MinIO isolés) :
    - **Endpoints qualifiés et exercés avec l’API réelle** :
      - Endpoint A : `http://127.0.0.1:59006` (`region=us-east-1`, `path-style=true`).
      - Endpoint B : `http://localhost:59007` (`region=eu-west-1`, `path-style=false` / `virtual-hosted`).
      - Backup EU indépendant : `http://s3.eu.backup-offsite.test:9000` (`region=eu-central-1`, `path-style=true`).
      - Contrôles négatifs validés : refus `http://` avec `--require-https`, refus d’un bucket `public/` lisible anonymement, et refus d’une sauvegarde colocalisée.
    - **Mesures consignées lors de la répétition isolée** :
      - Point de restauration pré-bascule (mode disque) : `20261122T125632Z`.
      - Durée mesurée de la fenêtre de pause (`cutover` : pause, drainage, sync delta finale, vérification SHA-256, redémarrage API en `s3`, vérifications avant réouverture, reprise) : `10339.88 ms` (~10,3 s).
      - Point de restauration post-bascule (mode S3) : `20261122T125649Z`.
      - Après réouverture : dépôt public & privé, suppression (`204` -> `403`), et sauvegarde chiffrée depuis S3 vers la destination EU indépendante réussis.
  - **Procédure opérateur** : `docs/MEDIA_MIGRATION.md`, `docs/DEPLOY.md`, `apps/api/docs/production.md` mis à jour (qualification `qualify-bucket`, sauvegarde EU indépendante, séquence Coolify avec `MEDIA_WRITES_PAUSED=true` pendant le redéploiement et vérifications avant réouverture, puis nouveau dépôt/suppression/sauvegarde après réouverture, et retour arrière avec réconciliation des créations/modifications/suppressions).
  - **Séparation répétition isolée vs production Coolify** :
    - **Répétition isolée** : terminée et validée ci-dessous.
    - **Activation réelle dans Coolify** : **non exécutée par l’agent** ; reste une action opérateur au moment du déploiement en production (qualification du bucket réel choisi avec `--require-https`, sauvegarde pré-bascule réelle, bascule des variables Coolify).
  - **Vérifications exécutées** :
    - `./infra/tests/s3-cutover-rehearsal.test.sh` : tous les contrôles réussis (`all passed`, 27/27).
    - `./infra/tests/media-transfer.test.sh` : 33/33 contrôles réussis (`all passed`).
    - `cd infra/media && python3 -B -m unittest discover -s . -p '*_test.py'` : 15/15 réussis.
    - `./infra/tests/deploy-docs.test.sh` : 5/5 réussis.
    - `./infra/tests/coolify-stack.test.sh static` : tous les contrôles statiques et CSP réussis.
    - `apps/api` : 514/514 unitaires réussis (`./mvnw -B -o -Daether.syncContext.named.factory=noop test -DargLine="-javaagent:...byte-buddy-agent-1.17.8.jar"`), et 9/9 intégration ciblées (`S3MediaControllerIntegrationTests` 5/5 avec MinIO isolé, `MediaWritePauseIntegrationTests` 3/3, `MediaWriteDrainIntegrationTests` 1/1).
