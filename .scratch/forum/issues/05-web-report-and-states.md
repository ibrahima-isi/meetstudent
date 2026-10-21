# 05: Signalement et états côté web

**What to build:** L'utilisateur signale un message depuis le site et voit clairement les fils verrouillés et les messages masqués.

**Blocked by:** [02](02-web-forum-pages.md), [03](03-api-reports-moderation.md)

**Status:** resolved

**Worktree:** `forum-05`

- [x] Bouton « Signaler » avec motif ; message de confirmation ; doublon (409) expliqué
- [x] Fil verrouillé : formulaire de réponse remplacé par une explication (fait par forum/02, revérifié : l'avis s'affiche aussi à l'anonyme)
- [x] Message masqué remplacé par un libellé neutre : sans objet, voir ci-dessous (pas de code mort)
- [x] fr/en, mode sombre ; specs, rouge d'abord

## Résultat

Branche `feat/web-forum-report`, uniquement `apps/web` (plus docs). Nouveau composant `forum-shared/forum-report` (bouton « Signaler » + formulaire de motif) utilisé sur le fil et sur chaque réponse de `/:lang/forum/:id` ; `ForumService.reportThread|reportReply` (`POST .../report`, corps `{reason}`, 201 sans corps) ; `reason` ajouté aux champs 400 reconnus ; i18n `forum.report.*` fr + en.

Comportement :
- Visible pour étudiant, expert approuvé, **en attente ou rejeté** (l'API l'autorise : tout connecté non admin), jamais sur son propre message (`mine`), jamais pour l'admin (403 côté API), pas par message pour l'anonyme (la porte de connexion du bas de page l'invite déjà). Visible aussi sur un fil verrouillé.
- Motif : requis, non vide, ≤ 255 avec compteur, sans caractères de contrôle ; texte envoyé tel quel (rogné), jamais réaffiché ; chargement + bouton désactivé (un seul envoi), `aria-busy`.
- Succès : formulaire fermé, « Merci… » dans une région `role="status"` polie, bouton devient « Signalé » (`aria-disabled`), focus rendu au bouton. Annuler : focus rendu au bouton.
- 409 : « déjà signalé, ou c'est votre propre message » (l'API a deux causes avec le même statut ; message unique plutôt que de deviner d'après le texte anglais) et le bouton passe à « Signalé ». 404 : « ce message n'existe plus », bouton retiré. 400 `{reason}` : sous le champ (`aria-invalid`, focus). 429 : secondes de `Retry-After` (sinon « une minute »), bouton désactivé le temps du délai, texte conservé. 401 : session terminée. 403 et autres : alerte, texte conservé, nouvel essai possible.

États vérifiés :
- Fil verrouillé : le formulaire de réponse est remplacé par l'avis (forum/02), y compris sans session (spec existante) ; badge « Masqué » pour l'admin (spec existante).
- Message masqué pour les non-admins : **cas impossible**, rien à coder. L'API ne renvoie jamais un message masqué à un non-admin (liste et lecture filtrées, 404 sur le fil masqué ou ses réponses) ; seul l'admin reçoit `hidden: true`. Un libellé neutre serait du code mort ; à reprendre si l'API change.

Preuves réelles :
- `npm test -- --no-watch --browsers=ChromeHeadless` : 1618/1618 verts (1584 avant) ; `npm run build` (SSR) OK (seul avertissement : budget CSS de `dock-navbar`, déjà présent).
- Rouge d'abord : les specs (composant, service `reportThread|reportReply`, `reason` dans `classifyForumError`, 7 cas de page) ont été lancées avant le code : échec de compilation (module `forum-report.component` introuvable). Deux erreurs de mes specs corrigées ensuite (URL `replys`, portée du sélecteur).
- Specs XSS : motif `<img onerror>` + `<script>` envoyé tel quel ; aucun élément `img`/`script` créé, aucun effet de bord sur `window`.

Non vérifié : navigateur réel (rendu, 375 px, mode sombre au-delà de la garde de classes), lecteur d'écran (focus et `aria-live` vérifiés par specs seulement), API réelle (contrats lus dans `ForumController`/`ForumReportService`, pas appelés), hydratation avec session, expiration du délai 429 en temps réel (horloge simulée).

Limites connues :
- Le bouton « Signalé » n'est pas mémorisé : après rechargement, un nouveau signalement répond 409 et affiche le message de doublon.
- Pas de bouton « Signaler » par message pour l'anonyme (invite unique en bas de page).
