# 04: Basculer et valider la production

**What to build:** MeetStudent sert les médias depuis le bucket S3 privé avec les mêmes URL et autorisations. L’exploitant dispose des preuves de reprise et d’un chemin de retour au disque.

**Blocked by:** [s3-production-cutover/03](03-copier-et-verifier-les-medias.md).

**Status:** ready-for-agent

- [ ] Obtenir l’accord explicite de l’exploitant sur l’activation, la fenêtre de maintenance, la durée maximale de pause et les critères d’abandon. Confirmer que le point de restauration vérifié est disponible.
- [ ] Mettre les écritures en pause et attendre le drainage des requêtes, transactions PostgreSQL et suppressions différées. Vérifier le 503 et l’en-tête Retry-After, synchroniser le delta et confirmer un inventaire sans écart par clé, taille et SHA-256.
- [ ] Activer S3 dans Coolify avec les écritures encore pausées. Vérifier les lectures publiques et historiques, les plages en 206, la lecture privée par le propriétaire et l’administrateur, les refus 401 et 403 et le refus d’accès direct au bucket.
- [ ] En cas d’échec avant réouverture, revenir au disque, vérifier les lectures puis rouvrir les écritures. Après réouverture sur S3, tout retour au disque passe par une synchronisation inverse vérifiée. Si S3 est inaccessible, utiliser la dernière archive complète et identifier la période de perte possible.
- [ ] Après réouverture, vérifier un dépôt et une suppression avec des médias de test. Produire une sauvegarde chiffrée complète et consigner son identifiant, les résultats et la durée réelle de pause. Conserver les volumes source pour le retour arrière.
