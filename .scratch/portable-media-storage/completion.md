# Suivi du stockage portable des médias

[Spécification](spec.md)

- [x] [portable-media-storage/01](issues/01-disk-through-media-storage.md) · Servir les médias sur disque via l’interface de stockage · resolved sur la branche d’intégration (`05d2e01`). Tests ciblés réussis (74 unitaires et 45 d’intégration). Suite API complète en cours.
- [ ] [portable-media-storage/02](issues/02-s3-compatible-media-storage.md) · Servir les médias depuis une destination compatible S3 · claimed. Prochaine étape : vérifier le contrat HTTP contre un bucket S3 isolé.
- [ ] [portable-media-storage/03](issues/03-s3-media-backup-restore.md) · Sauvegarder et restaurer PostgreSQL et les médias · ready-for-agent
- [ ] [portable-media-storage/04](issues/04-rehearse-media-transfer-rollback.md) · Répéter la copie des médias et le retour arrière · ready-for-agent
- [ ] [portable-media-storage/05](issues/05-production-s3-cutover.md) · Préparer la bascule des médias de production vers S3 · ready-for-agent
