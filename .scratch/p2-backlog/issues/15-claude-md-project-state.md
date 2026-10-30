# 15: Mettre à jour « Project state » et les limites connues du CLAUDE.md

**What to build:** Une session d'agent qui lit le `CLAUDE.md` racine y trouve l'état réel du projet : ce qui est livré (dont forum, approbation des Experts, Coolify, correctifs de l'audit #227 à #241), le commit de référence, les limites connues encore vraies et les étapes manuelles du premier déploiement. Les affirmations périmées sont retirées : `dev` = `main` sur `284ff82`, `infra/` décrit comme vide, « photo de programme non rendue », builds Docker jamais testés.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Problem Statement » (dernier point). Suivre la skill `writing-for-agents`.

- [ ] Section « Project state » réécrite avec la date et le commit de référence
- [ ] « Known gaps » : chaque point revérifié dans le code ; les points résolus sont retirés, les nouveaux ajoutés (CSP web en Report-Only tant que 13 n'est pas fait, limites des sessions entre onglets, etc.)
- [ ] Les liens vers `docs/DEPLOY.md`, les ADR et les specs pointent vers des fichiers existants
- [ ] `apps/api/CLAUDE.md` vérifié pour les mêmes affirmations périmées

## Comments
