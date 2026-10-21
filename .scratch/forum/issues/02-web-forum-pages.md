# 02: Pages web du forum

**What to build:** Les visiteurs lisent le forum ; les connectés créent des fils et répondent depuis le site.

**Blocked by:** [01](01-api-threads-replies.md)

**Status:** resolved

**Worktree:** `forum-02`

- [x] Pages liste et fil, rendues côté serveur pour la lecture ; écriture côté client
- [x] Formulaires création et réponse (compteurs, états chargement/succès/erreur, 400 par champ, 429)
- [x] Texte affiché par interpolation (aucun `innerHTML`), sauts de ligne conservés ; un test XSS ne crée aucun élément
- [x] Anonyme invité à se connecter ; expert en attente voit pourquoi il ne peut pas écrire
- [x] L'entrée « Forum » de la navigation devient un lien actif
- [x] fr/en, mode sombre (garde de classes verte), 375 px sans débordement (classes `break-words`, `min-w-0`, `flex-wrap` seulement, non vérifié en navigateur réel) ; specs de page, rouge d'abord

## Résultat

Branche `feat/web-forum-pages`, uniquement `apps/web` (plus docs). Preuves réelles :
- `npm test -- --no-watch --browsers=ChromeHeadless` : 1584/1584 verts ; `npm run build` (SSR) OK (seul avertissement : budget CSS de `dock-navbar`, déjà présent avant le ticket, réduit ici).
- Rouge d'abord : pour chaque nouveau module (service, `cleanDisplayName`, erreurs, composeur, deux pages) la spec échouait à la compilation (module introuvable) ; `cleanDisplayName` avec un stub identité, 4 specs navbar (lien actif), 3 specs de routes (liste, fil, rendu serveur) vues échouer avant le code.
- SSR réel sans API (`API_URL` injoignable) : `/fr/forum` 200 avec état d'erreur, `/fr/forum/abc` 404, `/fr/forum/5` 200 avec état d'erreur.

Décisions :
- Pas de route de création : le formulaire de nouveau fil est un volet de la liste (`/:lang/forum`), visible seulement après hydratation pour un étudiant ou un expert approuvé (`injectHydratedSession`), donc aucune route gardée ni rendu client forcé ; lecture rendue côté serveur (`RenderMode.Server`).
- Un expert sans statut connu (session ancienne) n'est pas bloqué : l'API tranche (403 affiché). Admin : lecture seule. Filtre et page dans l'URL (`?school=`, `?page=`).
- Texte brut : interpolation uniquement, `whitespace-pre-line break-words`, jamais `innerHTML` ; specs XSS (liste et fil : `<img onerror>`, `<script>`, `<svg onload>` dans titre, corps et prénom) : aucun élément créé.
- Prénoms nettoyés avant affichage par `shared/display-name.ts` (contrôles, bidi, zéro-largeur, formats, séparateurs, remplissages U+3164/2800/115F/1160/FFA0/180E/034F, sélecteurs de variante FE00-FE0F et E0100-E01EF, U+17B4/17B5, marques combinantes limitées à 2 de suite ; ZWNJ/ZWJ conservés ; lettres accentuées et CJK conservées) ; nom vide = « Membre ».
- 429 : message avec les secondes de `Retry-After` (plafonné à 3600), sans valeur : « une minute » ; 409 fil verrouillé ; 400 `{title|body|schoolId}` sous le champ ; suppression avec confirmation en ligne.
- Navbar : « Forum » devient un lien (barre et menu), badge « Bientôt », styles et clé `nav.comingSoon` supprimés.

Non vérifié : rendu dans un vrai navigateur (375 px, mode sombre, hydratation avec session, `mine` recalculé après connexion car la requête authentifiée n'utilise pas le cache de transfert), SSR avec la vraie API, parcours complet avec un compte réel, signalement (forum/05).

Limites connues (revues, non corrigées) :
- `expertStatus` est lu dans la session stockée (`TokenService.user`) : un expert approuvé depuis sa dernière connexion reste traité comme « en attente » jusqu'à la prochaine connexion ou au rafraîchissement du profil, comme la fiche école.
- La pagination de l'URL (`?page=`) est en base 0 (`?page=1` = deuxième page), alignée sur l'API ; l'affichage « Page x sur y » est en base 1. Une page hors limites affiche un lien vers la dernière page.
- Amplification SSR : une visite de `/:lang/forum` déclenche 2 appels API côté serveur (liste des fils, liste des établissements pour le filtre) et jusqu'à 1 de plus (`GET /schools/{id}`) si `?school=` désigne un établissement hors des 100 premiers ; `/:lang/forum/:id` en déclenche 2 (fil, réponses). Le rate limit applicatif ne couvre pas les lectures.
- Les corrections de revue (focus et `aria-live` sur le fil, suppression anti double envoi, 409 qui verrouille la page, bouton désactivé pendant le délai du 429, nettoyage ZWNJ/ZWJ conservés) sont vérifiées par specs seulement, pas au lecteur d'écran ni en navigateur réel.

