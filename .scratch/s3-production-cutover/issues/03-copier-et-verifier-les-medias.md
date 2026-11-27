# 03: Copier et vérifier les médias en ligne

**What to build:** La destination S3 contient une première copie vérifiée des médias de production pendant que le site continue à servir les lectures et les écritures depuis le disque.

**Blocked by:** [s3-production-cutover/02](02-verifier-point-de-restauration.md).

**Status:** ready-for-agent

- [ ] Inventorier les médias référencés dans PostgreSQL et les anciens fichiers publics encore servis. Résoudre les clés invalides, les références privées historiques, les fichiers absents et les tailles incohérentes sur la source.
- [ ] Effectuer une copie initiale relançable vers le bucket qualifié pendant que le catalogue reste accessible et que les écritures restent ouvertes.
- [ ] Comparer les clés, les tailles et les sommes SHA-256. Consigner les résultats et résoudre les écarts constatés ou attribuer les changements concurrents au delta à vérifier pendant la pause.
