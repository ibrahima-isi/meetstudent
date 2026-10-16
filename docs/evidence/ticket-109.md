# Ticket #109 — favoris étudiants masqués aux enseignants

Base isolée : squash #108 sur `dev` à `502f489ef72bd079524b5ae6e6f697e213c920be`, branche `fix/hide-teacher-wishlist-109`.

## TDD et comportement

- Tests rouges observés pour huit cas : le service chargeait la wishlist d’un enseignant et conservait celle d’un étudiant après changement de rôle sur le même compte ; accueil, fiche établissement, profil et barre de navigation affichaient encore les commandes étudiantes.
- `WishlistService` n’accepte le chargement et les mutations que pour `ROLE_STUDENT`. La clé de propriétaire inclut l’identifiant et le rôle ; un changement de session/rôle efface la liste et invalide les réponses en cours. Un test avec `HttpTestingController` confirme zéro requête utilisateur/favori pour l’enseignant, puis un GET étudiant après retour au rôle étudiant.
- Accueil et fiche établissement cachent les cœurs et ne lancent aucun chargement pour l’enseignant ; le panier disparaît de la navigation et la section favoris du profil suit le rôle de la session. Les parcours étudiants et l’invite de connexion anonyme restent disponibles.

## Vérifications provisoires

- 957 tests web headless, contrôle TypeScript et build SSR verts après correction de revue.
- Suites et builds API/backoffice inchangés ; leur CI sur la PR vérifiera le head final.

Revues Sol Standards/Spec finales sans écart après centralisation du rôle dans `WishlistService.available`. [CI API/web/backoffice verte](https://github.com/ibrahima-isi/meetstudent/actions/runs/38890633643) sur `71515c8`. Le présent closeout documentaire doit passer les trois CI sur son head final avant squash de la [PR #140](https://github.com/ibrahima-isi/meetstudent/pull/140) vers `dev`. La panne temporaire de signature 1Password a été résolue avant fusion ; la branche est reconstruite en un commit signé et sera vérifiée sur GitHub. Arrêt prévu après #109 ; #110 hors de cette demande.
