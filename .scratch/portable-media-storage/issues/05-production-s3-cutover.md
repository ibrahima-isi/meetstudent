# 05 · Préparer la bascule des médias de production vers S3

Blocked by: [portable-media-storage/03](03-s3-media-backup-restore.md), [portable-media-storage/04](04-rehearse-media-transfer-rollback.md)
Status: ready-for-agent

## What to build

L’exploitant dispose d’une procédure répétée et d’un outillage pour migrer la production vers le bucket privé compatible S3 de son choix. La procédure décrit la courte pause des modifications et les vérifications avant réouverture des écritures.

## Acceptance criteria

- [ ] L’endpoint, le bucket, la région de signature et le mode d’adressage se configurent au déploiement. La préparation fonctionne avec des buckets privés compatibles S3 à des endpoints différents.
- [ ] Une répétition avec une destination S3 isolée couvre la sauvegarde, la copie initiale, le drainage des écritures, la validation de l’inventaire final et l’activation. Elle consigne la durée de la pause et le point de restauration.
- [ ] Avant la réouverture, la répétition vérifie les lectures publiques et privées et les refus d’accès. Après réouverture, elle vérifie un dépôt, une suppression et une sauvegarde depuis S3.
- [ ] La procédure décrit la qualification du bucket de l’exploitant, la sauvegarde indépendante et le retour au stockage source avec les fichiers créés ou supprimés après activation.
- [ ] Le suivi distingue la répétition isolée et l’activation réelle dans Coolify.
