# Spec : p2-backlog

Status: ready-for-agent

Origine : audit du 2026-10-27 (inventaire, revue de code, revue sécurité) puis correctifs P0 (#227 à #229) et P1 (#230 à #241), promus sur `main` le 2026-10-29 (`922ff98`). Chaque point ci-dessous a été revérifié sur `922ff98`. Décisions à respecter : [ADR 0001](../../docs/adr/0001-credential-session-version.md) (version de mot de passe dans le jeton), [ADR 0002](../../docs/adr/0002-session-inactivity.md) (inactivité de 7 jours), [ADR 0003](../../docs/adr/0003-coolify-raw-compose-ghcr.md) (Coolify).

## Problem Statement

MeetStudent est prêt pour un premier déploiement, mais l'audit a laissé une série de défauts de priorité 2 :

- **Le contrat de l'API n'est pas tenu partout.** 29 réponses 400/404 n'ont pas de corps, alors que les clients attendent `{champ: message}` ou une `ErrorResponse`. `PATCH /media/{id}/verification` avec `PENDING` répond 500. `GET /users/role/STUDENT` répond toujours 400, alors que c'est l'exemple donné par Swagger.
- **Une petite fuite d'information demeure.** `GET /users/email/{email}` répond 403 si le compte existe et 404 sinon, à tout utilisateur connecté. Côté web, l'e-mail n'est pas encodé dans cette URL : une adresse contenant `#`, `?` ou `%` laisse l'utilisateur à moitié connecté.
- **Le catalogue d'écoles est lent.** Il fait environ 52 requêtes SQL pour 10 écoles (N+1 sur les médias, moyenne des notes calculée école par école), et cette page publique est rendue en SSR.
- **La logique « une note par utilisateur » est recopiée** dans trois contrôleurs, hors service et sans transaction unique. Cette copie a déjà produit un bug (note de programme sans programme, corrigée en P0).
- **Une création accepte un `id` envoyé par le client**, ce qui la transforme en mise à jour (merge).
- **Rien en base n'empêche deux entités de partager le même média d'image**, alors que supprimer l'une supprime l'image de l'autre.
- **Du code mort et un log4j isolé** brouillent la lecture, et la configuration du profil de test est embarquée dans le jar de production.
- **Côté web, des fragilités restent** : 7 composants sans `OnPush` (application sans zone), une course entre requêtes sur la page école, et un `localStorage` corrompu qui bloque le démarrage.
- **Côté sessions** : le back-office ne synchronise pas le refresh token entre onglets, si bien qu'un second onglet déclenche une fausse détection de rejeu et ferme la Session. Deux onglets qui rafraîchissent exactement en même temps peuvent encore se déconnecter. Un logout envoyé avec un ancien refresh token ne ferme pas la Session.
- **La CSP du web n'est qu'en Report-Only**, sans endpoint qui collecte les violations.
- **Des médias hérités d'avant les correctifs restent sans contrôle** : fichiers orphelins, PDF ou vidéos stockés dans des catégories d'images.
- **La section « Project state » du `CLAUDE.md` racine est périmée**, ce qui égare les sessions d'agents.

## Solution

Une série de tickets indépendants et vérifiables, chacun livré par sa propre branche, avec ses tests et une PR vers `dev` :

- **Contrat API** : un 400/404 a toujours un corps ; un statut de modération invalide donne 400 ; le rôle se cherche avec ou sans préfixe `ROLE_`.
- **Lecture par e-mail** : un non-admin reçoit la même réponse qu'il s'agisse d'un compte existant ou non. Le web encode l'e-mail et ne reste jamais à moitié connecté.
- **Catalogue** : un nombre de requêtes borné, qui ne dépend pas du nombre d'écoles.
- **Notes** : un service de note unique et transactionnel ; les contrôleurs ne font que déléguer.
- **Création** : un `id` envoyé par le client est ignoré.
- **Images d'entités** : unicité en base, conflit traduit en 409.
- **Ménage** : code mort supprimé, journalisation homogène, configuration de test hors du jar.
- **Web** : composants `OnPush`, chargement de la page école annulable, lecture du stockage tolérante.
- **Onglets** : synchronisation dans le back-office, verrou de rafraîchissement partagé entre onglets, logout capable de retrouver une Session par un ancien refresh token.
- **CSP web** : bloquante, avec nonce SSR et endpoint de rapport.
- **Médias hérités** : un rapport admin des cas non conformes, sans suppression.
- **Doc** : `CLAUDE.md` mis à jour.
- **Tests de bout en bout** : un harnais Playwright réutilisable par les tickets web.

## User Stories

1. En tant que développeur du web, je veux qu'une réponse 400 ait toujours un corps `{champ: message}`, afin d'afficher l'erreur sur le bon champ.
2. En tant que développeur du back-office, je veux qu'une réponse 404 ait toujours une `ErrorResponse`, afin de montrer un message clair plutôt qu'un écran vide.
3. En tant qu'admin, je veux qu'un statut de modération invalide (par exemple `PENDING`) soit refusé en 400, afin de comprendre mon erreur au lieu de voir « erreur serveur ».
4. En tant qu'admin, je veux lister les utilisateurs par rôle avec `STUDENT` comme avec `ROLE_STUDENT`, afin que l'exemple de la documentation fonctionne.
5. En tant qu'utilisateur connecté, je ne veux pas pouvoir découvrir si une adresse e-mail a un compte, afin de protéger la vie privée des autres.
6. En tant qu'étudiant dont l'adresse contient un `+`, `#` ou `%`, je veux pouvoir me connecter normalement, afin de ne pas rester bloqué à moitié connecté.
7. En tant qu'étudiant, si le chargement de mon profil échoue juste après la connexion, je veux être déconnecté proprement, afin de pouvoir réessayer.
8. En tant que visiteur, je veux que le catalogue d'écoles s'affiche vite même quand il y a beaucoup d'écoles, afin de comparer sans attendre.
9. En tant qu'exploitant, je veux que le nombre de requêtes SQL du catalogue soit borné et testé, afin qu'une régression soit détectée par la CI.
10. En tant qu'étudiant, je veux que noter une école deux fois mette à jour ma note, afin de garder une seule note par établissement.
11. En tant qu'Expert approuvé, je veux le même comportement pour les programmes et les cours, afin que les règles soient identiques partout.
12. En tant que mainteneur, je veux une seule implémentation de la règle « une note par utilisateur et par cible », afin de ne pas corriger un bug trois fois.
13. En tant qu'admin, je veux qu'une création ignore l'`id` envoyé dans le corps, afin de ne jamais écraser une entité existante par erreur.
14. En tant qu'admin, je veux qu'un même média ne puisse pas être l'image de deux entités, même en cas de requêtes simultanées, afin que supprimer une école n'efface pas le logo d'une autre.
15. En tant qu'admin, je veux recevoir un 409 clair si je réutilise un média déjà attaché, afin de choisir une autre image.
16. En tant que mainteneur, je veux que le code mort soit supprimé, afin de lire seulement ce qui sert.
17. En tant qu'exploitant, je veux une journalisation homogène (slf4j), afin de configurer les logs à un seul endroit.
18. En tant qu'exploitant, je veux que le jar de production n'embarque pas la configuration de test, afin de ne livrer que ce qui sert en prod.
19. En tant qu'étudiant, je veux que les pages se mettent à jour correctement dans l'application sans zone, afin de ne jamais voir d'écran figé.
20. En tant qu'étudiant qui passe vite d'une école à l'autre, je veux toujours voir l'école demandée, afin de ne pas lire les données d'une autre.
21. En tant qu'étudiant dont le stockage local est abîmé, je veux que l'application démarre quand même et me demande de me reconnecter, afin de ne pas rester sur une page blanche.
22. En tant qu'admin avec plusieurs onglets du back-office, je veux que chaque onglet reprenne les jetons renouvelés par un autre, afin de ne pas être déconnecté par une fausse détection de vol.
23. En tant qu'utilisateur qui restaure une session de navigateur avec plusieurs onglets, je veux qu'un seul onglet rafraîchisse la Session à la fois, afin de ne pas être déconnecté partout.
24. En tant qu'utilisateur victime d'un vol de jeton, je veux qu'un logout envoyé avec mon ancien refresh token ferme la Session, afin d'expulser le voleur.
25. En tant qu'exploitant, je veux une CSP bloquante sur le web, afin qu'une faille XSS ne puisse pas exfiltrer les jetons.
26. En tant qu'exploitant, je veux recevoir les violations de CSP, afin de détecter une régression ou une attaque.
27. En tant qu'admin, je veux un rapport des médias orphelins et des fichiers publics non conformes, afin de décider quoi nettoyer en connaissance de cause.
28. En tant que session d'agent, je veux une section « Project state » exacte dans le `CLAUDE.md`, afin de ne pas partir d'hypothèses fausses.
29. En tant que développeur, je veux un harnais Playwright qui lance la stack native et un parcours fumée, afin que les tickets web soient vérifiés dans un vrai navigateur.
30. En tant que relecteur, je veux que chaque ticket arrive avec ses tests dans les deux sens (la règle et son contraire), afin de faire confiance au correctif.

## Implementation Decisions

- **Contrat d'erreur.** Les réponses vides passent par les exceptions existantes du gestionnaire global (`ResourceNotFoundException` pour 404, `FieldValidationException` pour 400 par champ). Les codes HTTP ne changent pas, seul le corps apparaît. Un statut de modération autre que `VERIFIED`/`REJECTED` lève une erreur de validation sur le champ `status`. La recherche par rôle normalise le nom (préfixe `ROLE_` ajouté s'il manque, casse ignorée).
- **Lecture par e-mail.** Le contrôle d'accès passe avant la recherche : un non-admin qui demande un autre compte que le sien reçoit la même réponse, que le compte existe ou non. Le comportement pour le propriétaire et l'admin ne change pas. Le web encode l'e-mail dans l'URL ; si la lecture du profil échoue après la connexion, il efface les jetons.
- **Catalogue.** La moyenne des notes est calculée en une seule requête agrégée par page d'écoles. Les médias des écoles et des programmes sont chargés en lot (requête groupée par ids, ou batch fetch Hibernate). Les relations `@ManyToOne` chargées par défaut en EAGER sont revues. Un DTO résumé pour les listes est permis si le contrat de `SchoolDTO` reste compatible pour le web et le back-office. La limite de taille de page (`max-page-size`) est fixée.
- **Notes.** Un service de note générique sur la cible (école, programme, cours) porte l'upsert dans une seule transaction et garde le comportement actuel : 201 à la création, mise à jour sur place, 409 en cas de course perdue avec réessai qui met à jour. `@RequiresApprovedExpert` continue de garder programmes et cours. Les contrôleurs se contentent de déléguer.
- **Création.** Les chemins de création (école, programme, cours, accréditation, tag) remettent l'`id` du corps à `null`. Les mises à jour gardent l'`id` du chemin.
- **Images d'entités.** Une migration V38 ajoute des index uniques partiels (`WHERE ... IS NOT NULL`) sur `logo_media_id`, `cover_media_id` (écoles) et `photo_media_id` (programmes, cours). L'unicité s'applique aussi d'une table à l'autre, via `EntityImageValidator`, qui reste en place. Une violation est traduite en 409 `ErrorResponse`. La migration détecte les doublons existants et échoue avec un message explicite plutôt que de supprimer.
- **Ménage.**
  - On supprime `AdminService` et `ExpertService` (vides), les helpers d'URL de `MediaService` qui ne servent plus et les méthodes inutilisées de `UserService`.
  - Le filtre JWT passe à slf4j.
  - La configuration du profil `test` quitte les ressources principales pour les ressources de test.
