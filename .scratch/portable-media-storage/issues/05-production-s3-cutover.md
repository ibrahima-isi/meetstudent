# 05 · Basculer les médias de production vers S3

Blocked by: [portable-media-storage/03](03-s3-media-backup-restore.md), [portable-media-storage/04](04-rehearse-media-transfer-rollback.md)
Status: ready-for-agent

## What to build

L’exploitant migre la production vers un bucket privé compatible S3, avec une courte pause des modifications et des vérifications avant de rouvrir les écritures.

## Acceptance criteria

- [ ] Le fournisseur compatible S3 et la région européenne sont sélectionnés. La destination et la sauvegarde indépendante sont qualifiées avant la fenêtre de bascule.
- [ ] La répétition sur une destination isolée du fournisseur choisi vérifie le transfert, la restauration et la reprise. Les résultats fixent la durée de la fenêtre de bascule.
- [ ] Après sauvegarde, copie initiale et drainage des écritures, l’inventaire final concorde avant activation dans Coolify. Les lectures publiques et privées et les refus d’accès sont vérifiés avant réouverture.
- [ ] Après réouverture, un dépôt, une suppression et une sauvegarde depuis S3 sont vérifiés. Le retour arrière conserve un accès aux volumes source et à la dernière archive complète.
- [ ] Le suivi enregistre le fournisseur, la durée réelle, les résultats et le point de restauration disponible.
