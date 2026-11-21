# 04 · Répéter la copie des médias et le retour arrière

Blocked by: [portable-media-storage/02](02-s3-compatible-media-storage.md)
Status: ready-for-agent

## What to build

L’exploitant répète hors production le transfert disque vers S3 et le transfert entre fournisseurs compatibles S3, puis vérifie un retour au stockage source.

## Acceptance criteria

- [ ] L’inventaire inclut les clés en base et les anciens chemins publics servis. Les chemins privés historiques sont vérifiés avant la copie et le mode objet ne les réécrit pas.
- [ ] La copie initiale et la synchronisation finale sont répétables. Le contrôle compare clés, tailles et sommes de contrôle et signale les fichiers manquants ou divergents.
- [ ] La répétition couvre la pause des opérations qui modifient les médias ou leurs références et le drainage des requêtes, transactions et suppressions différées. Elle mesure la fenêtre et vérifie l’abandon de la bascule en cas de divergence ou de dépassement.
- [ ] Le retour arrière exercé après reprise des écritures réconcilie les ajouts, modifications et suppressions. La procédure décrit la reprise depuis la dernière archive complète si le fournisseur est indisponible.
