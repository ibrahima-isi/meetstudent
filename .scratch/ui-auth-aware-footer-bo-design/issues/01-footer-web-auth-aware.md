# 01: Footer web sensible à l'auth

**What to build:** Dans le footer commun du site, la colonne « Account » dépend de l'état de l'utilisateur. Visiteur : Log in / Sign up. Connecté (étudiant ou expert) : « Mon profil » et « Se déconnecter ». Tant que l'état n'est pas connu côté navigateur (pages rendues serveur), la colonne n'est pas affichée, pour éviter le clignotement.

**Blocked by:** None (can start immediately). Vérifier d'abord l'état du ticket GitHub #117, qui recoupe ce sujet.

**Status:** resolved (implementation; Playwright pending)

- [x] Un connecté ne voit plus Log in / Sign up ; il voit Mon profil et Se déconnecter, traduits FR/EN.
- [x] « Se déconnecter » ferme la session ; « Mon profil » ouvre le profil.
- [x] Un visiteur voit toujours Log in / Sign up ; aucun clignotement pour un connecté sur une page rendue serveur.
- [x] Lisible en thème clair et sombre.
- [x] Specs de composant en échec d'abord, puis vertes.
- [ ] Parcours Playwright visiteur/connecté sur la pile locale (non exécuté, à faire avant la promotion).
