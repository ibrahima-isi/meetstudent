# 03: Page publique d'une formation

**What to build:** Un visiteur ouvre la page d'une formation et voit description, niveau, frais, places et cours.

**Blocked by:** [01](01-program-fields.md), [02](02-course-description.md)

**Status:** ready-for-agent

**Worktree:** `program-details-03`

- [ ] Route `/:lang/programs/:id` rendue côté serveur, sans garde ; la spec des routes serveur reste verte
- [ ] Champs absents non affichés ; places affichées seulement si renseignées
- [ ] Liens depuis la liste des formations et la fiche école ; cours listés avec leur description
- [ ] Texte brut (une charge `<script>` / `<img onerror>` ne crée aucun élément)
- [ ] Erreur avec nouvel essai ; fr/en ; mode sombre ; 375 px sans débordement
- [ ] Suppression du calcul mort `capacity - enrolled` et des champs transitoires associés
- [ ] Specs de page, rouge d'abord
