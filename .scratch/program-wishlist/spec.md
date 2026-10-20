# Spec : liste de souhaits des formations

Status: ready-for-agent

Branche cible : `feat/program-wishlist` (PR vers `dev`). Dépend de `program-details` pour afficher les champs d'une formation, mais le contrat d'API est indépendant.

## Problem Statement

La liste de souhaits ne contient que des établissements. Le mémoire déclare qu'on peut y ajouter, retirer et consulter des établissements ou des formations.

## Solution

L'étudiant ajoute et retire aussi des formations à sa liste de souhaits, et consulte une liste unifiée établissements et formations. Les enseignants n'ont toujours pas de liste de souhaits.

## User Stories

1. En tant qu'étudiant, je veux ajouter une formation à ma liste de souhaits, afin de la retrouver plus tard.
2. En tant qu'étudiant, je veux retirer une formation de ma liste, afin de la garder à jour.
3. En tant qu'étudiant, je veux consulter ma liste avec mes établissements et mes formations, afin de tout comparer au même endroit.
4. En tant qu'étudiant, je veux voir sur une formation si elle est déjà dans ma liste, afin d'éviter les doublons.
5. En tant qu'étudiant, je veux que l'ajout répété d'une même formation ne crée pas de doublon, afin d'avoir une liste propre.
6. En tant qu'étudiant, je veux une erreur claire si la formation n'existe pas, afin de comprendre l'échec.
7. En tant qu'étudiant, je veux que ma liste survive à la déconnexion et à un autre appareil, afin de la retrouver.
8. En tant qu'étudiant, je veux que personne d'autre ne puisse modifier ma liste, afin de protéger mes données.
9. En tant qu'enseignant, je ne vois pas le bouton liste de souhaits, afin de ne pas voir une fonction qui m'est interdite.
10. En tant que visiteur anonyme, je suis invité à me connecter quand je clique sur le cœur, afin de comprendre qu'il faut un compte.
11. En tant qu'administrateur, je veux que la suppression d'une formation la retire des listes, afin d'éviter des références orphelines.
12. En tant qu'étudiant supprimant son compte, je veux que ma liste soit effacée, afin de respecter la suppression des données.
13. En tant qu'étudiant, je veux la liste en français et en anglais et en mode sombre, afin d'un affichage cohérent.

## Implementation Decisions

- Nouvelle relation utilisateur-formation (table de jointure, migration au numéro libre suivant) avec suppression en cascade côté formation et côté utilisateur ; le nettoyage à la suppression de compte existant est étendu.
- Endpoints sous `/api/v1/users/{userId}/wishlist/programs/{programId}` (POST ajoute, DELETE retire) et une lecture de la liste de formations ; réservés à `ROLE_STUDENT` avec contrôle du propriétaire, comme la liste d'établissements. L'ajout est idempotent.
- La lecture combinée peut rester portée par le DTO utilisateur ou par un endpoint dédié ; choisir le plus simple en respectant les contrats clients actuels.
- Web : le service de liste de souhaits gère les deux types ; le panier et la page profil affichent les deux ; cœur sur la formation.
- Le cœur reste masqué aux experts (ticket #109 inchangé).

## Testing Decisions

- Seam backend : `UserControllerIntegrationTests` : ajout, retrait, idempotence, formation inconnue 404, autre utilisateur 403, expert et anonyme refusés, suppression de formation et de compte propres.
- Seam web : specs du service et des composants wishlist avec `HttpTestingController`.
- Art antérieur : `testThatWishlistCanBeManaged`, `testThatUserCannotManageAnotherUsersWishlist`, `wishlist.service.spec.ts`.

## Out of Scope

Liste de souhaits pour les cours ; listes partagées ou publiques ; notifications de places ou de frais ; ouverture aux experts.

## Further Notes

Données personnelles : la liste n'est lisible que par son propriétaire.
