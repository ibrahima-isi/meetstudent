# Suivi : prod-cleanup

Spec : [spec.md](spec.md). Origine : grilling du 2026-10-30, avant `coolify-deploy/05`.

Règle : une entrée reste décochée jusqu'à ce que l'implémentation et sa vérification soient faites (builds et tests verts, CI verte). `Status: resolved` dans le ticket ne signale que la fin de l'implémentation ; la fusion se note à part.

- [x] [prod-cleanup/01](issues/01-remove-dead-files.md) : Supprimer le prototype React, les tests manuels de l'API et les dossiers vides. État : ready-for-agent. Fusionné : #255 9f64a52.
- [x] [prod-cleanup/02](issues/02-remove-historical-docs.md) : Supprimer completion legacy, evidence, superpowers, anciens plans (après vérification du plan deploy). État : ready-for-agent. Fusionné : #254 18030cc.
- [x] [prod-cleanup/03](issues/03-compact-claude-md.md) : Compacter les CLAUDE.md : règles stables, renvois, sans état daté ni doublon. État : ready-for-agent. Fusionné : #257 753bc2a.
- [x] [prod-cleanup/04](issues/04-centralize-memory.md) : Migrer les faits uniques de la mémoire perso dans le dépôt, puis la vider (sauf 1Password). État : resolved. Partie dépôt fusionnée (#262 96fcfc6) ; mémoire perso vidée le 2026-10-31 avec l’accord du propriétaire (la leçon 1Password est passée dans `~/.claude/CLAUDE.md`).
- [x] [prod-cleanup/05](issues/05-remove-unused-deps.md) : Retirer oauth2-resource-server, karma-coverage et karma-jasmine-html-reporter. État : ready-for-agent. Fusionné : #256 10e7dd4 (plugins karma gardés).
- [x] [prod-cleanup/06](issues/06-api-patch-minor-upgrades.md) : API : Spring Boot 3.5.x et dépendances épinglées en dernière patch/minor. État : ready-for-agent. Fusionné : #261 ed2bcbe.
- [x] [prod-cleanup/07](issues/07-web-patch-minor-upgrades.md) : Web : Angular 20.3.x et dépendances en dernière patch/minor. État : ready-for-agent. Fusionné : #259 1573f4a.
- [x] [prod-cleanup/08](issues/08-backoffice-patch-minor-upgrades.md) : Back-office : Angular 20.3.x et dépendances en dernière patch/minor. État : ready-for-agent. Fusionné : #258 3ef4374.
- [x] [prod-cleanup/09](issues/09-dependabot-npm-maven.md) : Dependabot : npm (web, back-office) et Maven (api), hebdomadaire, majors ignorés. État : ready-for-agent. Fusionné : #263 643b96e (check Dependabot config : success).
- [x] [prod-cleanup/10](issues/10-short-readmes.md) : Réécrire en court les READMEs du web et de l'API. État : ready-for-agent. Fusionné : #260 375a6b3.

Revue de code faite ; corrections dans cette PR.
