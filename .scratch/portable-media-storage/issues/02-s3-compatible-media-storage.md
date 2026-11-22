# 02 · Servir les médias depuis une destination compatible S3

Blocked by: [portable-media-storage/01](01-disk-through-media-storage.md)
Status: resolved

## What to build

L’exploitant active un bucket privé par configuration. Le web et le back-office utilisent les mêmes URL pour déposer et lire les médias.

## Acceptance criteria

- [x] AWS S3, Cloudflare R2 et Hetzner utilisent un adaptateur commun configurable par bucket, région, endpoint et mode d’adressage selon le fournisseur.
- [x] Une configuration incomplète échoue au démarrage. Les erreurs distinguent un objet absent d’une indisponibilité et préservent la confidentialité des identifiants.
- [x] Un service S3 isolé vérifie les parcours HTTP de dépôt, lecture publique et privée, lecture partielle, remplacement et suppression. Une lecture partielle valide ne transfère que les octets demandés.
- [x] Les objets restent privés côté fournisseur. Les URL, les contrôles d’accès et les en-têtes sont vérifiés via MeetStudent.

## Comments

- Adaptateur S3 derrière les URL existantes, avec configuration d’AWS S3, R2 et Hetzner sous [Configuration du stockage](../../../apps/api/docs/production.md#media-storage).
- `./mvnw --batch-mode clean verify` a réussi avec MinIO et PostgreSQL 18 isolés. Le bilan est de 503 tests unitaires et 849 tests d’intégration, sans test ignoré. Les tests HTTP couvrent la lecture partielle, le refus d’accès direct au bucket, le remplacement, la suppression et les réponses 404 et 503.
- Le contrat S3 a été vérifié sur MinIO. La qualification d’un bucket cloud réel relève de la répétition du ticket 04.
