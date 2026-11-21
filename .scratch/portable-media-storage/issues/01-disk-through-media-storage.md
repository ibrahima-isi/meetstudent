# 01 · Servir les médias sur disque via l’interface de stockage

Blocked by: Aucun
Status: resolved

## What to build

Les utilisateurs peuvent déposer, consulter, remplacer et supprimer des médias sur les volumes existants. Les routes HTTP exercent l’interface commune avec le stockage disque par défaut.

## Acceptance criteria

- [x] Les images publiques, les anciennes URL servies et les documents privés existants restent lisibles depuis le disque.
- [x] Les dépôts, tentatives répétées, remplacements et suppressions passent par le parcours HTTP existant. La suppression du fichier suit la validation de la transaction.
- [x] Les lectures privées vérifient le propriétaire ou le rôle administrateur. Les tests d’intégration couvrent les accès refusés, les en-têtes et les plages d’octets prises en charge.
- [x] Le dépôt démarre en mode disque sans identifiants cloud.

## Comments

- Adaptateur disque et parcours HTTP intégrés dans `05d2e01`. Les tests ciblés ont réussi (74 unitaires et 45 d’intégration). La suite API complète est en cours.
