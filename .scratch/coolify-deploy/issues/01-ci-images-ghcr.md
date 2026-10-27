# 01: Images construites par la CI et publiées sur GHCR

**What to build:** Une PR prouve que les images de l'API, du web et du back-office se construisent ; un push sur `main` les publie sur GHCR, taguées par commit et `latest`. Aucun déploiement encore (webhook : [coolify-deploy/04](04-deploy-webhook-and-docs.md)).

**Blocked by:** aucun

**Status:** resolved

**Worktree:** `coolify-deploy-01` — branche `ci/coolify-images`

Spec : [coolify-deploy](../spec.md), user stories 2, 3, 4, 6, 8, 9 ; [ADR 0003](../../../docs/adr/0003-coolify-raw-compose-ghcr.md).

- [x] Sur `pull_request` : les trois images se construisent sans être poussées (back-office avec `DIST_DIR=dist/backoffice/browser`)
- [x] Job `deploy` dans `.github/workflows/ci.yml` : `needs: [api, web, backoffice]`, seulement sur push vers `main`, `environment: production`, permissions limitées à `packages: write` (+ `contents: read`)
- [x] Push GHCR avec `GITHUB_TOKEN` : `ghcr.io/ibrahima-isi/meetstudent-{api,web,backoffice}`, tags `sha-<commit>` et `latest`, en `linux/amd64` seulement
- [x] Les `name:` des trois jobs de tests restent inchangés (checks requis par les rulesets) ; le job `deploy` et le build sur PR ne deviennent pas des checks requis
- [x] Aucun secret dans les images : vérifier que les Dockerfiles ne copient ni `.env` ni fichier de secrets (les images seront publiques)
- [ ] Preuve : run CI vert sur la PR avec les trois builds d'images ; le push GHCR ne se vérifie qu'au premier push sur `main` ([coolify-deploy/05](05-first-production-deploy.md))

## Comments

### 2026-10-27 — implémentation (branche `ci/coolify-images`, commit `e69797b`)

- `docker-bake.hcl` (racine) : liste unique des images, en matrice (`meetstudent-api|web|backoffice`, contexte, build args). Tags `ghcr.io/ibrahima-isi/<image>:sha-${GITHUB_SHA}` et `:latest`, `linux/amd64`. Une validation refuse tout `GITHUB_SHA` qui n'est pas un SHA complet de 40 caractères (sinon un tag `sha-` vide partirait). Ticket 03 ajoute `meetstudent-backup` en une ligne de matrice (`{ name = "meetstudent-backup", context = "infra/backup", args = {} }`) et une ligne dans le test.
- `.github/workflows/ci.yml` : job `images / build` (PR seulement, `contents: read`, bake sans push) et job `deploy / production` (`needs: [api, web, backoffice]`, push sur `main` seulement, `environment: production`, `contents: read` + `packages: write`, login GHCR avec `GITHUB_TOKEN`, bake avec push). Le webhook Coolify s'ajoutera en dernière étape de `deploy` (ticket 04). Toujours appeler bake avec `-f docker-bake.hcl` : par défaut il fusionne aussi `compose.yml`.
- `infra/tests/ci-images.test.sh` : harnais bash (docker buildx, jq, yq, git ; image `rhysd/actionlint:1.7.12`). 7 tests : tags/plateforme/contextes/DIST_DIR résolus par `bake --print`, refus d'un SHA incomplet, contrat du job PR, contrat du job `deploy`, noms des checks requis inchangés, actionlint, aucun fichier secret suivi dans un contexte de build.
- Preuves locales : rouge → vert pour chaque test (sauf les gardes noms/actionlint/secrets, verts dès l'écriture ; le test secrets a été vu rouge en plantant `apps/web/.env` et `apps/backoffice/server.key` en intent-to-add). `GITHUB_SHA=<HEAD> docker buildx bake -f docker-bake.hcl` construit les trois images en local (exit 0) ; aucun `.env`, clé ou keystore dans les images finales (`find /` : seuls les bundles CA publics).
- Non vérifié : le run CI vert sur la PR (case restante), le push GHCR et `environment: production` réels (premier push sur `main`, ticket 05). L'environnement GitHub `production` doit exister (le créer, restreint à `main`, avant la promotion).

### 2026-10-27 — suivi après revue du propriétaire (branche `ci/coolify-images-followup`, commits `bb690d6`, `94b7798`)

- La définition des images passe de la racine à `infra/docker-bake.hcl` (décision du propriétaire). La CI et les tests lancent bake depuis la racine du dépôt (`docker buildx bake -f infra/docker-bake.hcl`), donc les contextes restent `apps/*`. Les mentions de `docker-bake.hcl` à la racine plus haut sont historiques.
- Le job `deploy / production` a son propre groupe de concurrence (`deploy-production`, `cancel-in-progress: false`) : les déploiements passent l'un après l'autre. L'en-tête du workflow (`cancel-in-progress: true` par branche) est inchangé et un test le protège. Limite : cet en-tête annule toujours tout le run précédent sur `main` quand un nouveau push arrive, `deploy` compris ; le groupe du job n'empêche pas cela.
- Le test du SHA complet exige maintenant le message de la validation : avant, un fichier bake absent le faisait passer quand même.
- Preuves : rouge → vert. Après le changement de chemin dans les tests : 4 tests en échec, puis le test du SHA durci en échec (`open infra/docker-bake.hcl: no such file or directory`) ; après `git mv` : 7/7 ok. Nouveau test `test_deploys_queue_instead_of_cancelling_each_other` en échec (`deploy concurrency` : `null`), puis vert. `infra/tests/ci-images.test.sh` → 8/8 ok, exit 0 ; `infra/tests/coolify-stack.test.sh static` → all passed, exit 0.
- L'environnement GitHub `production` (restreint à `main`) est créé par le propriétaire ; sa vérification appartient à coolify-deploy/05.

