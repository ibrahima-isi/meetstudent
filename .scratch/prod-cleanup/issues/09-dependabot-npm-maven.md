# 09: Dependabot pour npm et Maven

**What to build:** Les mises à jour patch/minor arrivent toutes seules. `.github/dependabot.yml` gagne trois écosystèmes : npm pour `/apps/web`, npm pour `/apps/backoffice` et Maven pour `/apps/api`. Chacun est hebdomadaire, cible `dev`, ignore les montées de major (`update-types: ["version-update:semver-major"]`) et groupe les patch/minor en une PR par app. La config GitHub Actions existante n'est pas modifiée.

**Blocked by:** [prod-cleanup/06](06-api-patch-minor-upgrades.md), [prod-cleanup/07](07-web-patch-minor-upgrades.md), [prod-cleanup/08](08-backoffice-patch-minor-upgrades.md)

**Status:** resolved

Spec : [prod-cleanup](../spec.md), point 7. Bloqué par les montées manuelles pour éviter une rafale de PR en conflit.

- [x] Le fichier est un YAML valide au schéma Dependabot v2 (vérifié avec un validateur ou `gh api` après la fusion)
- [x] Les majors sont ignorés, les patch/minor groupés, `target-branch: dev`
- [x] Le bloc `github-actions` existant est inchangé

## Comments

Résolu : `.github/dependabot.yml` gagne npm `/apps/web`, npm `/apps/backoffice` et maven `/apps/api` (hebdo, `target-branch: dev`, majors ignorés via `dependency-name: "*"`, un groupe patch+minor par app, préfixes `chore(web)`, `chore(backoffice)`, `chore(api)`). Validé avant (fichier existant) et après contre le schéma SchemaStore `dependabot-2.0.json` (ajv-cli, YAML converti en JSON) : valide. Le bloc `github-actions` est inchangé (le diff ne contient aucune ligne supprimée).
