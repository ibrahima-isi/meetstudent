# Ticket #106 — désassociation d’accréditation partagée

Base isolée : `origin/dev` à `93635e0c3af57e66de614786f93e1641b1ec9898`, branche `fix/accreditation-unlink-106`.

## Programme / accréditation

- Test HTTP rouge : avec deux programmes partageant une accréditation, `DELETE` du premier lien renvoyait 409 à cause du `CascadeType.ALL` du lien vers l’accréditation. Retrait d’un lien absent : 204 au lieu de 404.
- Correction : le lien ne propage plus la suppression à l’accréditation ; la suppression transactionnelle porte sur l’association trouvée, sinon 404.
- Tests ciblés verts : lien retiré seulement du premier programme, accréditation et autre lien toujours consultables ; lien absent 404 ; rôle étudiant interdit (403) avec lien préservé.
- Backoffice : confirmation visible après 204, accréditation de nouveau disponible dans la sélection ; 404 explicite et liste conservée. Les deux nouveaux tests de rendu ont été observés rouges puis verts.

## Vérifications provisoires du 2026-10-15

- API : `./mvnw clean verify -q`, 226 unitaires + 327 intégrations, zéro échec, avant le test de permission ajouté ensuite ; ce test ciblé passe.
- Backoffice : 290 tests, contrôle TypeScript et build de production verts.
- Web : 940 tests, contrôle TypeScript et build SSR verts, sans modification web.
- PostgreSQL 18 jetable : Flyway V1–V22 et `ddl-auto=validate` ; tests HTTP ciblés de conservation du lien partagé et du 404 verts sur deux bases propres. Après retrait, requête SQL : 1 accréditation et 1 association restantes. Conteneur arrêté et supprimé.

## Cours / accréditation

- Choix explicite du propriétaire : ajouter la relation directe cours/accréditation et sa désassociation. Migration Flyway V23, clé composée et contrôles HTTP de lecture, association et retrait.
- TDD HTTP : POST absent d’abord en 404, puis vert. Deux cours et un programme partagent une accréditation ; retirer le premier lien conserve l’autre lien de cours, le lien de programme et l’accréditation. Lien absent 404, rôle étudiant interdit 403, doublon 409, ressource absente 404 et suppression de l’accréditation encore liée 409.
- Backoffice : tests du composant rouges puis verts pour l’affichage, l’association avec années, le retrait avec confirmation et la réponse 404. La fiche d’un cours enregistré expose ces actions ; l’éditeur est commun aux cours et programmes. Le catalogue et les liens de cours sont chargés sur toutes leurs pages ; une accréditation conservée au-delà de la première page reste sélectionnable après réouverture. 300 tests passent, TypeScript et build de production verts.
- API complète après ajout : 558 tests (226 unitaires, 332 intégrations), aucun échec.
- PostgreSQL 18 jetable neuf : Flyway V1–V23, `ddl-auto=validate` et test HTTP du partage cours/programme verts. Après retrait, requêtes SQL : version 23, une accréditation, une association de cours et une association de programme. Conteneur arrêté et supprimé.

Les revues indépendantes ont demandé de paginer la liste HTTP, de charger toutes les pages des liens et du catalogue, d’extraire la duplication UI et d’appliquer OnPush aux composants ; ces points sont corrigés. La récursion de pagination est partagée entre les deux lectures. Les dernières revues Sol Standards et Spec n’ont aucun constat actionnable sur `e177808`. [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38774480762) sur ce commit ; ce closeout documentaire doit passer les trois CI sur son head final avant squash.
