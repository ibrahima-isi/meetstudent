# Spec : détails des formations et des cours

Status: ready-for-agent

Branche cible : `feat/program-details` (PR vers `dev`). Dans le vocabulaire du code, « formation » = `Program`.

## Problem Statement

Une formation n'a que son nom, sa durée et sa photo. Le mémoire déclare des formations avec description, places disponibles, frais et niveau ; rien de cela n'existe, et le code web « places restantes » ne s'affiche jamais faute de données. Il n'y a pas de page publique d'une formation, et un cours n'a pas de description.

## Solution

L'administrateur renseigne, dans le back office, la description, les places disponibles, les frais annuels et le niveau d'une formation, et la description d'un cours. Le public consulte une page détail de formation qui montre ces informations et la liste de ses cours.

## User Stories

1. En tant qu'administrateur, je veux saisir la description d'une formation, afin d'expliquer son contenu.
2. En tant qu'administrateur, je veux saisir le nombre de places disponibles, afin d'informer les candidats.
3. En tant qu'administrateur, je veux saisir les frais annuels en euros, afin d'afficher le coût.
4. En tant qu'administrateur, je veux choisir le niveau parmi une liste (BTS/DUT, Licence, Master, Doctorat, Autre), afin d'uniformiser les données.
5. En tant qu'administrateur, je veux laisser ces champs vides, afin de ne pas inventer une donnée inconnue.
6. En tant qu'administrateur, je veux modifier un champ sans écraser les autres (comportement PATCH existant), afin d'éviter les pertes.
7. En tant qu'administrateur, je veux saisir la description d'un cours, afin de détailler la formation.
8. En tant qu'administrateur, je veux un message clair si une valeur est invalide (places négatives, frais négatifs, description trop longue, niveau inconnu), afin de corriger.
9. En tant que visiteur, je veux ouvrir la page d'une formation, afin de voir description, niveau, frais, places et cours.
10. En tant que visiteur, je veux voir « places disponibles » seulement si l'information existe, afin de ne pas voir de faux chiffres.
11. En tant que visiteur, je veux voir la liste des cours de la formation avec leur description, afin de juger le programme.
12. En tant que visiteur, je veux accéder à la page depuis la liste des formations et la fiche de l'école, afin de naviguer facilement.
13. En tant que visiteur, je veux la page en français et en anglais, afin de la lire dans ma langue.
14. En tant que visiteur, je veux une page lisible en mode sombre et sur mobile, afin de consulter partout.
15. En tant que visiteur, je veux un message d'erreur avec nouvel essai si la formation n'existe pas ou si l'API échoue, afin de ne pas voir une page vide.
16. En tant que visiteur, je veux que les textes saisis par l'administrateur s'affichent en texte brut, afin d'éviter toute injection.
17. En tant que moteur de recherche, je veux que la page soit rendue côté serveur, afin d'être indexable.
18. En tant que propriétaire, je veux que les formations existantes continuent de fonctionner sans ces champs, afin qu'aucune donnée ne casse.
19. En tant qu'administrateur, je veux que seul un ADMIN puisse écrire ces champs, afin de protéger le catalogue.

## Implementation Decisions

- Migration (numéro libre suivant) : colonnes optionnelles sur formation : description (≤1000), places disponibles (entier ≥0), frais annuels (décimal ≥0, euros), niveau (liste fermée) ; colonne description (≤1000) sur cours. Aucune valeur par défaut inventée.
- Un seul champ « places disponibles » saisi par l'administrateur ; pas de capacité ni d'inscrits. Supprimer le calcul mort `capacity - enrolled` et ses champs transitoires côté web.
- « Intitulé » = le nom existant ; pas de nouveau champ.
- DTO formation et cours étendus ; `PUT` reste un PATCH (null = garder), les nouveaux champs ne peuvent être vidés que si une valeur explicite le permet ; décider à l'implémentation et documenter dans le contrat (CLAUDE.md « API contract quirks »).
- Validation : erreurs 400 `{champ: message}` comme le reste de l'API ; niveau inconnu = 400.
- Web : nouvelle route publique `/:lang/programs/:id` rendue côté serveur (hors des routes gardées), affichant champs et cours ; liens depuis la liste des formations et la fiche école ; clés i18n fr et en ; styles par jetons sémantiques.
- Back office : formulaires de formation et de cours étendus.
- `docs/` : mettre à jour la note « schools/programs have no capacity, tuition, level… » de CLAUDE.md.

## Testing Decisions

- Test du comportement externe uniquement.
- Seam backend : `ProgramControllerIntegrationTests` et `CourseControllerIntegrationTests` : création/lecture/patch avec les nouveaux champs, validation 400, anonyme et étudiant refusés en écriture, lecture publique, formations sans champs.
- Seam web : spec de la page détail (affichage conditionnel, erreur/nouvel essai, texte brut avec une charge `<script>`/`<img onerror>`) ; test que la route est rendue côté serveur (`app.routes.server.ts`).
- Seam back office : specs des formulaires.
- Art antérieur : `programs-page.component.spec.ts`, `program-form.spec.ts`, `ProgramControllerIntegrationTests`.

## Out of Scope

Suivi des inscrits ou des candidatures ; page détail d'un cours ; filtre de recherche par niveau ou frais ; devise autre que l'euro ; rôle gestionnaire ; classement des cours.

## Further Notes

Classement, notation et comparaison existent déjà : ne pas y toucher. Les pages de comparaison de formations pourront afficher les nouveaux champs dans une tâche ultérieure.
