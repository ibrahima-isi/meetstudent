# Spec : coolify-deploy

Status: ready-for-agent

Branche cible : `ci/coolify-deploy` (PR vers `dev`). Décisions : [ADR 0003](../../docs/adr/0003-coolify-raw-compose-ghcr.md), [ADR 0004](../../docs/adr/0004-encrypted-offsite-backups-r2-eu.md) ; déroulé opérationnel : [docs/DEPLOY.md §9](../../docs/DEPLOY.md) et [feuille de route](../../docs/deploy/coolify-roadmap.html). Ne dépend pas de `fix/hash-refresh-tokens` ni de la déconnexion côté serveur.

## Problem Statement

MeetStudent n'a pas de livraison continue. La CI construit et teste les trois applications, puis s'arrête : aucune image n'est publiée, et mettre la production à jour veut dire se connecter au VPS, récupérer le code et reconstruire les images sur la machine qui sert le trafic. La stack de production (`compose.prod.yml`) embarque son propre Traefik, ce qui la rend incompatible avec Coolify, la plateforme retenue pour piloter le VPS Hetzner : Coolify fournit déjà son propre Traefik. Les sauvegardes, enfin, restent sur le VPS : si on perd le serveur, on perd aussi les documents personnels des étudiants (diplômes, relevés). Le mémoire ne peut pas présenter une chaîne CI/CD complète tant que ce maillon manque.

## Solution

Quand `dev` est promu sur `main`, la CI publie les images de l'API, du web et du back-office sur GHCR (une par commit, plus `latest`), puis demande à Coolify de les déployer. Sur le VPS, Coolify fait tourner une stack dédiée qui garde toutes les protections déjà validées : routage single-origin, HTTPS, en-têtes de sécurité, limites de débit, limite d'upload. Chaque nuit, un service de sauvegarde de cette stack exporte la base et les fichiers uploadés, les chiffre et les envoie hors du VPS, dans un stockage S3 situé dans l'UE. Une restauration redonne une stack fonctionnelle avec les mêmes données. Si une version pose problème, le propriétaire revient à une image précédente sans rien reconstruire.

## User Stories

