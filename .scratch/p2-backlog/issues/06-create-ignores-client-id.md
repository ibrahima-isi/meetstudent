# 06: Une création ignore l'id envoyé par le client

**What to build:** Un admin qui crée une école, un programme, un cours, une accréditation ou un tag en envoyant un `id` dans le corps obtient toujours une nouvelle entité : l'`id` du corps est ignoré et l'entité existante qui porte cet `id` n'est jamais modifiée. Les mises à jour continuent d'utiliser l'`id` du chemin.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (création). Mieux vaut le faire après 02 (mêmes contrôleurs).

- [ ] Pour chaque ressource créable : POST avec l'`id` d'une entité existante → 201, nouvel `id`, entité existante inchangée (rouge vu sur le code actuel)
- [ ] POST sans `id` : comportement inchangé
- [ ] PUT/PATCH : l'`id` du chemin fait foi, un `id` différent dans le corps est ignoré
- [ ] La validation des images d'entités (`EntityImageValidator`) reste correcte en création

## Comments
