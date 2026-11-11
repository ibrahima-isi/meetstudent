# 01 · Servir les médias sur disque via l’interface de stockage

Blocked by: Aucun
Status: ready-for-agent

## What to build

Les utilisateurs peuvent déposer, consulter, remplacer et supprimer des médias sur les volumes existants. Les routes HTTP exercent l’interface commune avec le stockage disque par défaut.

## Acceptance criteria

- [ ] Les images publiques, les anciennes URL servies et les documents privés existants restent lisibles depuis le disque.
- [ ] Les dépôts, tentatives répétées, remplacements et suppressions passent par le parcours HTTP existant. La suppression du fichier suit la validation de la transaction.
- [ ] Les lectures privées vérifient le propriétaire ou le rôle administrateur. Les tests d’intégration couvrent les accès refusés, les en-têtes et les plages d’octets prises en charge.
- [ ] Le dépôt démarre en mode disque sans identifiants cloud.