1. En tant que propriétaire, je veux que promouvoir `dev` sur `main` suffise à mettre la production à jour, sans me connecter au VPS.
2. En tant que propriétaire, je veux que le déploiement ne parte que si les trois jobs de tests sont verts, pour ne jamais déployer une version cassée.
3. En tant que propriétaire, je veux que le VPS ne compile jamais rien, pour que les visiteurs ne subissent pas un build Maven ou Angular.
4. En tant que propriétaire, je veux une image par commit, pour savoir exactement quel code tourne en production.
5. En tant que propriétaire, je veux revenir à une version précédente en changeant une seule variable dans Coolify, pour corriger un incident en quelques minutes.
6. En tant que propriétaire, je veux qu'une PR prouve que les trois images se construisent, pour ne pas découvrir un Dockerfile cassé le jour de la promotion.
7. En tant que propriétaire, je veux qu'une PR valide le fichier compose de Coolify, pour qu'une faute de frappe ne bloque pas un déploiement.
8. En tant que propriétaire, je veux qu'aucun secret ne figure dans les images ni dans le dépôt, puisque les images sont publiques.
9. En tant que propriétaire, je veux que seule la CI de `main` puisse déclencher un déploiement, pour qu'une PR ou une branche ne touche jamais la production.
10. En tant que propriétaire, je veux que Coolify ne redéploie pas tout seul à chaque push Git, pour qu'il ne tire jamais des images qui n'existent pas encore.
11. En tant que visiteur, je veux que le site, l'API et les médias publics répondent sur le même domaine en HTTPS, comme aujourd'hui.
12. En tant que visiteur, je veux être redirigé de HTTP vers HTTPS.
13. En tant qu'administrateur, je veux que le back-office soit servi sur son propre domaine en HTTPS.
14. En tant que propriétaire, je veux garder la limite stricte sur les routes d'authentification, pour freiner les tentatives de connexion en force brute.
15. En tant que propriétaire, je veux garder la limite générale sur le reste de l'API.
16. En tant que propriétaire, je veux qu'un upload de plus de 10 Mo soit refusé avant d'atteindre l'API.
17. En tant que propriétaire, je veux garder les en-têtes de sécurité (HSTS, nosniff, interdiction d'affichage dans une iframe, referrer et permissions policy) sur chaque réponse.
18. En tant que propriétaire, je veux qu'un en-tête `Forwarded` envoyé par un client soit retiré avant l'API, pour que l'adresse IP utilisée par les limites de débit ne soit pas falsifiable.
19. En tant que propriétaire, je veux que ces protections restent actives même quand un conteneur de l'application redémarre.
20. En tant que propriétaire, je veux que Postgres reste injoignable depuis l'extérieur et depuis le réseau du proxy.
21. En tant que visiteur, je veux que les pages rendues côté serveur affichent de vraies données : le SSR doit joindre l'API par le réseau interne.
22. En tant que propriétaire, je veux une sauvegarde automatique chaque nuit, sans cron à gérer à la main sur l'hôte.
23. En tant que propriétaire, je veux que chaque sauvegarde contienne la base ET les deux volumes de fichiers, pour qu'une restauration soit complète.
24. En tant qu'étudiant, je veux que mes documents soient chiffrés avant de quitter le serveur, pour que le fournisseur de stockage ne puisse pas les lire.
25. En tant qu'étudiant, je veux que mes documents restent stockés dans l'UE.
26. En tant que propriétaire, je veux que les sauvegardes soient supprimées automatiquement après 30 jours.
27. En tant que propriétaire, je veux être prévenu par Coolify quand une sauvegarde échoue, pour ne pas découvrir le problème le jour où j'en ai besoin.
28. En tant que propriétaire, je veux restaurer une sauvegarde dans une stack vide et retrouver comptes, contenus et documents.
29. En tant que propriétaire, je veux que le service de sauvegarde lise les fichiers uploadés sans pouvoir les modifier.
30. En tant que propriétaire, je veux garder `compose.prod.yml` comme voie de déploiement sans Coolify.
31. En tant que futur mainteneur, je veux une section Coolify dans le guide de déploiement, pour refaire l'installation sans relire toute la conversation.

## Implementation Decisions

- **Stack Coolify** : un nouveau fichier compose dédié, dérivé de la stack de production existante. Pas de service proxy, aucun port publié. L'API, le web et le back-office référencent les images GHCR `meetstudent-api`, `meetstudent-web` et `meetstudent-backoffice` avec un tag pilotable (`IMAGE_TAG`, `latest` par défaut), et sont toujours retirées du registre au déploiement. Variables, volumes, healthchecks et durcissement (`no-new-privileges`) restent identiques à la stack de production.
- **Déploiement Coolify en mode Raw** (ADR 0003) : la stack déclare elle-même ses routes Traefik, avec les mêmes règles et priorités qu'aujourd'hui, sur l'entrée HTTPS, le résolveur Let's Encrypt et le réseau du proxy de Coolify. Les noms d'entrée, de résolveur et de réseau sont vérifiés sur l'instance réelle ; ce sont des paramètres de la stack, pas des choix de conception.
- **Middlewares partagés** (en-têtes de sécurité, retrait de `Forwarded`, limite auth, limite API, limite d'upload) et redirection HTTP → HTTPS : ils sont déclarés dans une configuration dynamique du proxy Coolify, versionnée dans le dépôt, et référencés depuis les routes. Ils ne sont jamais portés par les labels d'un conteneur de l'application : ils disparaîtraient quand ce conteneur redémarre.
- **Réseaux** : le réseau d'entrée devient le réseau externe du proxy Coolify ; Postgres reste seul sur un réseau interne sans sortie.
- **Service de sauvegarde** : une petite image dédiée (client Postgres + rclone), dans la même stack. Elle joint la base par le réseau interne, monte les deux volumes de fichiers en lecture seule, et reste en veille pour qu'une tâche planifiée Coolify y exécute la sauvegarde chaque nuit. La sauvegarde fait un dump Postgres (format custom) et une archive de chaque volume, envoyés par un remote rclone `crypt` qui enveloppe un remote S3. Elle échoue avec un code non nul à la moindre erreur, et ne laisse aucune copie locale. Une commande de restauration vit dans la même image. rclone est configuré uniquement par variables d'environnement. Le fournisseur S3 cible est Cloudflare R2 en juridiction EU ; la rétention de 30 jours est une règle de cycle de vie du bucket, pas une logique du script (ADR 0004).
- **Scripts existants** : les scripts de sauvegarde et de restauration de l'hôte restent dédiés à la stack de production sans Coolify.
- **CI** : un job de déploiement s'ajoute au workflow existant. Il dépend des trois jobs de tests, ne tourne que sur un push vers `main`, dans un environnement GitHub `production` qui détient les secrets, avec la seule permission d'écrire des packages. Il construit et pousse les trois images (tags `sha-<commit>` et `latest`, back-office construit avec son répertoire de sortie Angular), puis appelle le webhook de déploiement authentifié de Coolify (secrets `COOLIFY_WEBHOOK` et `COOLIFY_TOKEN`). Les noms des jobs de tests ne changent pas, car les rulesets les exigent par leur nom ; le job de déploiement n'est pas un check requis.
- **Vérifications sur PR** : sur une pull request, la CI construit les trois images sans les pousser et valide le fichier compose Coolify.
- **Retour arrière** : on fixe `IMAGE_TAG` sur un `sha-<commit>` antérieur dans Coolify, puis on redéploie. Les migrations Flyway ne sont pas annulées : on restaure la sauvegarde prise avant la migration.
- **Documentation** : le guide de déploiement gagne une section Coolify (ressource, variables, tâche planifiée, webhook, retour arrière, restauration). Le guide de production de l'API liste les nouvelles variables de sauvegarde.

## Testing Decisions

- Un bon test observe la stack comme un client : réponses HTTP, en-têtes, statuts, données restaurées. Il ne lit jamais les labels ni la configuration pour vérifier qu'ils « contiennent la bonne chaîne ».
- **Point 1 — la stack vue de l'extérieur** : le fichier compose Coolify tourne en local derrière un Traefik configuré comme celui de Coolify (mêmes entrées, même réseau externe, même configuration dynamique, certificat local). La vérification reprend la checklist de smoke test du guide de déploiement : routage single-origin (`/api`, `/uploads/public`, site), back-office sur son domaine, redirection vers HTTPS, HSTS et nosniff présents, rafale de connexions qui finit en 429, upload de 11 Mo refusé, SSR qui affiche des données réelles, Postgres injoignable. Précédent : les smoke tests en direct de la stack de production (réplique Traefik + Playwright, cf. « Process lessons » du `CLAUDE.md` racine).
- **Point 2 — aller-retour sauvegarde → restauration** : contre une stack peuplée (comptes, une école, un document privé), avec MinIO comme S3 local à la place de R2. La sauvegarde est lancée, on vérifie que les objets envoyés sont chiffrés (illisibles en clair), puis on restaure dans une stack vide et on retrouve la connexion d'un compte, l'école et le contenu du document privé, identique octet pour octet. Une sauvegarde qui échoue (S3 injoignable) doit rendre un code non nul. Précédent : la procédure de restauration du guide de déploiement (« a backup never restored is not a backup »).
- **Point 3 — la CI** : sur PR, les trois images se construisent et le compose est valide. Le push GHCR et le webhook ne se testent qu'au premier déploiement réel sur `main` (phase 6 de la feuille de route), dont le résultat est consigné dans le fichier de suivi de la spec.
- Le code applicatif ne change pas : la suite Maven et les tests Karma existants doivent rester verts sans modification.

## Out of Scope

- Le hachage des refresh tokens (branche `fix/hash-refresh-tokens`) et la déconnexion côté serveur (tâche séparée).
- La création du VPS, l'installation de Coolify, le DNS, le bucket R2 et l'accès au dashboard : ce sont des actions et des décisions du propriétaire (phases 1, 2, 4 et 5 de la feuille de route).
- Un environnement de staging et le déploiement de `dev`.
- Des images multi-architecture (le VPS est x86).
- Une CSP complète (toujours volontairement limitée à `frame-ancestors`, cf. guide de déploiement).
- La supervision au-delà des notifications Coolify (métriques, alertes de disponibilité).

## Further Notes

- Les images GHCR démarrent privées : après le premier push, le propriétaire les passe en public (sinon Coolify ne peut pas les tirer sans identifiants).
- Tant que `IMAGE_TAG` reste fixé sur un ancien commit, les déploiements suivants restent sur cette version : il faut le remettre à `latest` une fois `main` corrigé.
- La mise à jour des sections CI/CD et Sécurité du mémoire suit la livraison : la livraison continue y deviendra un fait, et non plus une perspective.
- Le plan demande l'accord du propriétaire avant de modifier plus de cinq fichiers ; cette spec en implique davantage (compose, configuration dynamique, Dockerfile et scripts de sauvegarde, workflow, guides), à découper en tickets.
