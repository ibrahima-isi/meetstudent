# Basculer les médias de production de MeetStudent vers S3

Status: ready-for-agent

## Problème

L’exploitant doit passer des volumes disque à un bucket privé compatible S3 tout en conservant les URL du catalogue et les droits d’accès aux documents privés. L’outillage et le retour arrière ont été validés en répétition isolée. Le bucket réel, la restauration d’une sauvegarde de production et la fenêtre de maintenance restent à valider.

## Solution

Organiser une bascule opérateur dans Coolify avec les outils existants. Qualifier les destinations, vérifier un point de restauration et faire la copie initiale en ligne. Réserver la pause d’écriture au drainage, à la synchronisation finale et aux vérifications avant réouverture. Consigner les résultats et garder un chemin de retour au disque.

## Récits utilisateurs

1. En tant qu’exploitant, je veux confirmer le SHA et le mode de stockage réellement en service, afin de partir d’une configuration connue.
2. En tant qu’exploitant, je veux qualifier le bucket privé de mon choix en HTTPS, afin de vérifier ses droits et sa compatibilité S3 avant toute copie.
3. En tant qu’étudiant, je veux que mon document soit inaccessible directement depuis le bucket, afin que MeetStudent applique mes droits d’accès.
4. En tant qu’exploitant, je veux vérifier une destination chiffrée dans l’UE, indépendante du VPS et du stockage des médias, afin de récupérer mes données si l’un des deux est indisponible.
5. En tant qu’exploitant, je veux contrôler la rétention de 30 jours et l’accès aux archives existantes, afin de conserver les points de restauration pendant la transition.
6. En tant qu’exploitant, je veux restaurer une sauvegarde fraîche dans une stack isolée, afin de partir d’un point de restauration vérifié.
7. En tant qu’exploitant, je veux repérer les clés invalides et les fichiers manquants, afin de résoudre les écarts pendant que le disque est encore actif.
8. En tant que visiteur, je veux consulter le catalogue pendant la copie initiale, afin que la préparation reste transparente.
9. En tant qu’exploitant, je veux comparer clés, tailles et sommes SHA-256, afin de détecter toute différence de contenu avant l’activation.
10. En tant qu’administrateur, je veux un délai de réessai explicite pendant la pause, afin de savoir quand retenter une écriture sur un média.
11. En tant qu’exploitant, je veux attendre la fin des écritures et suppressions différées, afin de synchroniser un état stable.
12. En tant qu’exploitant, je veux pouvoir abandonner si le délai convenu est dépassé ou si les inventaires divergent, afin de reprendre sur le disque.
13. En tant que visiteur, je veux retrouver les images via les mêmes URL, y compris les anciens liens, afin de continuer à consulter le catalogue.
14. En tant que propriétaire d’un document, je veux le relire après la bascule, afin de conserver l’accès à mon dossier.
15. En tant qu’administrateur, je veux ouvrir un PDF privé depuis le backoffice, afin de confirmer la modération avec la politique de sécurité de production.
16. En tant qu’exploitant, je veux vérifier un dépôt et une suppression après réouverture, afin de confirmer que S3 accepte les écritures.
17. En tant qu’exploitant, je veux enregistrer un point de restauration après la bascule, afin de vérifier que les sauvegardes couvrent la nouvelle destination.
18. En tant qu’exploitant, je veux réconcilier les ajouts, modifications et suppressions avant un retour au disque, afin de conserver le travail réalisé depuis la réouverture.
19. En tant qu’exploitant, je veux connaître la date de la dernière archive complète si S3 devient inaccessible, afin d’identifier la période de perte de données possible.

## Décisions de mise en œuvre

