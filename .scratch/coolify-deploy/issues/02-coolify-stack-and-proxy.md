# 02: Stack Coolify et protections du proxy

**What to build:** Un `compose.coolify.yml` qui fait tourner l'API, le web, le back-office et Postgres derrière le Traefik de Coolify, avec les mêmes routes et protections que `compose.prod.yml`, et une PR qui valide ce fichier.

**Blocked by:** aucun (les images GHCR peuvent être remplacées par des images construites en local pour le test)

**Status:** ready-for-agent

**Worktree:** `coolify-deploy-02` — branche `ci/coolify-stack`

Spec : [coolify-deploy](../spec.md), user stories 7, 11 à 21, 30 ; [ADR 0003](../../../docs/adr/0003-coolify-raw-compose-ghcr.md) ; détail : [plan, phase 3b](../../../docs/plans/2026-10-26-deploy-hetzner-coolify.md).

- [ ] `compose.coolify.yml` dérivé de `compose.prod.yml` : pas de service `traefik`, aucun `ports:`, images `ghcr.io/ibrahima-isi/meetstudent-<app>:${IMAGE_TAG:-latest}` avec `pull_policy: always` ; variables, volumes, healthchecks et `security_opt` identiques
- [ ] Réseaux : réseau d'entrée = réseau externe du proxy Coolify ; `backend` reste `internal: true`, Postgres seul dessus
- [ ] Labels de routes : mêmes règles et priorités qu'aujourd'hui, entrée HTTPS, résolveur Let's Encrypt et réseau de Coolify, en paramètres faciles à ajuster après vérification sur l'instance réelle
- [ ] Middlewares partagés (`sec`, `strip-forwarded`, `rl-auth`, `rl-api`, `upload-limit`) et redirection HTTP → HTTPS dans un fichier de configuration dynamique Traefik versionné, référencés en `<nom>@file` ; jamais en labels de conteneur
- [ ] `compose.prod.yml` reste intact
- [ ] CI : sur PR, le fichier est validé (`docker compose -f compose.coolify.yml config` avec des variables factices)
- [ ] Test « point 1 » de la spec : la stack en local derrière un Traefik configuré comme celui de Coolify (mêmes entrées, réseau externe, configuration dynamique, certificat local) ; routage single-origin, back-office sur son domaine, redirection HTTPS, HSTS et nosniff, rafale de connexions → 429, upload de 11 Mo refusé, SSR avec vraies données, Postgres injoignable
- [ ] `docker compose down -v` après le test

## Comments
