# 04: Catalogue d'écoles sans N+1, nombre de requêtes borné et testé

**What to build:** `GET /api/v1/schools` (et la recherche) répond avec un nombre de requêtes SQL qui ne dépend pas du nombre d'écoles, de programmes ni de médias de la page. La moyenne des notes est calculée en une requête agrégée. Les médias (logo, couverture, photos) sont chargés en lot. Le contenu de la réponse ne change pas pour le web et le back-office.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (catalogue). Mesure de l'audit : 52 requêtes et 23,9 Ko pour 10 écoles × 2 programmes × 2 cours, sans photo.

- [ ] Test d'intégration avec les statistiques Hibernate : le nombre de requêtes de `GET /schools?size=10` est sous un plafond, identique avec 2 et avec 10 écoles, avec et sans photos (rouge vu sur le code actuel)
- [ ] `averageRate` identique avant et après (école notée, non notée)
- [ ] La forme JSON consommée par le web et le back-office est inchangée (leurs suites restent vertes)
- [ ] Une taille de page maximale est fixée ; une taille trop grande est plafonnée ou refusée (testé)

## Comments
