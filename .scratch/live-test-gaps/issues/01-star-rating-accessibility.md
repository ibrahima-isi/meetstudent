# 01: Étoiles de notation, nom accessible et focus visible

**What to build:** Chaque bouton d'étoile du composant `shared/components/star-rating` a un nom accessible localisé (ex. « 3 sur 5 »), un état annoncé, et un contour de focus clavier visible en clair et en sombre.

**Blocked by:** None (can start immediately).

**Status:** resolved

- [x] Chaque étoile est annoncée avec sa valeur ; le groupe a un nom (fr/en dans fr.json et en.json).
- [x] Plus de `outline-none` sans équivalent `focus-visible` visible dans les deux thèmes.
- [x] Navigation et validation au clavier vérifiées ; pas de régression des pages de notation (école, programme, cours).
- [x] Spec en échec d'abord (nom accessible, focus), puis verte ; suite web et build verts.
