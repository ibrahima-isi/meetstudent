# 03: Page publique d'une formation

**What to build:** Un visiteur ouvre la page d'une formation et voit description, niveau, frais, places et cours.

**Blocked by:** [01](01-program-fields.md), [02](02-course-description.md)

**Status:** resolved

**Worktree:** `program-details-03`

- [x] Route `/:lang/programs/:id` rendue côté serveur, sans garde ; la spec des routes serveur reste verte
- [x] Champs absents non affichés ; places affichées seulement si renseignées
- [x] Liens depuis la liste des formations et la fiche école ; cours listés avec leur description
- [x] Texte brut (une charge `<script>` / `<img onerror>` ne crée aucun élément)
- [x] Erreur avec nouvel essai ; fr/en ; mode sombre ; 375 px sans débordement
- [x] Suppression du calcul mort `capacity - enrolled` et des champs transitoires associés
- [x] Specs de page, rouge d'abord
