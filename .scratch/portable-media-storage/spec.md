# Stockage portable des médias MeetStudent

Status: ready-for-agent

## Problem Statement

MeetStudent sert les images et les documents privés depuis les volumes du serveur. Pour déplacer ces fichiers chez un autre fournisseur, l’exploitant doit pouvoir changer la destination par configuration, préserver les accès aux fichiers existants et continuer à sauvegarder et restaurer ensemble PostgreSQL et les médias.

## Solution

Ajouter une interface de stockage indépendante du fournisseur pour le disque, les services compatibles S3 et Azure Blob. L’API continue de servir les URL publiques et de vérifier les droits d’accès aux documents privés. Le projet inclut le déplacement des fichiers existants et la bascule de production, avec une courte pause des modifications validée pour la synchronisation finale.

## User Stories

1. En tant que visiteur, je veux voir les images publiques après la bascule afin que les fiches restent consultables.
2. En tant que visiteur, je veux que les anciennes URL d’images ouvrent le bon fichier afin que les liens existants restent utilisables.
3. En tant qu’étudiant, je veux déposer un diplôme ou un bulletin afin de compléter mon dossier.
4. En tant qu’expert, je veux déposer une vidéo de présentation afin de compléter mon profil.
5. En tant que propriétaire, je veux télécharger mon document privé afin d’en récupérer le contenu.
6. En tant que propriétaire, je veux que l’accès à mes documents soit vérifié à chaque requête afin que seuls les utilisateurs autorisés y accèdent.
7. En tant qu’administrateur, je veux consulter les documents privés autorisés afin de poursuivre leur modération.
8. En tant qu’administrateur, je veux remplacer une image d’établissement afin que les visiteurs voient la nouvelle version.
9. En tant qu’utilisateur, je veux pouvoir retenter un dépôt interrompu afin de retrouver le même résultat sans créer de doublon.
10. En tant qu’utilisateur, je veux obtenir les octets demandés lorsqu’une lecture partielle est disponible afin d’éviter de télécharger le fichier entier.
11. En tant qu’utilisateur, je veux que les fichiers supprimés ne soient plus servis afin que le catalogue et les comptes reflètent la suppression.
12. En tant que développeur, je veux utiliser les volumes locaux afin de travailler sans compte cloud.
13. En tant qu’exploitant, je veux configurer AWS S3, R2 ou Hetzner avec le même adaptateur afin de choisir un fournisseur compatible S3.
14. En tant qu’exploitant, je veux pouvoir configurer Azure Blob afin de changer de protocole de stockage sans modifier le code métier.
15. En tant qu’exploitant, je veux qu’une configuration incomplète échoue au démarrage afin de détecter une destination mal configurée.
16. En tant qu’exploitant, je veux distinguer un fichier absent d’une panne du fournisseur afin de diagnostiquer l’incident.
17. En tant qu’exploitant, je veux inventorier et vérifier les fichiers avant la bascule afin de détecter les références manquantes ou les copies incomplètes.
18. En tant qu’utilisateur, je veux accéder au site pendant la copie initiale afin que la pause soit limitée à la synchronisation finale.
19. En tant qu’exploitant, je veux pouvoir interrompre la bascule si la fenêtre est dépassée afin de rouvrir les écritures sur le disque.
20. En tant qu’exploitant, je veux retrouver la base et les fichiers dans une sauvegarde chiffrée afin de pouvoir restaurer une instance cohérente.
21. En tant qu’exploitant, je veux pouvoir relire les anciennes archives afin que le changement de fournisseur préserve les points de restauration existants.
22. En tant qu’exploitant, je veux tester une restauration isolée avant la bascule afin de vérifier le chemin de reprise.
23. En tant qu’exploitant, je veux pouvoir revenir sur le disque ou migrer vers un autre fournisseur afin de récupérer les modifications intervenues depuis la bascule.
24. En tant qu’exploitant, je veux connaître le dernier point de restauration complet afin de savoir quelles écritures peuvent être perdues si le stockage actif devient inaccessible.

## Implementation Decisions

- Utiliser les clés existantes comme identifiants stables des fichiers dans PostgreSQL. L’interface couvre l’écriture, la lecture en flux avec métadonnées et plages d’octets, ainsi que la suppression idempotente.
- Sélectionner l’adaptateur par configuration de déploiement. Le disque est le mode par défaut. Le client S3 reçoit la région, le bucket et, si nécessaire, un endpoint et un mode d’adressage. Azure Blob utilise un adaptateur dédié.
- Servir les médias par l’API depuis des buckets et conteneurs privés. Appliquer les URL, règles d’autorisation et en-têtes existants. Le service métier gère la validation, les tentatives répétées, la modération et la suppression après validation de la transaction.
- Couvrir les anciens chemins publics encore servis. Régler les références historiques privées avant la bascule et empêcher leur réécriture au démarrage en mode objet.
- Sauvegarder le dump, l’inventaire des clés issu du même instantané PostgreSQL, les fichiers et leurs sommes de contrôle. Inclure les anciens fichiers publics servis, sérialiser les sauvegardes et publier uniquement les archives complètes. Retenter une sauvegarde incohérente et garder la dernière archive complète accessible.
- Conserver le chiffrement, la juridiction européenne et la rétention de 30 jours pour les sauvegardes. Leur fournisseur doit être indépendant du VPS et du stockage actif. Une bascule du stockage actif vers R2 impose une destination de sauvegarde indépendante et une lecture des archives R2 historiques jusqu’à leur échéance.
- Copier et vérifier les fichiers avant la pause. Pendant la pause, suspendre les opérations qui modifient les fichiers ou leurs références, attendre la fin des transactions et suppressions différées, puis réconcilier l’inventaire. Reprendre sur la source si la fenêtre est dépassée ou si les inventaires divergent.
- Tester les lectures publiques et privées sur la destination avant de rouvrir les écritures. Après leur réouverture, un retour au disque exige une synchronisation inverse des ajouts, modifications et suppressions. Si la destination est inaccessible, restaurer la dernière sauvegarde complète et expliciter la perte possible depuis ce point.

## Testing Decisions

- Vérifier d’abord les comportements par les routes HTTP existantes. Reprendre les tests d’intégration du contrôleur des médias pour les dépôts, lectures, refus, suppressions, en-têtes, plages d’octets et pannes. Exercer chaque adaptateur avec un stockage isolé et réserver les tests du contrat de stockage aux comportements que l’API ne permet pas d’observer.
- Reprendre les tests de sauvegarde et de restauration de l’infrastructure pour vérifier des archives anciennes et nouvelles, un fichier manquant et une sauvegarde interrompue. Restaurer ensemble PostgreSQL et les fichiers dans un environnement jetable.
- Répéter la copie, la pause, le retour arrière et la restauration avec le fournisseur de production choisi. Comparer clés, tailles et sommes de contrôle, puis vérifier les mêmes URL et autorisations sur l’instance restaurée.

## Out of Scope

- Envoi ou lecture directement depuis le navigateur vers le fournisseur, CDN dédié et URL présignées.
- Nouveaux types de fichiers ou changements de quotas, de modération et de visibilité.
- Réplication continue entre fournisseurs et garantie de reprise sans perte si le stockage actif devient inaccessible.

## Further Notes

Le fournisseur et la région européenne de production restent à choisir avant la création de la destination. La durée de la pause sera estimée pendant la répétition. La référence d’architecture est l’ADR proposé sur le stockage portable des médias. La décision de sauvegarde hors site reste applicable.
