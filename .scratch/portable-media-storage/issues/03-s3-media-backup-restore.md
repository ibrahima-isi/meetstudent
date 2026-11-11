# 03 · Sauvegarder et restaurer PostgreSQL et les médias

Blocked by: [portable-media-storage/02](02-s3-compatible-media-storage.md)
Status: ready-for-agent

## What to build

L’exploitant peut produire une archive chiffrée avec la base et les fichiers, puis restaurer ensemble les deux dans un environnement isolé, en mode disque ou S3.

## Acceptance criteria

- [ ] Le dump et l’inventaire des clés partagent un instantané PostgreSQL. L’archive couvre les fichiers référencés et les anciens fichiers publics servis.
- [ ] Le job vérifie les sommes de contrôle et la complétude, sérialise les exécutions et retente une sauvegarde incohérente. Une exécution incomplète laisse la dernière archive complète utilisable.
- [ ] Les archives historiques restent restaurables. Un test restaure la base et les fichiers vers une destination vide et vérifie les URL et droits d’accès.
- [ ] La sauvegarde chiffrée conserve une rétention de 30 jours en Europe chez un fournisseur indépendant du VPS et du stockage actif.
