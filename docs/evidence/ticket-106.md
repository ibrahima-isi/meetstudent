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

## Portée encore à décider

Le code actuel ne possède pas de relation directe cours/accréditation : un cours appartient à un programme, et seule la relation programme/accréditation existe. Le ticket mentionne également une désassociation cours/accréditation. Choix demandé au propriétaire : ajouter une relation directe et son retrait, ou couvrir le cours par les accréditations de son programme. Aucune PR ni revue finale avant cette décision.
