# 14: Rapport admin des médias orphelins et hérités non conformes

**What to build:** Un admin obtient, par un endpoint en lecture seule, la liste des médias à examiner :
- les lignes média dont le fichier manque ;
- les fichiers sans ligne ;
- les médias publics dont le type ne correspond plus à leur catégorie (par exemple un PDF stocké comme photo, envoyé avant les quotas de #237) ;
- les images d'entités qu'aucune entité ne référence.

Rien n'est supprimé : l'admin décide.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (médias hérités).

- [ ] Endpoint admin-only ; non-admin → 403, anonyme → 401
- [ ] Chaque catégorie de problème est détectée sur un jeu de données préparé, et un média sain n'apparaît pas (tests d'intégration avec de vrais fichiers)
- [ ] Aucun fichier ni aucune ligne modifiés par l'appel (vérifié)
- [ ] Réponse paginée ou bornée pour un grand nombre de médias

## Comments
