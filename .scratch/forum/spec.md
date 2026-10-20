# Spec : forum

Status: ready-for-agent

Branche cible : `feat/forum` (PR vers `dev`). Dépend de `expert-approval` (règle « expert approuvé »).

## Problem Statement

Le forum est annoncé dans le mémoire mais n'existe pas : seule une entrée désactivée « Bientôt » apparaît dans les navigations du site et du back office. Étudiants et experts n'ont aucun espace pour discuter, et l'administrateur n'a rien à modérer.

## Solution

Un forum à lecture publique où les étudiants et les experts approuvés ouvrent des fils et y répondent. Un fil peut être rattaché à un établissement. Les utilisateurs peuvent signaler un message ; l'administrateur traite les signalements dans le back office (masquer, supprimer, verrouiller un fil). Les contenus sont du texte brut et le rythme d'écriture est limité.

## User Stories

1. En tant que visiteur, je veux lire la liste des fils, afin de découvrir les discussions.
2. En tant que visiteur, je veux ouvrir un fil et lire ses réponses, afin de suivre la discussion.
3. En tant que visiteur, je veux filtrer les fils par établissement, afin de trouver ceux qui m'intéressent.
4. En tant qu'étudiant, je veux créer un fil avec titre et message, afin de poser une question.
5. En tant qu'étudiant, je veux rattacher mon fil à un établissement (facultatif), afin de cibler les réponses.
6. En tant qu'étudiant, je veux répondre à un fil, afin de participer.
7. En tant qu'expert approuvé, je veux répondre et créer des fils, afin d'aider les étudiants.
8. En tant qu'expert en attente ou rejeté, je ne peux pas écrire, afin que seuls les experts validés interviennent.
9. En tant qu'auteur, je veux modifier mon message, afin de corriger une erreur.
10. En tant qu'auteur, je veux supprimer mon message, afin de retirer ce que j'ai écrit.
11. En tant qu'auteur, je veux que personne d'autre ne puisse modifier ou supprimer mes messages, afin de garder le contrôle.
12. En tant qu'utilisateur, je veux signaler un message inapproprié avec un motif, afin d'alerter l'administrateur.
13. En tant qu'utilisateur, je veux ne pas pouvoir signaler deux fois le même message, afin d'éviter l'abus.
14. En tant qu'administrateur, je veux la liste des signalements ouverts, afin de traiter les plus urgents.
15. En tant qu'administrateur, je veux masquer un message, afin qu'il disparaisse du forum sans être détruit.
16. En tant qu'administrateur, je veux supprimer définitivement un message ou un fil, afin de retirer un contenu illégal.
17. En tant qu'administrateur, je veux verrouiller un fil, afin d'arrêter une discussion qui dérape.
18. En tant qu'administrateur, je veux clore un signalement (traité ou rejeté), afin de vider la file.
19. En tant qu'utilisateur, je veux voir qu'un fil est verrouillé et ne pas pouvoir y répondre, afin de comprendre.
20. En tant qu'utilisateur, je veux que mon texte s'affiche tel quel (aucun HTML ni script exécuté), afin d'être en sécurité.
21. En tant que propriétaire, je veux limiter à 5 messages par minute et par utilisateur, afin de freiner le spam.
22. En tant que propriétaire, je veux que les champs soient bornés (titre ≤150, corps ≤5000), afin d'éviter les abus.
23. En tant que lecteur, je veux la liste paginée, des plus récents aux plus anciens, afin d'un chargement rapide.
24. En tant que lecteur, je veux voir qui a écrit (prénom ou pseudo public), sans e-mail ni donnée privée, afin de protéger la vie privée.
25. En tant qu'utilisateur supprimé, mes messages restent et apparaissent « Utilisateur supprimé », afin de garder la cohérence des fils.
26. En tant que visiteur anonyme, je suis invité à me connecter pour écrire, afin de comprendre qu'il faut un compte.
27. En tant qu'utilisateur, je veux le forum en français et en anglais, en mode sombre et sur mobile, afin d'un usage partout.
28. En tant que visiteur, je veux que l'entrée « Forum » de la navigation soit active, afin d'y accéder.

## Implementation Decisions

- Modèle : fil (titre, message d'ouverture, auteur, établissement facultatif, verrouillé, dates), réponse (corps, auteur, fil, masquée, dates), signalement (message ciblé, auteur du signalement, motif, état ouvert/traité/rejeté). Migration au numéro libre suivant. Auteur supprimé : clé étrangère mise à null et affichage « Utilisateur supprimé ».
- Droits : lecture publique ; création/réponse pour `ROLE_STUDENT` ou expert approuvé ; modification/suppression par l'auteur seulement ; actions de modération sous `/api/v1/admin/` réservées à ADMIN. Un fil verrouillé refuse toute réponse (409 ou 403, à fixer et documenter).
- Texte brut uniquement : jamais de HTML ni de markdown à l'enregistrement ; rendu par interpolation Angular (aucun `innerHTML`) ; sauts de ligne conservés par mise en forme CSS.
- Anti-abus : limite de 5 messages par minute et par utilisateur (réponse 429 avec `Retry-After`, comme le contact) ; un seul signalement par utilisateur et par message ; titre ≤150, corps ≤5000, motif ≤255 ; contrôles de caractères invisibles comme pour le contact.
- Les messages masqués ne sont pas retournés au public (ni leur corps) ; l'administrateur les voit.
- Listes paginées, taille plafonnée à 100, tri fixe (récent d'abord) ; aucun e-mail ni identifiant interne dans les DTO publics.
- Web : pages liste de fils et fil (rendu côté serveur pour la lecture, écriture côté client), formulaires, signalement ; l'entrée « Bientôt » devient un lien actif dans la navbar ; clés i18n fr et en. Back office : entrée « Forum » (signalements + actions), remplace « Bientôt ».
- Glossaire : définir « Forum », « Fil », « Réponse », « Signalement », « Masquer » ; mettre à jour l'entrée « Forum » qui dit « périmètre non défini ».
- ADR à proposer seulement si un choix difficile à défaire apparaît (par exemple : texte brut seul).

## Testing Decisions

- Un bon test vérifie le comportement externe : statut, corps de réponse, contenu affiché, absence d'exécution de script.
- Seam backend : tests d'intégration des contrôleurs : lecture anonyme, écriture par rôle (étudiant, expert approuvé, expert en attente, anonyme), propriété (modifier/supprimer les messages d'autrui = 403), limite de débit 429, bornes de taille 400, signalement unique, masquage (disparaît du public, visible admin), verrouillage, suppression de compte (auteur anonymisé), injection `<script>` stockée telle quelle puis renvoyée en texte.
- Seam web : specs des pages avec `HttpTestingController` : rendu texte brut d'une charge XSS (aucun élément créé), états vide/erreur/chargement, invitation à se connecter, fil verrouillé.
- Seam back office : spec de la page de signalements (comme `moderation-page.spec.ts`).
- Art antérieur : `contact-rework` (limite de débit, XSS, liste admin), `SchoolRateControllerIntegrationTests` (rôles).

## Out of Scope

Réponses imbriquées ; markdown ou pièces jointes ; mentions et notifications ; votes ; recherche plein texte ; sujets épinglés ; modération automatique ; édition par l'administrateur du texte d'autrui.

## Further Notes

C'est la tranche la plus large (environ 45 fichiers) : la découper en tickets backend puis web puis back office. Les signalements sont des données personnelles : lecture réservée à l'admin.
