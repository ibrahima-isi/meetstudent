# 01: Confirmer la production et qualifier le bucket S3

**What to build:** L’exploitant dispose d’un état de référence vérifié pour le déploiement Coolify et d’un bucket média privé qualifié avant toute copie.

**Blocked by:** Aucun.

**Status:** ready-for-agent

- [ ] Confirmer dans Coolify le SHA et la santé des conteneurs actifs ainsi que le stockage disque. Vérifier le site, le backoffice et le catalogue en HTTPS.
- [ ] Ouvrir un PDF privé via le backoffice avec un compte administrateur. Consigner le résultat sans joindre le document.
- [ ] Vérifier l’endpoint HTTPS, la région de signature et le mode d’adressage du bucket choisi. Les identifiants restent dans le mécanisme de secrets existant.
- [ ] Le contrôle de qualification confirme la lecture, l’écriture, la suppression, une clé absente en 404, une plage en 206 et le refus des lectures et du listing anonymes. Les objets de test sont supprimés.
