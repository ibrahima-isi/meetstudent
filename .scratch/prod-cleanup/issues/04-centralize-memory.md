# 04: Le dépôt, seule source de vérité de la mémoire du projet

**What to build:** Une session qui démarre ne dépend plus de la mémoire perso. Chaque fait de `~/.claude/projects/-Users-ibrahimadiallo-IdeaProjects-meetstudent/memory/` est comparé au dépôt. S'il est absent et toujours vrai (par exemple les hôtes Coolify et le VPS dans `docs/DEPLOY.md`, les suites de l'audit 2026-10-27 non couvertes par `p2-backlog`, la règle de promotion `origin/dev:refs/heads/main` dans `docs/MONOREPO.md`), il est ajouté au bon doc du dépôt. Un instantané d'état (hash, « dev en avance sur main ») est abandonné. Ensuite, les notes projet sont supprimées et `MEMORY.md` ne garde que la note `onepassword-commit-signing.md`.

**Blocked by:** [prod-cleanup/03](03-compact-claude-md.md)

**Status:** resolved. Repo part merged in #262; the `~/.claude` memory was cleared on 2026-10-31 with the owner's approval.

Spec : [prod-cleanup](../spec.md), point 4. La partie dépôt passe par une PR. La suppression dans `~/.claude` se fait hors Git, seulement après la fusion de cette PR et avec l'accord du propriétaire.

- [x] La PR contient un tableau : note mémoire → fait → destination dans le dépôt, ou « abandonné (instantané) »
- [x] Aucun secret, ni l'adresse IP du VPS si le propriétaire la refuse dans le dépôt (lui demander)
- [x] Après la fusion : dossier mémoire vidé le 2026-10-31 avec l’accord du propriétaire ; la note 1Password (partie générale) est passée dans `~/.claude/CLAUDE.md` au lieu de rester ici.

## Comments

Table: memory note -> fact -> destination.

| Note | Fact | Destination |
|---|---|---|
| audit-2026-10-27 | P0/P1 fixes, CSP, api-edge, refresh reuse, quotas | already in `apps/api/docs/reference.md`, `docs/DEPLOY.md` 9.1 |
| audit-2026-10-27 | lockout keyed only on NFKC+upper email | already in `apps/api/docs/reference.md` |
| audit-2026-10-27 | follow-ups (tab sync, nonce CSP, navigator.locks, logout by old hash, `id` in POST, image uniqueness, legacy media, N+1, empty 400/404, PATCH PENDING 500, role 400, rate logic, dead code, OnPush, stale CLAUDE.md) | already in `.scratch/p2-backlog` |
| audit-2026-10-27 | sort review leftovers (admin raw sort, ignorecase/`%` 500, no guard test) | added to `apps/api/docs/reference.md` |
| audit-2026-10-27 | test a private PDF under the CSP before first deploy | added to `docs/DEPLOY.md` section 9 |
| audit-2026-10-27 | hashes, "fixed in #252, not on main", Postgres flake | abandoned (snapshot) |
| deploy-setup | dashboard host, Cloudflare DNS-only, app/admin hosts, root MX/SPF untouched, dev env hosts and the 4 code changes | added to `docs/DEPLOY.md` section 9 (no IP, by owner decision) |
| deploy-setup | VPS IP, "being done step by step" | abandoned (owner decision / snapshot) |
| monorepo-state | no upstream, rulesets, 3 traps, legacy protection | already in `docs/MONOREPO.md` |
| monorepo-state | promote with `origin/dev:refs/heads/main`, `merge-base --is-ancestor`, deploy job fails until env exists | added to `docs/MONOREPO.md`; root `CLAUDE.md` and `docs/DEPLOY.md` corrected from `dev:main` |
| monorepo-state | `main`/`dev` hashes, promotion pending, smoke test 922ff98, stale CLAUDE.md | abandoned (snapshot) |
| working-agreement | branch per task, TDD, scope, cleanup | already in root `CLAUDE.md` ("more than 3 files" is superseded by 5) |
| working-agreement | findings listed before modifying, evidence over assertion | added to root `CLAUDE.md` (Implementation) |
| working-agreement | merge into local main + docker | abandoned (superseded by dev/main rules) |
| mandatory-branch-cleanup | cleanup rules, merge-tree test, 403 on remote delete | already in root `CLAUDE.md` |
| interactive-questions | AskUserQuestion always | already in root `CLAUDE.md` and `docs/MONOREPO.md` |
| completion-per-spec | one completion.md per spec | already in `CLAUDE.md`, `docs/MONOREPO.md`, `docs/agents/issue-tracker.md` |
| onepassword-commit-signing | signing tips | stays personal, not migrated |

Results: `bash infra/tests/deploy-docs.test.sh` passes (relative links of both guides resolve). Files touched: `CLAUDE.md`, `docs/MONOREPO.md`, `docs/DEPLOY.md`, `apps/api/docs/reference.md`, this ticket. Still to do by the owner after merge, outside Git: delete the project notes in `~/.claude/projects/-Users-ibrahimadiallo-IdeaProjects-meetstudent/memory/` and trim `MEMORY.md` to the `onepassword-commit-signing.md` entry.
