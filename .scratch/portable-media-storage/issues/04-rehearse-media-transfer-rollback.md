# 04 · Répéter la copie des médias et le retour arrière

Blocked by: [portable-media-storage/02](02-s3-compatible-media-storage.md)
Status: resolved

## What to build

L’exploitant répète hors production le transfert disque vers S3 et le transfert entre fournisseurs compatibles S3, puis vérifie un retour au stockage source.

## Acceptance criteria

- [x] L’inventaire inclut les clés en base et les anciens chemins publics servis. Les chemins privés historiques sont vérifiés avant la copie et le mode objet ne les réécrit pas.
- [x] La copie initiale et la synchronisation finale sont répétables. Le contrôle compare clés, tailles et sommes de contrôle et signale les fichiers manquants ou divergents.
- [x] La répétition couvre la pause des opérations qui modifient les médias ou leurs références et le drainage des requêtes, transactions et suppressions différées. Elle mesure la fenêtre et vérifie l’abandon de la bascule en cas de divergence ou de dépassement.
- [x] Le retour arrière exercé après reprise des écritures réconcilie les ajouts, modifications et suppressions. La procédure décrit la reprise depuis la dernière archive complète si le fournisseur est indisponible.

## Comments

- Implémenté sur `feature/portable-media-storage-04` :
  - API (`apps/api`) : `MediaWriteGate`, `MediaWritesPausedException` (`503` + `Retry-After`), `AdminMediaMaintenanceController` (`/api/v1/admin/media/maintenance{,/pause,/resume}`), protection des écritures médias et références (médias, écoles, programmes, cours, suppression de compte) tout en conservant les lectures publiques/privées et contrôles d’accès (`401`/`403`), arrêt gracieux (`server.shutdown: graceful`, `30s`, `stop_grace_period: 35s`), suivi des suppressions différées `afterCommit`/`afterCompletion`, et test confirmant la désactivation de `MediaMigrationRunner` en mode `s3`.
  - Outillage & procédure : `infra/media/media_transfer.py` (`precheck`, `inventory`, `sync`, `verify`, `pause-drain`, `resume`, `cutover`, `verify-api`), tests unitaires (`infra/media/media_transfer_test.py`), répétition complète isolée (`infra/tests/media-transfer.test.sh` avec PostgreSQL 18, deux instances MinIO et `meetstudent-api`), et procédure `docs/MEDIA_MIGRATION.md` (y compris reprise depuis la dernière archive complète en cas d’indisponibilité S3).
  - Vérifications :
    - `apps/api`: `./mvnw --batch-mode clean verify` (sandboxé, sans `MIGRATION_PG_URL`/`MEDIA_S3_TEST_ENDPOINT`) : 510/510 unitaires réussis ; intégration 834/854 réussis, 14 ignorés (PG/MinIO non configurés), 6 erreurs réseau sandbox sur `localhost` (`ContactTrustedProxyIntegrationTests` x3, `ErrorDispatchIntegrationTests` x3).
    - `S3MediaControllerIntegrationTests` avec MinIO isolé : 5/5 réussis ; `MediaWritePauseIntegrationTests` (3/3) et `MediaWriteDrainIntegrationTests` (1/1) réussis.
    - `python3 -B -m unittest infra/media/media_transfer_test.py` : 8/8 réussis.
    - `./infra/tests/media-transfer.test.sh` (PostgreSQL 18 + MinIO A & B + `meetstudent-api`) : 33/33 contrôles réussis (`all passed`).
    - `./infra/tests/deploy-docs.test.sh` (5/5) et `./infra/tests/coolify-stack.test.sh static` : tous réussis.
