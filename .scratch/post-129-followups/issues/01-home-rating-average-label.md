# 01: Étoiles de l'accueil : libellé de moyenne

**What to build:** Sur la carte d'une école de l'accueil (`home-page.component.html`, ~l.162), `star-rating` affiche la moyenne mais annonce « Votre note », et l'étoile égale à une moyenne entière est annoncée « enfoncée ». Le composant a un mode lecture seule/moyenne : libellé « Moyenne : N sur 5 » (fr/en), sans boutons ni `aria-pressed` ; la notation de l'utilisateur (fiche école) reste inchangée.

**Blocked by:** None.

**Status:** ready-for-agent

- [ ] Une moyenne est annoncée « Moyenne : N sur 5 » (fr/en), jamais « Votre note ».
- [ ] Aucune étoile n'est « enfoncée » en lecture seule ; plus de bouton inutile au clavier.
- [ ] Notation utilisateur (école, programme, cours) inchangée.
- [ ] Spec en échec d'abord, puis verte ; suite web et build verts.
