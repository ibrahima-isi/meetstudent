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

- Le dump et l’inventaire partagent un instantané PostgreSQL. Le service vérifie les fichiers et les archives avant de publier le marqueur de fin.
- Les scénarios `scripts`, `roundtrip` et `roundtrip-s3` de `infra/tests/backup.test.sh` passent avec PostgreSQL 18 et MinIO isolés. Ils couvrent un envoi interrompu, un média absent, la restauration disque et S3, le format historique et les droits d’accès via l’API. Les contrôles CI, documentation et configuration Coolify passent aussi.
- Le guide définit le choix d’un fournisseur indépendant dans l’UE et la rétention de 30 jours. La configuration et la qualification du bucket de production restent à faire.
