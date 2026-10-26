# Sauvegardes hors site chiffrées sur Cloudflare R2, juridiction EU

Les sauvegardes contiennent des données personnelles d'étudiants (diplômes, relevés, bulletins) en plus de la base. Chaque nuit, un service `backup` dédié de la stack Coolify produit un dump Postgres et une archive des deux volumes d'upload, les chiffre avec `rclone crypt` et les envoie dans un bucket Cloudflare R2 créé en **juridiction EU**, où une règle de cycle de vie les supprime après 30 jours. La clé de chiffrement est conservée hors du VPS (gestionnaire de mots de passe du propriétaire) : sans elle, une sauvegarde est irrécupérable.

Le stockage est volontairement chez un autre fournisseur que le VPS, pour qu'un incident Hetzner (compte, région, facturation) n'emporte pas la production et ses sauvegardes en même temps. La juridiction EU et le chiffrement côté client viennent du RGPD : le fournisseur ne voit que des données chiffrées, stockées dans l'UE.

## Considered Options

- **Hetzner Object Storage / Storage Box** : même fournisseur que le VPS, rejeté pour la raison ci-dessus.
- **Sauvegardes Postgres intégrées de Coolify** : ne couvrent pas les fichiers uploadés, rejetées.
- **Cron sur l'hôte avec `scripts/backup.sh`** : écarté au profit d'une tâche planifiée Coolify qui exécute le script dans le service `backup`, pour que tout le cycle de vie reste visible dans Coolify.
