# 04: Webhook de déploiement Coolify et guide

**What to build:** Une fois les images poussées, le job `deploy` demande à Coolify de déployer ; le guide de déploiement explique comment installer, déployer, revenir en arrière et restaurer avec Coolify.

**Blocked by:** [coolify-deploy/01](01-ci-images-ghcr.md), [coolify-deploy/02](02-coolify-stack-and-proxy.md), [coolify-deploy/03](03-encrypted-offsite-backup.md)

**Status:** resolved

**Worktree:** `coolify-deploy-04` — branche `ci/coolify-deploy-webhook`

Spec : [coolify-deploy](../spec.md), user stories 1, 5, 9, 10, 31 ; [ADR 0003](../../../docs/adr/0003-coolify-raw-compose-ghcr.md) ; détail : [plan, phases 4 et 6](../../../docs/plans/2026-10-26-deploy-hetzner-coolify.md).

- [x] Dernière étape du job `deploy`, après le push des images : `curl -fsS -H "Authorization: Bearer $COOLIFY_TOKEN" "$COOLIFY_WEBHOOK"` ; secrets lus depuis l'environnement GitHub `production` ; un échec du webhook rend le run rouge
- [x] Rien d'autre ne déclenche un déploiement : pas sur PR ni sur `dev`, et le runbook impose l'auto-déploiement Coolify désactivé
- [x] `docs/DEPLOY.md` : section Coolify (ressource Compose en mode Raw, variables, fichier de configuration dynamique du proxy, tâche planifiée `backup`, webhook et secrets GitHub, passage des paquets GHCR en public, retour arrière par `IMAGE_TAG=sha-<commit>` puis remise à `latest`, restauration)
- [x] `apps/api/docs/production.md` : variables de sauvegarde (R2, `crypt`)
- [x] Preuve : CI verte sur la PR (le job `deploy` ne s'y exécute pas) ; l'appel réel du webhook se vérifie dans [coolify-deploy/05](05-first-production-deploy.md) — fait : [run](https://github.com/ibrahima-isi/meetstudent/actions/runs/40594615837) sur la PR #225.

## Comments

### 2026-10-27 — implémentation (branche `ci/coolify-deploy-webhook`, commits `19840ed`, `30de070`, `982fce1`)

Livré (5 fichiers, sans compter ticket et suivi) :
- `.github/workflows/ci.yml` : dernière étape de `deploy / production`, juste après le push bake, `Ask Coolify to deploy` : `curl -fsS -L --max-redirs 0 -H "Authorization: Bearer $COOLIFY_TOKEN" "$COOLIFY_WEBHOOK"`, secrets `COOLIFY_TOKEN` / `COOLIFY_WEBHOOK` de l'environnement `production` passés par `env:` (aucune expression `${{ }}` dans le script). Écart volontaire avec la commande du ticket : `-L --max-redirs 0` refuse une redirection (avec `-fsS` seul, une 302, par exemple `http://` vers `https://`, laissait le run vert sans rien déployer). Nouvelle étape `Check the Coolify deployment guide` dans `compose / validate-coolify`. Noms des jobs inchangés.
- `infra/tests/ci-images.test.sh` : 4 tests. Contrat (dernière étape, juste après le push, env des secrets, aucun autre job ne mentionne `COOLIFY_*`) ; comportement : l'étape est exécutée comme sur le runner (`bash -eo pipefail`) contre un faux Coolify local (python `http.server`) : un seul appel avec `Bearer <token>` sur 200, run rouge sur 302/401/404/500, jamais le jeton dans la sortie.
- `infra/tests/deploy-docs.test.sh` (lancé par la CI) : chaque variable interpolée par `compose.coolify.yml` est nommée dans la section Coolify de `docs/DEPLOY.md`, chaque secret du job `deploy` aussi, chaque variable propre au service `backup` dans `apps/api/docs/production.md`, et les liens relatifs des deux guides existent.
- `docs/DEPLOY.md` §9 : chaîne de déploiement ; 9.1 copie de `infra/coolify/proxy/dynamic/meetstudent.yaml` dans `/data/coolify/proxy/dynamic/`, variables `PROXY_*`, alias `meetstudent-db` / `meetstudent-api` ; 9.2 ressource (dépôt public, `main`, Docker Compose, Raw, auto deploy off, tableau des variables dont `IMAGE_TAG`) ; 9.3 environnement GitHub `production` restreint à `main`, `COOLIFY_WEBHOOK`, `COOLIFY_TOKEN`, comportement du job (file `deploy-production`, jamais annulé sur `main`, pas un check requis), paquets GHCR en public (quatre) ; 9.4 tâche planifiée `15 3 * * *` / `backup` / `/usr/local/bin/backup.sh`, R2 EU + cycle de vie 30 jours, `BACKUP_CRYPT_*` en clair dans Coolify et le gestionnaire de mots de passe (obscurcis par les scripts), réseau `egress` ; 9.5 restauration (`stop api`, `run --rm -v …:/restore/uploads -v …:/restore/private backup restore.sh latest --yes`, `start api`, noms de projet et de volumes de Coolify à relever sur l'instance) ; 9.6 retour arrière `IMAGE_TAG=sha-<40 caractères>` puis `latest`, Flyway ; 9.7 routine.
- `apps/api/docs/production.md` : section « Backups (Coolify stack) » (`R2_*`, `BACKUP_CRYPT_*`, exit 2 si incomplet).

Preuves (machine locale, macOS, yq v4.54.1, actionlint `rhysd/actionlint:1.7.12` en conteneur) :
- Rouge webhook : sans l'étape → `FAIL` sur le contrat (env `null`, push pas juste avant, aucun job `COOLIFY_*`), sur l'appel (exit 1, aucune requête reçue) et sur l'échec (« Coolify was never called » ×3) ; 8 assertions. Première version de l'étape en scalaire simple (`run: curl … "Authorization: Bearer …"`) → YAML invalide, 27 assertions rouges dont actionlint ; corrigée en bloc `|`.
- Mutations vues rouges : `curl -v` → `FAIL test_webhook_step_never_prints_the_token` ; sans `-f` → run vert sur 401/404/500 ; `-fsS` seul → `webhook answering 302 left the step green`.
- Rouge docs : `infra/tests/deploy-docs.test.sh` avant les guides → 9 assertions (pas de section Coolify, `COOLIFY_TOKEN`, `COOLIFY_WEBHOOK`, six variables de sauvegarde). Mutations : lien ADR cassé et `PROXY_CERT_RESOLVER` retiré → rouges.
- Vert : `infra/tests/ci-images.test.sh` → 12/12 ok (actionlint compris), exit 0 ; `infra/tests/deploy-docs.test.sh` → 4/4 ok ; `infra/tests/coolify-stack.test.sh static` → all passed. Aucun serveur de test restant (`pgrep`), rien d'autre démarré.

Non vérifié :
- CI verte sur la PR (case restante) : aucune PR ouverte, la branche part de `feat/coolify-deploy`. `deploy-docs.test.sh` suppose `yq` (mikefarah v4) présent sur `ubuntu-latest`.
- L'appel réel du webhook, l'environnement `production` et ses secrets, les quatre paquets GHCR en public : coolify-deploy/05.
- Sur l'instance : chemin `/data/coolify/applications/<uuid>/docker-compose.yaml`, nom du projet et des volumes (préfixe UUID possible) dans la commande de restauration ; libellés de l'interface Coolify (Webhooks, Scheduled Tasks, Raw) ; injection des variables de Coolify en mode Raw.
- Point ouvert pour le propriétaire : avec l'option « tunnel SSH seulement » du dashboard, le webhook est injoignable depuis GitHub et chaque job `deploy` échoue à sa dernière étape ; il faut alors l'option nom d'hôte HTTPS (documenté en 9.3). Pas de `--max-time` sur le `curl` : un Coolify qui ne répond pas bloque le job jusqu'au délai de GitHub.
