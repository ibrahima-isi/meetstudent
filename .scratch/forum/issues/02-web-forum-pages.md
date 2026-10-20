# 02: Pages web du forum

**What to build:** Les visiteurs lisent le forum ; les connectés créent des fils et répondent depuis le site.

**Blocked by:** [01](01-api-threads-replies.md)

**Status:** ready-for-agent

**Worktree:** `forum-02`

- [ ] Pages liste et fil, rendues côté serveur pour la lecture ; écriture côté client
- [ ] Formulaires création et réponse (compteurs, états chargement/succès/erreur, 400 par champ, 429)
- [ ] Texte affiché par interpolation (aucun `innerHTML`), sauts de ligne conservés ; un test XSS ne crée aucun élément
- [ ] Anonyme invité à se connecter ; expert en attente voit pourquoi il ne peut pas écrire
- [ ] L'entrée « Forum » de la navigation devient un lien actif
- [ ] fr/en, mode sombre, 375 px sans débordement ; specs de page, rouge d'abord
