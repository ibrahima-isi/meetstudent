# 01: Étoiles de l'accueil : libellé de moyenne

**What to build:** Sur la carte d'une école de l'accueil (`home-page.component.html`, ~l.162), `star-rating` affiche la moyenne mais annonce « Votre note », et l'étoile égale à une moyenne entière est annoncée « enfoncée ». Le composant a un mode lecture seule/moyenne : libellé « Moyenne : N sur 5 » (fr/en), sans boutons ni `aria-pressed` ; la notation de l'utilisateur (fiche école) reste inchangée.

**Blocked by:** None.

**Status:** resolved

- [x] Une moyenne est annoncée « Moyenne : N sur 5 » (fr/en), jamais « Votre note ».
- [x] Aucune étoile n'est « enfoncée » en lecture seule ; plus de bouton inutile au clavier.
- [x] Notation utilisateur (école, programme, cours) inchangée.
- [x] Spec en échec d'abord, puis verte ; suite web et build verts.

## Comments

2026-10-17 : mode lecture seule = `role="img"` « Moyenne : N sur 5 » / « Pas encore noté » (fr/en), sans boutons ni `aria-pressed`, moyenne arrondie à une décimale ; la carte de l'accueil passe `[readonly]="true"`. En écrivant la spec, un défaut voisin est apparu : `initialRating` n'était lu que dans le constructeur (donc 0), les étoiles des cartes de l'accueil n'étaient jamais remplies ; le mode lecture seule lit désormais l'entrée directement. La notation utilisateur (non readonly) garde son comportement (son `rating` initial reste lu au constructeur, hors périmètre).
