# 04: Basculer et valider la production

**What to build:** MeetStudent sert les médias depuis le bucket S3 privé avec les mêmes URL et autorisations. L’exploitant dispose des preuves de reprise et d’un chemin de retour au disque.

**Blocked by:** [s3-production-cutover/03](03-copier-et-verifier-les-medias.md).

**Status:** ready-for-agent

- [ ] Obtenir l’accord explicite de l’exploitant sur l’activation, la fenêtre de maintenance, la durée maximale de pause et les critères d’abandon. Confirmer que le point de restauration vérifié est disponible.
- [ ] Geler les fusions et les pushes vers `main`. Attendre la fin des workflows déjà lancés sur `main` et des déploiements Coolify en cours ou en file. Réserver les redéploiements à l’exploitant chargé de la bascule jusqu’à la validation de la reprise.
- [ ] Lancer `pause-drain` sur l’API disque. Conserver `MEDIA_STORAGE_BACKEND=disk`, enregistrer `MEDIA_WRITES_PAUSED=true` dans Coolify et redéployer. Vérifier la valeur dans chaque conteneur `api` actif et confirmer le `503` avec `Retry-After`. Relancer `pause-drain` et attendre la fin des requêtes d’écriture, des transactions PostgreSQL et des suppressions différées.
- [ ] Confirmer que `sync --delete` cible le bucket qualifié au ticket 01 et que les préfixes sont toujours réservés à cette migration. Synchroniser le delta, puis exiger un inventaire sans écart par clé, taille et SHA-256.
- [ ] Activer S3 dans Coolify en conservant `MEDIA_WRITES_PAUSED=true`. Dans les conteneurs `api` et `backup` actifs, vérifier `MEDIA_STORAGE_BACKEND=s3` et comparer le bucket, l’endpoint, la région et le mode d’adressage au relevé de qualification.
- [ ] Exécuter `verify-api --expect-writes-paused` avec le bucket qualifié comme source. Vérifier les lectures publiques et historiques, les plages en `206`, les lectures privées du propriétaire et de l’administrateur, les refus `401` et `403` et le refus d’accès direct au bucket. Après validation de tous les contrôles et dans le délai convenu, enregistrer `MEDIA_WRITES_PAUSED=false` dans Coolify et rouvrir les écritures.
- [ ] Si un contrôle échoue, si la pause ne peut plus être confirmée ou si le délai est dépassé avant réouverture, rétablir le mode disque avec `MEDIA_WRITES_PAUSED=true`. Vérifier le stockage actif et les lectures, puis enregistrer `MEDIA_WRITES_PAUSED=false` et rouvrir les écritures sur disque. Après réouverture sur S3, tout retour au disque passe par une synchronisation inverse vérifiée. Si S3 est inaccessible, utiliser la dernière archive complète et identifier la période de perte possible.
- [ ] Après réouverture, vérifier un dépôt et une suppression avec des médias de test. Produire une sauvegarde chiffrée complète et consigner son identifiant, les résultats et la durée réelle de pause. Conserver les volumes source pour le retour arrière.
