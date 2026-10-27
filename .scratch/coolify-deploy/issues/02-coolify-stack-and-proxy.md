# 02: Stack Coolify et protections du proxy

**What to build:** Un `compose.coolify.yml` qui fait tourner l'API, le web, le back-office et Postgres derrière le Traefik de Coolify, avec les mêmes routes et protections que `compose.prod.yml`, et une PR qui valide ce fichier.

**Blocked by:** aucun (les images GHCR peuvent être remplacées par des images construites en local pour le test)

**Status:** resolved

**Worktree:** `coolify-deploy-02` — branche `ci/coolify-stack`

Spec : [coolify-deploy](../spec.md), user stories 7, 11 à 21, 30 ; [ADR 0003](../../../docs/adr/0003-coolify-raw-compose-ghcr.md) ; détail : [plan, phase 3b](../../../docs/plans/2026-10-26-deploy-hetzner-coolify.md).

- [x] `compose.coolify.yml` dérivé de `compose.prod.yml` : pas de service `traefik`, aucun `ports:`, images `ghcr.io/ibrahima-isi/meetstudent-<app>:${IMAGE_TAG:-latest}` avec `pull_policy: always` ; variables, volumes, healthchecks et `security_opt` identiques
- [x] Réseaux : réseau d'entrée = réseau externe du proxy Coolify ; `backend` reste `internal: true`, Postgres seul dessus
- [x] Labels de routes : mêmes règles et priorités qu'aujourd'hui, entrée HTTPS, résolveur Let's Encrypt et réseau de Coolify, en paramètres faciles à ajuster après vérification sur l'instance réelle
- [x] Middlewares partagés (`sec`, `strip-forwarded`, `rl-auth`, `rl-api`, `upload-limit`) et redirection HTTP → HTTPS dans un fichier de configuration dynamique Traefik versionné, référencés en `<nom>@file` ; jamais en labels de conteneur
- [x] `compose.prod.yml` reste intact
- [x] CI : sur PR, le fichier est validé (`docker compose -f compose.coolify.yml config` avec des variables factices)
- [x] Test « point 1 » de la spec : la stack en local derrière un Traefik configuré comme celui de Coolify (mêmes entrées, réseau externe, configuration dynamique, certificat local) ; routage single-origin, back-office sur son domaine, redirection HTTPS, HSTS et nosniff, rafale de connexions → 429, upload de 11 Mo refusé, SSR avec vraies données, Postgres injoignable
- [x] `docker compose down -v` après le test

## Comments

### 2026-10-27 — résolu (branche `ci/coolify-stack`, commits `d0b4f86`, `cda8468`)

Livré :
- `compose.coolify.yml` : dérivé de `compose.prod.yml`, sans `traefik` ni `ports:`, images `ghcr.io/ibrahima-isi/meetstudent-{api,web,backoffice}:${IMAGE_TAG:-latest}` en `pull_policy: always`. Paramètres du proxy à vérifier sur l'instance : `PROXY_NETWORK` (`coolify`), `PROXY_HTTP_ENTRYPOINT` (`http`), `PROXY_HTTPS_ENTRYPOINT` (`https`), `PROXY_CERT_RESOLVER` (`letsencrypt`).
- `infra/coolify/proxy/dynamic/meetstudent.yaml` : `sec`, `strip-forwarded`, `rl-auth`, `rl-api`, `upload-limit` et `https-redirect` (pas `redirect-to-https`, nom que le fichier dynamique de Coolify peut déjà porter). À copier dans `/data/coolify/proxy/dynamic/` (ticket 04 le documente). Les routeurs HTTP (un par domaine) sont des labels qui référencent `https-redirect@file`.
- Collision de noms sur le réseau partagé `coolify` : l'API et le web y sont, et tout autre service nommé `api` ou `db` y répondrait aussi. Résolu par des alias sur les réseaux internes de la stack : `meetstudent-db` (réseau `backend`, Postgres + API) et `meetstudent-api` (nouveau réseau interne `ssr`, web + API). `SPRING_DATASOURCE_URL`, `API_URL` et `SERVER_URL` les utilisent : ce sont les seules variables qui diffèrent de `compose.prod.yml`. Le service de sauvegarde (ticket 03) n'est que sur `backend`, il peut joindre `db` ou `meetstudent-db`.
- Harnais `infra/tests/coolify-stack.test.sh` : `static` (compose rendu en JSON, lancé par la CI) et `live` (stack réelle derrière une réplique du Traefik de Coolify : entrées `http`/`https`, provider fichier sur `dynamic/`, provider docker sans réseau par défaut, résolveur `letsencrypt` vers une CA injoignable donc certificat local auto-signé, plus un conteneur leurre qui porte les alias `api` et `db` sur le réseau du proxy).
- CI : job `compose / validate-coolify` (non requis) ; `api / build-and-test`, `web / build-and-test` et `backoffice / build-and-test` sont inchangés.

