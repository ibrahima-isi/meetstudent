# 02: Connexion : message d'identifiants invalides

**What to build:** Un mauvais mot de passe affiche « La connexion a échoué. Veuillez réessayer. », sans dire que les identifiants sont faux. Afficher un message distinct pour un 401 (« E-mail ou mot de passe incorrect »), identique pour e-mail inconnu et mauvais mot de passe (ne pas révéler si l'e-mail existe). Les autres erreurs (réseau, 5xx, 429) gardent leur message.

**Blocked by:** None.

**Status:** resolved

- [x] 401 : message « identifiants incorrects » fr/en, identique pour les deux cas.
- [x] Erreur réseau/5xx/429 : message générique inchangé.
- [x] Spec en échec d'abord, puis verte ; suite web et build verts.

## Comments

2026-10-17 : un 401 affiche `auth.login.invalidCredentials` (« E-mail ou mot de passe incorrect. » / « Incorrect email or password. »), sans jamais lire le message de l'API, donc identique pour e-mail inconnu et mauvais mot de passe. Réseau (0), 429, 500, 503 gardent `auth.login.failed`. Le test existant du bandeau `role="alert"` cherchait le texte générique après un 401 : mis à jour.
