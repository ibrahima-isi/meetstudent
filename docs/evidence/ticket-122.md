# Ticket #122 — quatre destinations Produit du footer

Issue : [#122](https://github.com/ibrahima-isi/meetstudent/issues/122), parent [#99](https://github.com/ibrahima-isi/meetstudent/issues/99).
Base isolée : `7b903108806f8b28ef47b12dee9c7adc7134b6e3`, branche `feat/footer-product-122`.

## Périmètre

Le footer partagé propose les quatre liens localisés : établissements, formations/programmes, cours et accréditations. Les trois nouvelles entrées réutilisent `pageTitle.programs`, `pageTitle.courses` et `pageTitle.accreditations`, déjà traduites en français et anglais ; le libellé établissements reste celui de `nav.schools`. Les ancres Angular conservent la langue active et ouvrent les catalogues publics existants. Chaque cible Produit mesure au moins 44 × 44 pixels et possède un contour explicite au focus clavier, avec les couleurs sémantiques du thème. Les boutons compte et le placement unique du footer partagé sont préservés.

Manifeste autorisé : composant footer, son test, cette preuve et les deux suivis `docs/completion.md` / `completion.md` (cinq fichiers). Aucun service, route, traduction, API ni autre ticket modifiés.

## TDD et vérifications locales

Seams approuvés : DOM rendu, Router applicatif et frontières HTTP existantes. Les tests Router utilisent la vraie table des routes et les composants/services de production ; seul le transport HTTP est contrôlé par `HttpTestingController`. Cette qualification déterministe ne constitue pas un smoke navigateur/API réel.

- Premier rouge réel : liste Produit FR limitée à un lien, trois destinations absentes ; **13 succès / 1 échec** (`/private/tmp/meetstudent-122-red.log`). Ajout minimal : **14 succès** (`/private/tmp/meetstudent-122-green-first.log`).
- Deuxième rouge réel : liens trop bas pour le toucher ; **15 succès / 1 échec** (`/private/tmp/meetstudent-122-red-touch.log`). Hauteur minimale et focus explicite : **16 succès** (`/private/tmp/meetstudent-122-green-touch.log`).
- Troisième rouge réel : Cours mesuré à **40,296875 px** de large ; **17 succès / 1 échec** (`/private/tmp/meetstudent-122-red-width.log`). Largeur minimale ajoutée.
- Ciblés finaux : **33 succès** sur footer, shell public et landing (`/private/tmp/meetstudent-122-targeted-final.log`). Couverture : quatre libellés/hrefs FR, changement dynamique EN, focus et dimensions tactiles ; activation des quatre catalogues fr/en par les ancres, requêtes GET attendues, contenu public chargé sans erreur, footer unique et boutons compte préservés sur About/Contact/catalogues.
- Types application et tests : verts (`/private/tmp/meetstudent-122-types-app-final.log`, `/private/tmp/meetstudent-122-types-spec-final.log`).
- Suite web complète finale : **1075 / 1075 succès** (`/private/tmp/meetstudent-122-web-suite.log`). Build SSR final réussi : **8,406 s**, sortie `apps/web/dist/frontend` (`/private/tmp/meetstudent-122-web-build.log`). Les deux ont été exécutés une seule fois sur le code final.

## Vérifications et livraison restantes

Navigation réelle sur stack jetable, régressions mobile/clavier/clair/sombre, revues indépendantes Standards/Spec, puis CI API/web/backoffice sur le head définitif et PR vers `dev` restent à consigner. Les deux suivis restent décochés jusqu’aux preuves requises. Aucune stack partagée lancée ou modifiée par l’implémenteur ; aucune promotion vers `main` dans ce ticket.