- **Web.** `OnPush` sur les 7 composants listés par l'audit. La page école charge via `switchMap` (ou `rxResource`), et le repli `loadPrograms` mort est supprimé ou corrigé (`schoolId`). `TokenService` protège la lecture de l'utilisateur stocké : en cas d'erreur, il vide la session.
- **Onglets.**
  - Le back-office reprend le mécanisme livré dans le web par #241 : adoption des jetons écrits par un autre onglet, relecture du stockage avant de rafraîchir, rattrapage si un autre onglet a déjà fait la rotation.
  - Le verrou de rafraîchissement utilise `navigator.locks` quand il existe, avec repli sur le comportement actuel (et rien côté SSR).
- **Logout.** `POST /auth/logout` cherche aussi la Session par l'historique des refresh tokens (V37). Un ancien secret ferme la Session ; la réponse reste identique.
- **CSP web.** Le serveur SSR génère un nonce par réponse (`CSP_NONCE` / `ngCspNonce`), l'applique aux scripts et styles inline, et envoie une CSP bloquante. Le middleware `sec-web` de Traefik n'envoie plus sa propre CSP, pour ne pas écraser celle du serveur. Les violations sont envoyées à un endpoint de rapport de l'API, limité en débit, qui se contente de journaliser.
- **Médias hérités.** Un endpoint admin, en lecture seule, liste :
  - les lignes média sans fichier ;
  - les fichiers sans ligne ;
  - les médias publics dont l'extension ne correspond plus à leur catégorie ;
  - les médias d'images d'entités qu'aucune entité ne référence.

  Aucune suppression automatique.
