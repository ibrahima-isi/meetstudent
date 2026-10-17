# Spec: footer sensible à l'auth, design du back office aligné sur le web, emplacement du Forum

Status: ready-for-agent

## Problem Statement

Un utilisateur connecté voit encore, dans la colonne « Account » du footer du site, les boutons « Log in » et « Sign up », comme s'il était visiteur. De son côté, le back office ne ressemble pas au site public : pas de navbar, pas de footer, et un habillage différent. Enfin, il n'existe aucun emplacement prévu pour le futur Forum.

## Solution

- Le footer du site adapte la colonne « Account » à l'état de l'utilisateur : visiteur = Log in / Sign up ; connecté = Mon profil / Se déconnecter. Rien n'est affiché tant que l'état est inconnu côté navigateur (pas de clignotement Log in/Sign up pour un connecté).
- Le back office adopte les mêmes couleurs, boutons et thème clair/sombre que le web, avec une navbar haute et un footer. La sidebar d'administration reste.
- Le Forum apparaît comme entrée désactivée « Bientôt » dans les navbars du site et du back office.

## User Stories

1. En tant que visiteur, je veux voir Log in et Sign up dans le footer, afin de créer un compte ou me connecter.
2. En tant qu'étudiant connecté, je ne veux plus voir Log in / Sign up, afin que l'interface reflète mon état.
3. En tant qu'expert connecté, je veux les mêmes liens « Mon profil » et « Se déconnecter » dans le footer.
4. En tant qu'utilisateur connecté, je veux me déconnecter depuis le footer, afin de fermer ma session facilement.
5. En tant qu'utilisateur connecté, je veux ouvrir mon profil depuis le footer.
6. En tant qu'utilisateur connecté qui recharge une page rendue côté serveur, je ne veux pas voir clignoter Log in / Sign up avant l'hydratation.
7. En tant que visiteur dont la session vient d'expirer, je veux revoir Log in / Sign up dès que l'état est connu.
8. En tant qu'utilisateur FR ou EN, je veux ces libellés traduits dans ma langue.
9. En tant qu'utilisateur en mode sombre ou clair, je veux un footer lisible dans les deux thèmes.
10. En tant qu'administrateur, je veux que le back office ait le même look que le site (couleurs, boutons, thème), afin d'éviter l'effet « deux produits ».
11. En tant qu'administrateur, je veux basculer entre thème clair et sombre dans le back office, comme sur le site.
12. En tant qu'administrateur, je veux une navbar haute avec le nom du produit, mon identité et un bouton de déconnexion.
13. En tant qu'administrateur, je veux garder la sidebar de navigation de modération.
14. En tant qu'administrateur, je veux un footer avec la version, un lien vers le site public et le ©.
15. En tant qu'administrateur, je veux que le back office reste en français.
16. En tant qu'administrateur, je veux voir une entrée « Forum – Bientôt » désactivée, afin de savoir qu'une modération du forum viendra.
17. En tant qu'étudiant ou expert, je veux voir « Forum – Bientôt » désactivé dans la navbar du site, afin de savoir qu'un forum arrive.
18. En tant qu'utilisateur au clavier, je veux que l'entrée Forum désactivée soit annoncée comme désactivée et ne reçoive pas de focus actif.
19. En tant que visiteur sur mobile, je veux que la navbar et le footer restent utilisables en petite largeur.
20. En tant que non-admin sur le back office, je veux rester refusé comme aujourd'hui, sans régression.

## Implementation Decisions

- Le footer commun du site lit l'état d'authentification existant et masque la colonne tant que l'état n'est pas connu côté navigateur (pages publiques rendues serveur).
- Connecté : liens « Mon profil » et « Se déconnecter » ; visiteur : boutons actuels.
- Le back office réutilise les mêmes jetons de design (couleurs sémantiques, boutons, dark/light) que le web, sans introduire de dépendance entre les deux apps.
- Le back office gagne une navbar haute et un footer ; la sidebar d'administration est conservée. Pas de sélecteur de langue (français uniquement).
- Le Forum : entrée désactivée « Bientôt » (aria-disabled, non focusable) sur le web et le back office, sans route. Le terme est défini dans le glossaire.
- Découpage en 3 PRs vers `dev` : (1) correctif du footer web ; (2) jetons et thème du back office ; (3) navbar et footer du back office, avec l'entrée Forum sur les deux apps.
- Aucun changement d'API.

## Testing Decisions

- Un bon test vérifie le comportement visible (ce qui s'affiche selon l'état d'auth, les liens, l'état désactivé), jamais l'implémentation.
- Seams : specs de composants Karma/Jasmine du footer et de la navbar du web, et du shell du back office ; un parcours e2e Playwright visiteur/connecté sur la pile locale (SSR réel) pour couvrir le clignotement.
- Art antérieur : specs du footer et du public-shell du web ; garde `dark-mode.guard.spec.ts` (classes dark) ; specs du shell et des routes du back office ; smoke tests Playwright décrits dans CLAUDE.md.
- TDD : test en échec d'abord, puis implémentation minimale.

## Out of Scope

- Le Forum lui-même (pages, API, modération) ; son périmètre reste à définir.
- Refonte complète de la structure du back office, internationalisation du back office.
- Le sujet du cœur de wishlist pour les enseignants (ticket distinct).
- Tout changement d'API.

## Further Notes

- Ce travail recoupe le ticket GitHub #117 (boutons du footer) ; vérifier son état avant de démarrer la PR 1.
- Le plan de réécriture du back office en TypeScript/Vite (`docs/plans/2026-10-10-backoffice-vite-rewrite.md`) peut recouper la PR 2/3 : le lire avant de toucher au back office.
- Après chaque ticket : docs/completion.md à tenir à jour selon `docs/agents/issue-tracker.md`.
