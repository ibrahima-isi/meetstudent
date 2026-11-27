# 02: Vérifier le point de restauration pré-bascule

**What to build:** L’exploitant peut récupérer ensemble PostgreSQL et les médias depuis une sauvegarde de production récente, testée dans une stack isolée.

**Blocked by:** [s3-production-cutover/01](01-qualifier-production-et-bucket.md).

**Status:** ready-for-agent

- [ ] Confirmer le chiffrement, la juridiction UE, la rétention de 30 jours et l’indépendance du fournisseur de sauvegarde vis-à-vis du VPS et du bucket média qualifié. Si les médias passent sur R2, vérifier la destination indépendante et l’accès aux anciennes archives jusqu’à leur échéance.
- [ ] Produire une sauvegarde fraîche de PostgreSQL et des médias. Vérifier le marqueur de complétude et consigner l’identifiant de l’archive.
- [ ] Restaurer cette archive dans une stack isolée. Vérifier le catalogue, les octets d’un média public et d’un document privé et le contrôle d’accès à ce dernier. Consigner les résultats sans joindre de données privées.