- Réutiliser les outils livrés et le runbook existant. Les clés PostgreSQL, les URL MeetStudent et les autorisations restent les mêmes.
- L’exploitant renseigne le bucket, la région de signature, l’endpoint le cas échéant et le mode d’adressage. Les identifiants passent par le mécanisme de secrets existant.
- La sauvegarde hors site reste chiffrée, dans l’UE, chez un fournisseur indépendant du VPS et des médias actifs, avec une rétention de 30 jours. Si les médias actifs passent sur R2, prévoir un autre fournisseur de sauvegarde et garder l’accès aux anciennes archives jusqu’à leur échéance.
- Consigner le point de restauration pré-bascule et sa restauration isolée. Vérifier l’inventaire des médias référencés dans PostgreSQL et des anciens fichiers publics servis. Résoudre les écarts avant la copie initiale et comparer le contenu par clé, taille et SHA-256.
- L’exploitant fixe la fenêtre de maintenance, la durée maximale de pause et les critères d’abandon. Pendant la pause, drainer les écritures HTTP, les transactions PostgreSQL et les suppressions différées. Synchroniser le delta et exiger un inventaire sans écart.
- Activer S3 dans Coolify avec les écritures encore en pause. Si la vérification échoue ou si la pause dépasse la durée convenue, remettre le disque en service avant de rouvrir les écritures. Après réouverture, tout retour au disque passe par une synchronisation inverse vérifiée.
- Après reprise, vérifier les écritures et consigner une nouvelle sauvegarde complète. Conserver les volumes source pour un éventuel retour arrière. Si le bucket est inaccessible, restaurer la base et les médias depuis la dernière archive complète.

## Décisions de vérification

- **Coolify et HTTPS** — vérifier le SHA actif et la santé des services, puis ouvrir le site, le backoffice et le catalogue. Confirmer l’ouverture d’un PDF privé avec un compte administrateur et la politique de sécurité en vigueur.
- **Bucket et intégrité** — utiliser le contrôle de qualification existant pour vérifier lecture, écriture, suppression, accès anonyme refusé, clé absente (404) et plage d’octets (206). Vérifier la destination de sauvegarde et comparer clés, tailles et SHA-256 avant la bascule.
- **Restauration isolée** — restaurer la sauvegarde pré-bascule et vérifier ensemble le catalogue, un média public et un document privé. Consigner l’identifiant de l’archive et les résultats.
- **API MeetStudent** — pendant la pause, contrôler le refus d’écriture (503 avec Retry-After), les lectures publiques et les anciennes URL, les plages d’octets, la lecture privée par le propriétaire et l’administrateur, le refus anonyme (401) et le refus au non-propriétaire (403). Vérifier aussi le refus d’accès direct au bucket. Après reprise, tester un dépôt et une suppression sur des médias de test, puis produire une nouvelle sauvegarde complète.
- Réutiliser les points de contrôle externes de la répétition isolée. Consigner la durée de pause et les résultats sans exposer d’identifiants ni de documents privés.

## Hors périmètre

- Choisir un fournisseur à la place de l’exploitant ou créer un compte chez un fournisseur.
- Modifier le schéma, l’interface ou les outils de migration.
- Supprimer les volumes source ou les anciennes archives.
- Exécuter la bascule en publiant cette spec. L’exploitant autorise et planifie l’activation réelle séparément.

## Notes

- Le workflow du commit 349c9a9 a publié les quatre images et Coolify a accepté le webhook. Les contrôles publics du 27 novembre 2026 ont confirmé le web, le backoffice et l’API en HTTPS, avec 99 écoles dans le catalogue et un refus à la route privée sans authentification.
- L’accès SSH à Coolify a expiré. Le SHA actif, la lecture privée autorisée et l’état des sauvegardes de production restent à confirmer. La répétition isolée est validée. Sa durée ne fixe pas la durée de pause en production.
- Les paramètres du bucket et la fenêtre de maintenance restent à renseigner avant exécution.
- [Runbook de bascule et de retour arrière](../../docs/MEDIA_MIGRATION.md)
- [Suivi du stockage portable des médias](../portable-media-storage/completion.md)
- [Déploiement GitHub de 349c9a9](https://github.com/ibrahima-isi/meetstudent/actions/runs/45911712600/job/140222304988)
