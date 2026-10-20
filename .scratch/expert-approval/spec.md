# Spec : approbation du compte Expert (enseignant)

Status: ready-for-agent

Branche cible : `feat/expert-approval` (PR vers `dev`). Première tranche : le forum en dépend.

## Problem Statement

Aujourd'hui n'importe qui s'inscrit comme enseignant (`ROLE_EXPERT`) et obtient aussitôt les droits d'expert (noter formations et cours). Le mémoire déclare une validation du compte Expert par un administrateur ; elle n'existe pas : ni statut, ni écran de validation, ni mail, ni blocage.

## Solution

Un compte Expert porte un statut d'approbation : en attente, approuvé ou rejeté. Un expert en attente ou rejeté se connecte normalement mais n'a pas les droits d'expert (noter formations et cours, écrire sur le forum). Un administrateur traite une file « Experts en attente » dans le back office, avec un motif facultatif au rejet. L'expert reçoit un mail à l'inscription (en attente) et à la décision, et voit son statut dans son profil. Les experts déjà inscrits sont considérés approuvés.

## User Stories

1. En tant que visiteur, je veux m'inscrire comme enseignant, afin de rejoindre la plateforme comme expert.
2. En tant qu'enseignant qui vient de s'inscrire, je veux voir que mon compte est « en attente de validation », afin de savoir pourquoi je ne peux pas encore noter les formations.
3. En tant qu'enseignant en attente, je veux me connecter et consulter le site comme un étudiant, afin de ne pas être bloqué pendant la validation.
4. En tant qu'enseignant en attente, je veux recevoir un mail confirmant que ma demande est reçue, afin d'être rassuré.
5. En tant qu'enseignant en attente, je ne peux pas noter une formation ni un cours, afin que seuls les experts validés influencent les notes.
6. En tant qu'enseignant approuvé, je veux noter formations et cours, afin d'apporter mon avis d'expert.
7. En tant qu'enseignant approuvé, je veux recevoir un mail de confirmation, afin de savoir que je peux contribuer.
8. En tant qu'enseignant rejeté, je veux voir que ma demande est rejetée et le motif s'il existe, afin de comprendre la décision.
9. En tant qu'enseignant rejeté, je veux recevoir un mail de rejet, afin d'être informé sans me connecter.
10. En tant qu'administrateur, je veux une liste paginée des experts en attente (nom, e-mail, spécialité, date d'inscription), afin de les traiter rapidement.
11. En tant qu'administrateur, je veux approuver un expert en un clic, afin de lui donner ses droits.
12. En tant qu'administrateur, je veux rejeter un expert avec un motif facultatif (255 caractères), afin de justifier ma décision.
13. En tant qu'administrateur, je veux ré-approuver un expert rejeté, afin de corriger une erreur.
14. En tant qu'administrateur, je veux filtrer la liste par statut (en attente, approuvé, rejeté), afin de retrouver un dossier.
15. En tant qu'administrateur, je veux que mon action soit refusée si le compte n'est pas un expert, afin d'éviter les statuts incohérents.
16. En tant qu'étudiant, je ne suis pas concerné par le statut, afin que mon parcours reste inchangé.
17. En tant que propriétaire, je veux que les experts existants passent « approuvé » à la migration, afin de ne retirer aucun droit acquis.
18. En tant que propriétaire, je veux qu'un non-administrateur ne puisse jamais changer un statut, afin d'éviter l'auto-promotion.
19. En tant qu'utilisateur anonyme, je ne peux pas lire la liste des experts en attente, afin de protéger les données personnelles.
20. En tant qu'administrateur, je veux que le changement de statut prenne effet sans que l'expert ait à se reconnecter, afin d'éviter une attente inutile.

## Implementation Decisions

- Nouveau statut d'approbation sur l'utilisateur, uniquement significatif pour `ROLE_EXPERT` ; valeurs : en attente, approuvé, rejeté, plus un motif de rejet optionnel (255 caractères) et la date de décision. Une migration (V29 ou suivante libre) ajoute les colonnes et passe tous les experts existants à « approuvé » ; un nouvel expert naît « en attente », les autres rôles n'ont pas de statut.
- Les droits de notation formation/cours (`program-rates`, `course-rates`) exigent maintenant un expert approuvé, appliqué côté serveur (jamais seulement côté client). La notation d'établissement par un expert reste ouverte, comme pour un étudiant.
- Le futur forum réutilisera la même condition « expert approuvé » ; prévoir un point d'accès unique pour cette règle plutôt que la dupliquer.
- Le statut doit être pris en compte à chaque requête (lu en base ou porté par le jeton avec invalidation), pour que l'approbation et le rejet s'appliquent sans reconnexion ; trancher à l'implémentation en respectant l'ADR 0001 (version des identifiants/sessions).
- Endpoints admin sous `/api/v1/admin/` : liste paginée filtrable par statut, approbation, rejet avec motif. Tri fixe (date d'inscription décroissante), taille de page plafonnée à 100, comme la liste des messages.
- Mails (nouveaux types) : demande reçue, expert approuvé, expert rejeté (avec le motif, échappé). Passent par le service de messagerie existant et ses résultats d'envoi.
- DTO utilisateur : expose le statut et le motif à l'intéressé uniquement ; jamais d'autre donnée interne.
- Web : le profil affiche le statut d'un expert ; les écrans de notation de formation/cours expliquent le blocage au lieu d'un échec muet. Clés i18n fr et en.
- Back office : nouvelle entrée « Experts », file en attente, onglets par statut, actions avec confirmation, motif de rejet.
- Glossaire : ajouter « Expert (enseignant) » et « Statut d'approbation ».

## Testing Decisions

- Un bon test vérifie le comportement externe (statut HTTP, contenu de la réponse, mail émis, texte affiché), pas l'implémentation.
- Seam backend : tests d'intégration des contrôleurs (`*IntegrationTests`) : inscription crée « en attente » ; expert en attente reçoit 403 sur notation formation/cours ; approuvé passe ; rejeté refusé ; non-admin et anonyme refusés (403/401) ; statut inchangé pour un étudiant ; migration : experts existants approuvés (test Postgres comme les tests de classement).
- Seam web et back office : specs des pages (profil, file des experts) avec `HttpTestingController` ; états vide, chargement, erreur avec nouvel essai.
- Art antérieur : `contact-rework` (liste admin paginée, mails), `UserControllerIntegrationTests` (inscription enseignant), `moderation-page.spec.ts`.

## Out of Scope

Rôle gestionnaire d'établissement ; pièces justificatives pour l'enseignant (les documents existants restent modérés à part) ; blocage de la connexion ; notification dans l'application ; changement du formulaire d'inscription.

## Further Notes

Risque : la règle de rôle est dans `WebSecurityConfig` et en `@PreAuthorize` ; vérifier les deux. Sauvegarder la base avant la migration.
