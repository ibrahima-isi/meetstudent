# 07: Unicité en base des médias d'images d'entités (V38) et conflit en 409

**What to build:** La base garantit qu'un même média ne sert d'image qu'à une seule école, un seul programme ou un seul cours, même en cas de requêtes admin simultanées. Une violation donne un 409 `ErrorResponse` clair. La validation applicative actuelle (catégorie, visibilité, propriétaire, déjà utilisé) reste en place.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (images d'entités). V38 est réservé à ce ticket.

- [ ] Migration V38 : index uniques partiels (`IS NOT NULL`) sur `logo_media_id`, `cover_media_id`, `photo_media_id` ; si des doublons existent déjà, la migration échoue avec un message explicite (aucune suppression)
- [ ] Test `*MigrationPostgresIntegrationTests` : V1→V38 sans doublon passe ; avec un doublon préexistant, échec explicite ; exécuté par la CI
- [ ] Deux attachements concurrents du même média : un seul réussit, l'autre reçoit 409
- [ ] Le démarrage avec `ddl-auto: validate` reste vert
- [ ] Note de déploiement : sauvegarde avant V38 (docs)

## Comments
