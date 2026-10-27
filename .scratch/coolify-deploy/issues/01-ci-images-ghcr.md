# 01: Images construites par la CI et publiées sur GHCR

**What to build:** Une PR prouve que les images de l'API, du web et du back-office se construisent ; un push sur `main` les publie sur GHCR, taguées par commit et `latest`. Aucun déploiement encore (webhook : [coolify-deploy/04](04-deploy-webhook-and-docs.md)).

**Blocked by:** aucun

**Status:** ready-for-agent

**Worktree:** `coolify-deploy-01` — branche `ci/coolify-images`

Spec : [coolify-deploy](../spec.md), user stories 2, 3, 4, 6, 8, 9 ; [ADR 0003](../../../docs/adr/0003-coolify-raw-compose-ghcr.md).

- [ ] Sur `pull_request` : les trois images se construisent sans être poussées (back-office avec `DIST_DIR=dist/backoffice/browser`)
- [ ] Job `deploy` dans `.github/workflows/ci.yml` : `needs: [api, web, backoffice]`, seulement sur push vers `main`, `environment: production`, permissions limitées à `packages: write` (+ `contents: read`)
- [ ] Push GHCR avec `GITHUB_TOKEN` : `ghcr.io/ibrahima-isi/meetstudent-{api,web,backoffice}`, tags `sha-<commit>` et `latest`, en `linux/amd64` seulement
- [ ] Les `name:` des trois jobs de tests restent inchangés (checks requis par les rulesets) ; le job `deploy` et le build sur PR ne deviennent pas des checks requis
- [ ] Aucun secret dans les images : vérifier que les Dockerfiles ne copient ni `.env` ni fichier de secrets (les images seront publiques)
- [ ] Preuve : run CI vert sur la PR avec les trois builds d'images ; le push GHCR ne se vérifie qu'au premier push sur `main` ([coolify-deploy/05](05-first-production-deploy.md))

## Comments
