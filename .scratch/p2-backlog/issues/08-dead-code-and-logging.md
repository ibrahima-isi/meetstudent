# 08: Ménage : code mort, journalisation slf4j, configuration de test hors du jar

**What to build:** Le code de l'API ne contient plus de classes ni de méthodes inutilisées (services vides, helpers d'URL de médias abandonnés, méthodes non appelées, `System.err`). Toute la journalisation passe par slf4j. Le jar de production n'embarque plus la configuration du profil `test`. Aucun comportement visible ne change.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (ménage).

- [ ] Code mort supprimé (liste confirmée par recherche des appels, aucun appel restant)
- [ ] Plus aucun import log4j direct ni `System.err` dans le code principal
- [ ] Le jar construit ne contient plus `application-test.yml` (vérifié en listant le jar) ; les tests trouvent toujours leur configuration
- [ ] `./mvnw clean verify` vert ; `apps/api/CLAUDE.md` corrigé là où il décrit l'emplacement de la configuration de test

## Comments
