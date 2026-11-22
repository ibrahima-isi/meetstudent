# MeetStudent project memory

Apply the GitHub Flow policy below. Read [CLAUDE.md](CLAUDE.md) for the rest of the shared project memory, including engineering conventions, app-specific instructions, commands, known gaps, and ticket tracking. Read [docs/MONOREPO.md](docs/MONOREPO.md) before changing GitHub protection or CI.

## GitHub Flow

`main` is the only permanent local and remote branch. Use short-lived branches for features, fixes, releases and urgent work. Never commit, push, force-push, or merge locally into `main`. Every change reaches `main` through a GitHub PR with all required checks passing. Never bypass protection.

1. Check Git status and worktrees, then fetch `origin`. Start each task from the latest `origin/main` in a separate worktree. Use `feature/<description>`, `fix/<description>`, `refactor/<description>`, `chore/<description>`, or `docs/<description>` with a short kebab-case description. Include the Jira key when available, for example `fix/MS-123-media-upload`.
2. Implement and run the relevant checks. Write concise Conventional Commits with `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, or `ci` and an optional scope. Sign commits and push the working branch when appropriate.
3. Report the branch, changes, check results and PR readiness. A proposed PR title and body are optional. Create, open or submit a PR only when the owner explicitly requests it or explicitly invokes the `pr` skill. Never invoke that skill on your own. Task completion and a branch push do not authorize a PR.
4. When authorized, open the PR against `main`. If `main` moves, update the working branch and rerun CI. Merge through GitHub after the owner explicitly authorizes the merge and all checks pass. Prefer squash. Opening a PR does not authorize merging it. Keep automatic PR creation and automatic merging disabled.
5. After GitHub confirms the merge, fetch and synchronize local `main` with `git pull --ff-only origin main`. Check both local and remote heads against the merged PR before proposing deletion. A squash merge can change commit IDs. Verify the branch's current work is incorporated before deleting either copy. Get explicit approval before deleting any existing branch. Preserve unmerged work and any worktree in use or with uncommitted changes.
