# 02: Connexion : message d'identifiants invalides

**What to build:** Un mauvais mot de passe affiche « La connexion a échoué. Veuillez réessayer. », sans dire que les identifiants sont faux. Afficher un message distinct pour un 401 (« E-mail ou mot de passe incorrect »), identique pour e-mail inconnu et mauvais mot de passe (ne pas révéler si l'e-mail existe). Les autres erreurs (réseau, 5xx, 429) gardent leur message.

**Blocked by:** None.

**Status:** ready-for-agent

- [ ] 401 : message « identifiants incorrects » fr/en, identique pour les deux cas.
- [ ] Erreur réseau/5xx/429 : message générique inchangé.
- [ ] Spec en échec d'abord, puis verte ; suite web et build verts.
