---
status: proposed
---

# Stockage portable des médias derrière l’API

MeetStudent conserve des clés de fichier indépendantes du fournisseur dans PostgreSQL. Une interface commune permet de sélectionner le disque ou un stockage compatible S3 par configuration. L’API conserve la maîtrise des URL et des droits d’accès, ce qui permet de déplacer les fichiers sans modifier leurs références dans le catalogue ou les comptes.

Le même adaptateur couvre AWS S3, Cloudflare R2 et Hetzner Object Storage. Changer de fournisseur exige de copier et de vérifier les fichiers avant de modifier la configuration. Le stockage des sauvegardes chiffrées reste indépendant du fournisseur du VPS et du fournisseur qui héberge les médias actifs. [Sauvegardes hors site](0004-encrypted-offsite-backups-r2-eu.md)
