# Ticket #103 — preuves frontend (2026-10-14)

## Contrat et décisions

La durée et l'inactivité restent décidées par le serveur : aucun timer du navigateur ne prétend prolonger ni expirer une session. Les clients réagissent au 401, essaient un refresh unique partagé, puis terminent la session si le refresh échoue ou si la requête rejouée répond encore 401. Le nettoyage supprime token, refresh token et profil via le TokenService existant ; la synchronisation entre onglets web de #102 est conservée.

Dans chaque Angular app, SessionActivityService est initialisé par un environment initializer, avant les lectures de pages. Il écoute en capture click, keydown et input, uniquement dans le navigateur, et ignore les événements synthétiques (isTrusted=false). Un intent peut marquer **une** lecture GET métier authentifiée sous l'URL API configurée, dans la seconde qui suit l'interaction ; cela couvre notamment la recherche après debounce 300 ms. L'intent est consommé une fois. Les lectures initiales, les lectures automatiques sans intent, les appels /auth, les assets et les URLs externes ne sont pas marqués. Aucun polling périodique n'existe actuellement dans ces applications. Cette heuristique ne prétend pas établir une causalité entre événement et requête ; un futur polling devrait explicitement conserver son exclusion même lorsqu'une interaction intervient en parallèle.

L'interceptor d'activité précède le refresh : une lecture marquée garde son header lors du retry, et la requête /auth/refresh n'est jamais marquée. Les mutations métier restent comptées par le serveur sans header.

Après expiration, le web revient vers le login fr/en actif, le backoffice vers /login, avec reason=expired et le returnUrl de la page interrompue (sans retour vers le login lui-même). Les pages invitent explicitement à se reconnecter. Le refresh interceptor laisse les 401 SSR traverser sans accéder au stockage navigateur ni lancer de navigation. Les listeners d'activité sont retirés lors de la destruction.

## TDD observé

Les tests sont aux frontières HTTP Angular et rendu des écrans login, sans accès à une méthode privée. La source d'événements DOCUMENT est contrôlée pour simuler les événements trusted que seul le navigateur peut émettre.

| Cycle rouge avant implémentation | Web | Backoffice |
| --- | --- | --- |
| Session terminée après second 401, redirect expired, invitation rendue | 4 échecs / 43 tests | 3 échecs / 19 tests |
| Header après intent réel, intent consommé, recherche debouncée | 3 échecs / 8 tests | 3 échecs / 8 tests |
| 401 SSR sans logout navigateur | 1 échec / 16 tests | 1 échec / 10 tests |

Les logs rouges sont /tmp/103-{web,bo}-{expiry,activity,ssr}-red.log. Le premier lancement sandbox des tests s'est arrêté dans les workers natifs esbuild ; les relances autorisées hors sandbox ont permis les rouges fonctionnels ci-dessus.

## Validation sur le code final

Node 22.21.1, Google Chrome Headless 152 ; npm ci exécuté séparément dans chaque app.

| Commande (depuis l'app concernée) | Web | Backoffice |
| --- | --- | --- |
| npm test -- --no-watch --browsers=ChromeHeadless | **940 succès**, exit 0 | **286 succès**, exit 0 |
| npx tsc --noEmit -p tsconfig.app.json | exit 0 | exit 0 |
| npm run build | SSR/browser générés, exit 0 | SPA générée, exit 0 |

Environnement des commandes : PATH=/Users/ibrahimadiallo/.nvm/versions/node/v22.21.1/bin:$PATH, NG_BUILD_MAX_WORKERS=2, CHROME_BIN=/Applications/Google Chrome.app/Contents/MacOS/Google Chrome (tests uniquement).

Logs finaux : /tmp/103-{web,backoffice}-{full,types,build}.log. git diff --check est vert. Les preuves navigateur ici sont Karma/Chrome et le rendu Angular ; aucun scénario Playwright connecté à une API réelle n'a été exécuté dans ce volet frontend. La preuve serveur d'expiration/inactivité appartient au volet API et la preuve CI finale est conservée dans le bilan global #103.
