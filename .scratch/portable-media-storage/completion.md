# Suivi du stockage portable des médias

[Spécification](spec.md)

- [x] [portable-media-storage/01](issues/01-disk-through-media-storage.md) · Servir les médias sur disque via l’interface de stockage · Intégré à `main` via les PR [#279](https://github.com/ibrahima-isi/meetstudent/pull/279) et [#288](https://github.com/ibrahima-isi/meetstudent/pull/288). Tests ciblés réussis (74 unitaires et 45 d’intégration). [CI API sur `main`](https://github.com/ibrahima-isi/meetstudent/actions/runs/44932867333) réussie avec PostgreSQL.
- [x] [portable-media-storage/02](issues/02-s3-compatible-media-storage.md) · Servir les médias depuis une destination compatible S3 · intégré à la branche commune (`93340fe`). Contrat HTTP vérifié avec MinIO. Suite API réussie (503 unitaires, 849 d’intégration avec PostgreSQL 18).
- [ ] [portable-media-storage/03](issues/03-s3-media-backup-restore.md) · Sauvegarder et restaurer PostgreSQL et les médias · ready-for-agent
- [x] [portable-media-storage/04](issues/04-rehearse-media-transfer-rollback.md) · Répéter la copie des médias et le retour arrière · `resolved` sur `feature/portable-media-storage-04`. Pause écritures/références (`503` + `Retry-After`) + arrêt gracieux + drainage API/PG/post-commit dans `apps/api`, CLI `infra/media/media_transfer.py`, procédure `docs/MEDIA_MIGRATION.md`, répétition Disk->S3, S3->S3, abandon divergence/dépassement et retour arrière validés (`infra/tests/media-transfer.test.sh` 33/33 avec PG 18 & deux MinIO, `media_transfer_test.py` 8/8, `S3MediaControllerIntegrationTests` 5/5 avec MinIO, 510 unitaires API, `deploy-docs.test.sh` & `coolify-stack.test.sh static`).
- [ ] [portable-media-storage/05](issues/05-production-s3-cutover.md) · Préparer la bascule des médias de production vers S3 · ready-for-agent
