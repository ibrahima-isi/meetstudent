# 02: Bouton « Postuler maintenant » de la fiche établissement

**What to build:** Le bouton « Postuler maintenant » (`school-detail-page.component.html`, ligne ~222) a un comportement réel défini par le propriétaire, ou n'est plus affiché s'il n'y en a pas.

**Blocked by:** None. Décision de produit requise : que doit faire « Postuler » (lien vers le site de l'établissement, formulaire, contact, ou retrait du bouton) ? À trancher avant de coder.

**Status:** resolved

- [x] Décision produit consignée dans ce ticket (Comments).
- [x] Le bouton fait ce qui est décidé, ou disparaît ; jamais un bouton mort. Donnée absente : ne rien inventer.
- [x] Libellés fr/en, mode sombre, mobile ; états connecté/visiteur respectés.
- [x] Spec en échec d'abord, puis verte ; suite web et build verts.

## Comments

- 2026-10-17, décision du propriétaire : « Postuler maintenant » mène à la page contact `/:lang/contact` avec l'école pré-remplie. Constat : cette page n'a pas de formulaire, seulement le lien `mailto:` de l'adresse de contact ; le propriétaire a choisi (a) : le lien mailto reçoit un sujet pré-rempli avec le nom de l'école.
- Implémenté : le bouton (et l'entrée « Liste d'attente » d'un programme complet) est un lien `routerLink` vers `/<lang>/contact?school=<nom>`, jamais désactivé. La page contact lit `school` et ajoute `?subject=` (fr « Candidature : … », en « Application: … ») ; sans paramètre, le mailto reste nu. La route contact n'a pas de guard : visiteurs et connectés y accèdent.
