# Ticket #122 — quatre destinations Produit du footer

Issue : [#122](https://github.com/ibrahima-isi/meetstudent/issues/122), parent [#99](https://github.com/ibrahima-isi/meetstudent/issues/99).
Base isolée : `7b903108806f8b28ef47b12dee9c7adc7134b6e3`, branche `feat/footer-product-122`.

## Périmètre

Le footer partagé propose les quatre liens localisés : établissements, formations/programmes, cours et accréditations. Les trois nouvelles entrées réutilisent `pageTitle.programs`, `pageTitle.courses` et `pageTitle.accreditations`, déjà traduites en français et anglais ; le libellé établissements reste celui de `nav.schools`. Les ancres Angular conservent la langue active et ouvrent les catalogues publics existants. Chaque cible Produit mesure au moins 44 × 44 pixels et possède un contour explicite au focus clavier, avec les couleurs sémantiques du thème. Les boutons compte et le placement unique du footer partagé sont préservés.

Manifeste de cinq fichiers, sous le seuil d’autorisation supplémentaire : composant footer, son test, cette preuve et les deux suivis `docs/completion.md` / `completion.md` (cinq fichiers). Aucun service, route, traduction, API ni autre ticket modifiés.

## TDD et vérifications locales

Seams approuvés : DOM rendu, Router applicatif et frontières HTTP existantes. Les tests Router utilisent la vraie table des routes et les composants/services de production ; seul le transport HTTP est contrôlé par `HttpTestingController`. Cette qualification déterministe ne constitue pas un smoke navigateur/API réel.

- Premier rouge réel : liste Produit FR limitée à un lien, trois destinations absentes ; **13 succès / 1 échec** (`/private/tmp/meetstudent-122-red.log`). Ajout minimal : **14 succès** (`/private/tmp/meetstudent-122-green-first.log`).
- Deuxième rouge réel : liens trop bas pour le toucher ; **15 succès / 1 échec** (`/private/tmp/meetstudent-122-red-touch.log`). Hauteur minimale et focus explicite : **16 succès** (`/private/tmp/meetstudent-122-green-touch.log`).
- Troisième rouge réel : Cours mesuré à **40,296875 px** de large ; **17 succès / 1 échec** (`/private/tmp/meetstudent-122-red-width.log`). Largeur minimale ajoutée.
- Ciblés finaux : **33 succès** sur footer, shell public et landing (`/private/tmp/meetstudent-122-targeted-final.log`). Couverture : quatre libellés/hrefs FR, changement dynamique EN, focus et dimensions tactiles ; activation des quatre catalogues fr/en par les ancres, requêtes GET attendues, contenu public chargé sans erreur, footer unique et boutons compte préservés sur About/Contact/catalogues.
- Types application et tests : verts (`/private/tmp/meetstudent-122-types-app-final.log`, `/private/tmp/meetstudent-122-types-spec-final.log`).
- Suite web complète finale : **1075 / 1075 succès** (`/private/tmp/meetstudent-122-web-suite.log`). Build SSR final réussi : **8,406 s**, sortie `apps/web/dist/frontend` (`/private/tmp/meetstudent-122-web-build.log`). Les deux ont été exécutés une seule fois sur le code final.

## Parcours navigateur réels

Le coordinateur a construit l’API du même worktree sans modification backend, puis démarré une API en profil prod avec `ddl-auto=validate`, une base PostgreSQL 18 jetable, le build SSR final et un proxy de même origine sur des ports dédiés. Quatre ressources ont été créées via les endpoints HTTP authentifiés : établissement, formation, cours lié et accréditation. Flyway a confirmé **24 migrations réussies, versions 1–24**. Aucune réponse métier n’a été simulée.

- **96 parcours Produit** : quatre catalogues × Accueil/À propos/Contact × fr/en × bureau 1440 px/mobile 375 px × clair/sombre. Chacun a ouvert la destination localisée et affiché la ressource correspondante, présente dans la réponse HTTP 200 réelle.
- Bureau : ordre de tabulation depuis la marque, focus visible et activation Entrée. Mobile : activation tactile réelle sous émulation Chrome. Chaque lien mesure au moins **44 × 44 px** dans les deux langues et les deux thèmes ; aucun débordement horizontal.
- **48 parcours compte** : Connexion/Inscription depuis les trois pages × deux langues × deux formats × deux thèmes. Ancres et styles de boutons existants conservés, pages réelles atteintes.
- Un seul footer sur chaque page source et **0 erreur JavaScript**. Huit captures du footer ; captures bureau FR clair et mobile FR sombre inspectées visuellement.
- Résultats : `/private/tmp/meetstudent-122-browser-results.json` ; script et journal : `/private/tmp/meetstudent-122-browser.cjs`, `/private/tmp/meetstudent-122-browser.log`. Les ressources jetables API/SSR/proxy, conteneur PostgreSQL et volume ont été arrêtés/supprimés ; ports 18122/42123/42124/55122 sans listener et inventaire `ms122` vide. Authentification temporaire supprimée. La stack et le checkout partagés sont préservés.

Ces parcours qualifient Chrome sur bureau et mobile émulé ; ils ne constituent pas une validation sur téléphone physique. Aucune promotion vers `main` dans ce ticket.

## Revues et livraison

Les trois agents primaires ont utilisé exactement les modèles/efforts demandés : lecteur gpt-5.6-sol medium, planner/implémenteur gpt-6.1-sol medium, reviewer/fixer gpt-6.1-sol high. Les phases de lecture puis d’implémentation étaient terminées avant les deux axes enfants indépendants Standards et Spec.

- Revue indépendante de `7b90310...68d2373` : **Standards 0 / Spec 0** ; aucune correction requise. Le reviewer a aussi vérifié les journaux et confirmé **33/33** tests ciblés (`/private/tmp/meetstudent-122-reviewer-targeted.log`).
- Code final signé : `68d23738f3414ed41ea15743547dcc8ea6c457b1` ; aucune modification source après les tests, build et smoke. Cette actualisation ne touche que les trois documents déjà prévus.
- [PR #158](https://github.com/ibrahima-isi/meetstudent/pull/158) vers `dev`. L’implémentation et ses validations sont terminées ; la fusion reste conditionnée aux **trois checks API/web/backoffice verts sur le head documentaire définitif**, même si un head antérieur est vert.
- Le coordinateur vérifie le squash, la signature et l’identité de l’arbre, clôture explicitement #122, puis archive seulement le worktree et la branche du ticket. L’état CI/fusion final fait foi sur la PR ; les hashes définitifs, clôture et nettoyage après fusion sont conservés dans `/private/tmp/meetstudent-122-delivery.md` et le handoff suivant.
