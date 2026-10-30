# 05: Une seule implémentation transactionnelle de « une note par utilisateur et par cible »

**What to build:** Noter une école, un programme ou un cours passe par un même service de note, générique sur la cible, qui fait l'upsert dans une seule transaction. Le comportement visible ne change pas : 201 à la création, mise à jour sur place à la seconde note, 409 si une course est perdue puis mise à jour au réessai. Seuls les Experts approuvés notent les programmes et les cours. Les trois contrôleurs de notes ne font plus que déléguer.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (notes). Mieux vaut le faire après 02 (mêmes contrôleurs).

- [ ] Les tests d'intégration existants des trois types de notes restent verts sans modification de leurs attentes
- [ ] Nouveaux tests sur le service : création, mise à jour, cible inconnue, cible absente (400), course concurrente (409 puis mise à jour)
- [ ] Étudiant → programme : refusé ; Expert en attente → programme : refusé ; Expert approuvé → programme : accepté
- [ ] Plus aucune logique d'upsert dans les contrôleurs de notes

## Comments