Preuves (machine locale, Docker 29.6.1, Compose v5.3.0, images locales retaguées en `ghcr.io/...:latest`, `--pull never`) :
- Rouge statique : `infra/tests/coolify-stack.test.sh static` sans le fichier → `FAIL the stack renders… open …/compose.coolify.yml: no such file or directory`, exit 1. Les mêmes assertions contre `compose.prod.yml` → 9 échecs (ports, proxy, images, réseau d'entrée, middlewares en labels).
- Rouge live : répertoire dynamique vide → 17 échecs (routeurs désactivés faute de `…@file` : 404 partout, pas de redirection, ni HSTS, ni 429, ni 413).
- Rouge collision : avec `db` à la place de `meetstudent-db`, l'API redémarre en boucle (elle résout le leurre) ; avec `api` dans `API_URL` seul → `FAIL SSR's API host (api) resolves to this stack's api only`.
- Vert : `infra/tests/coolify-stack.test.sh live` → 35 ok, `all passed`, exit 0 : routage single-origin (`/api` en JSON, `/uploads/public` répond par la 404 de l'API, `/fr` en SSR), back-office sur `BO_DOMAIN` avec `https://DOMAIN/api/v1`, `http://` → 301 vers `https://` pour les deux domaines, HSTS `max-age=31536000; includeSubDomains` + `nosniff` + `DENY` sur web, API et back-office, connexion admin puis école créée par l'API et visible dans le HTML du catalogue rendu côté serveur, rafale de 20 connexions → `4 401 16 429`, upload de 11 Mo → 413, Postgres joignable sur `backend` (contrôle) mais pas depuis le réseau du proxy, aucun port publié, `meetstudent-api` et `meetstudent-db` ne résolvent que vers les conteneurs de la stack.
- `git diff --exit-code compose.prod.yml` : aucun changement. `ci.yml` relu (YAML valide, 4 jobs, noms des trois jobs de tests identiques).
- Nettoyage : `down -v` par le `trap` du test à chaque exécution ; aucun conteneur, volume ni réseau `meetstudent-coolify-test` restant ; tags `ghcr.io/...:latest` retirés.

Non vérifié :
- Le job `compose / validate-coolify` n'a pas encore tourné sur GitHub (aucune PR ouverte).
- Le vrai Traefik de Coolify : noms d'entrées, de résolveur et de réseau, comportement du mode Raw (réseau ajouté par Coolify, prise en charge de l'ancre `x-logging`), émission réelle des certificats Let's Encrypt, conflit éventuel avec les fichiers dynamiques de Coolify (ticket 05).
- Le retrait de l'en-tête `Forwarded` n'est pas observable de l'extérieur et n'est pas testé ; le middleware est le même que dans `compose.prod.yml`.
- Les images testées sont des builds locaux d'il y a 7 à 8 jours, pas celles de la CI (ticket 01).
