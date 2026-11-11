# 02 · Servir les médias depuis une destination compatible S3

Blocked by: [portable-media-storage/01](01-disk-through-media-storage.md)
Status: ready-for-agent

## What to build

L’exploitant active un bucket privé par configuration. Le web et le back-office utilisent les mêmes URL pour déposer et lire les médias.

## Acceptance criteria

- [ ] AWS S3, Cloudflare R2 et Hetzner utilisent un adaptateur commun configurable par bucket, région, endpoint et mode d’adressage selon le fournisseur.
- [ ] Une configuration incomplète échoue au démarrage. Les erreurs distinguent un objet absent d’une indisponibilité et préservent la confidentialité des identifiants.
- [ ] Un service S3 isolé vérifie les parcours HTTP de dépôt, lecture publique et privée, lecture partielle, remplacement et suppression. Une lecture partielle valide ne transfère que les octets demandés.
- [ ] Les objets restent privés côté fournisseur. Les URL, les contrôles d’accès et les en-têtes sont vérifiés via MeetStudent.
