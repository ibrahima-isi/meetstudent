# Issue tracker: GitHub

Issues and specs live in GitHub Issues for `ibrahima-isi/meetstudent`. Use the `gh` CLI with `--repo ibrahima-isi/meetstudent` for issue and PR commands; the Git remote uses the SSH alias `github-isi`.

## Conventions

- Create: `gh issue create --repo ibrahima-isi/meetstudent --title "..." --body-file <file>`. Save multiline bodies in a temporary file, preserving actual newlines.
- Read: `gh issue view <number> --repo ibrahima-isi/meetstudent --json number,title,body,labels,comments`.
- List: `gh issue list --repo ibrahima-isi/meetstudent --state open --json number,title,body,labels,comments`, adding label or state filters as needed.
- Comment: `gh issue comment <number> --repo ibrahima-isi/meetstudent --body-file <file>`.
- Apply/remove labels: `gh issue edit <number> --repo ibrahima-isi/meetstudent --add-label "..."` / `--remove-label "..."`.
- Close: `gh issue close <number> --repo ibrahima-isi/meetstudent`.
- Sub-issues: use the installed CLI's supported sub-issue commands. If unavailable, list children in the parent's task list and put `Part of #<parent>` at the top of each child.

## Pull requests as a triage surface

**PRs as a request surface: no.**

GitHub issues and PRs share a number space. Resolve an ambiguous number with `gh pr view`, then fall back to `gh issue view`.

## Skill operations

- Publish to the issue tracker: create a GitHub issue.
- Fetch the relevant ticket: read its issue body, labels and comments.

## Wayfinding operations

- Map: one issue labelled `wayfinder:map`, holding Notes / Decisions-so-far / Fog.
- Child: a sub-issue of the map, labelled `wayfinder:<type>` (`research`, `prototype`, `grilling`, `task`). Use the task-list fallback above when necessary.
- Blocking: use native GitHub issue dependencies. Add a blocker with `gh api --method POST repos/ibrahima-isi/meetstudent/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`. Obtain the database ID with `gh api repos/ibrahima-isi/meetstudent/issues/<number> --jq .id`. If unavailable, use a `Blocked by: #<number>` line in the child body.
- Frontier: among the map's open children, select the first in map order with no open blockers and no assignee.
- Claim: assign the selected child to the driving developer (`gh issue edit <number> --repo ibrahima-isi/meetstudent --add-assignee @me`).
- Resolve: comment with the result, close the child, then add a concise result and link to the map's Decisions-so-far.