- **Harnais e2e.** Un dossier e2e au niveau du dépôt (Playwright), avec un script qui démarre la stack native comme le smoke test du 2026-10-29 : Postgres jetable, jar API en profil prod, web, back-office. Il lance les parcours puis arrête tout. Pas de job CI obligatoire dans ce ticket : on ne change pas les checks requis du ruleset.

## Testing Decisions

- **Un bon test vérifie le comportement observable** : statut, corps, en-têtes, données en base, rendu. Il ne vérifie pas les détails internes. Chaque règle est testée dans les deux sens (la règle et son contraire), et le test doit d'abord échouer.
- **API.** Seam principal : tests d'intégration MockMvc par HTTP (`*IntegrationTests`, `TestDataUtil.mockUser`, chemins complets `/api/v1/...`). Exemples existants :
  - `EntityImageCategoryIntegrationTests` ;
  - `CredentialAttemptLimitIntegrationTests` ;
  - `RefreshTokenReuseIntegrationTests`.

  Pour le N+1, le même seam compte les requêtes SQL avec les statistiques Hibernate et vérifie qu'un `GET /schools` reste sous un plafond indépendant du nombre d'écoles. Pour V38, un `*MigrationPostgresIntegrationTests` sur le modèle de `ProgramRatesRequireProgramMigrationPostgresIntegrationTests`, exécuté par la CI (service Postgres de #230).
- **Web et back-office.** Specs TestBed avec `HttpTestingController` (exemples : les specs de `token.service`, `refresh.interceptor` et `forum-access` de #241). La garde dark-mode et la parité i18n doivent rester vertes.
- **Bout en bout.** Playwright, avec le harnais du ticket 01, pour les tickets web 09, 10, 11 et 13. Les parcours couvrent le multi-onglets (deux pages du même contexte), le rechargement avec un stockage abîmé et la CSP (aucune violation au chargement, script injecté bloqué).
- **Infra.** `infra/tests/coolify-stack.test.sh` et `infra/tests/deploy-docs.test.sh` pour tout changement de middleware ou de documentation de déploiement.

## Out of Scope

- La réécriture du back-office en Vite (plan du 2026-10-10).
- Le premier déploiement réel (`coolify-deploy/05`).
- La suppression automatique des médias hérités : le ticket 14 ne fait que les lister.
- Rendre le job e2e obligatoire dans le ruleset GitHub (décision du propriétaire, plus tard).
- La question produit « un Expert en attente ou refusé peut-il noter une école ? » : le comportement actuel est conservé.

## Further Notes

- **Chevauchements de fichiers.** Les tickets 02, 03 et 06 touchent les mêmes contrôleurs, et 02 et 05 touchent les contrôleurs de notes. Rien ne les bloque fonctionnellement, mais lancés en parallèle ils entreront en conflit. Mieux vaut les faire l'un après l'autre (02, puis 05, puis 03, puis 06), ou mettre chaque branche à jour depuis `dev` avant le merge.
- **Migrations.** V38 est réservé au ticket 07. Toute autre migration prend le numéro libre suivant au moment du merge.
- **Déploiement.** Le ticket 13 modifie un middleware Traefik : il faut recopier le fichier dynamique sur le serveur avant de déployer (`docs/DEPLOY.md` §9.1).
