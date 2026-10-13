# Issue tracker: Local Markdown

New issues and specs for MeetStudent live as versioned Markdown files under `.scratch/`. GitHub remains the PR review and delivery surface defined in the root working agreement.

## Conventions

- One feature directory: `.scratch/<feature-slug>/`.
- Spec: `.scratch/<feature-slug>/spec.md`.
- One file per ticket: `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, numbered in dependency order.
- Canonical ticket identity: `<feature-slug>/<NN>`; numbers alone may repeat between features.
- Record `Status:` near the top using the configured triage vocabulary. During execution, record `claimed` or `resolved` where appropriate.
- Record `Blocked by:` near the top, using local ticket identities and relative Markdown links. External legacy blockers may use their GitHub URL.
- Append discussion under `## Comments`; preserve existing content and decisions.
- Keep local specs, tickets and the completion document in Git so future worktrees/sessions receive them.

## Mandatory completion tracking

Every ticket creation MUST immediately create or update the repository-root `docs/completion.md`. This applies to any ticket-creating skill or manual workflow in this project, including `to-tickets`, `triage` and wayfinding.

For each ticket, in this order:

1. Write the ticket file.
2. Create `docs/completion.md` if absent, or preserve and update it if present.
3. Add exactly one unchecked Markdown checklist entry with the canonical ticket identity, title, link and initial state. Save it before creating another ticket or reporting creation complete.

A ticket without its saved completion entry is incompletely created. If either write fails, report the failure and reconcile that ticket before proceeding; never defer tracking until implementation begins or until the end of a batch.

When claiming/resuming a ticket, read both its file and the completion document; keep its checkbox unchecked and record progress, blockers and the next action. Mark it checked only when the implementation and required verification are complete, and record actual test evidence and PR/delivery links. Set the ticket to resolved consistently; distinguish implementation completion from PR merge status. Reopening a ticket restores its unchecked state and records the reason.

Preserve all prior completed entries and evidence. Rerunning ticket creation updates the existing entry rather than duplicating it.

## Existing GitHub work

Spec #99 and its existing tickets #100–#129 remain GitHub records. No migration or duplication is implied. Resolve explicit references to those records with the GitHub CLI and preserve their original issue/PR workflow. New tickets use the local convention above unless the user explicitly instructs otherwise.

## Skill operations

- Publish a spec: write the local spec file.
- Publish a ticket: write its individual local file AND immediately save its completion entry as required above.
- Fetch a ticket: read its referenced local file; for a canonical identity, locate the corresponding feature and numbered file. Use GitHub for the explicit legacy references above.
- Change triage labels: update the ticket's Status field using `docs/agents/triage-labels.md`.

## Wayfinding operations

- Map: `.scratch/<effort>/map.md`, holding Notes / Decisions-so-far / Fog.
- Child: one numbered file in the effort's issues directory, with its Type and Status. Creating the child immediately updates the shared completion document.
- Blocking: local Blocked by links; a blocker is satisfied only when its implementation is resolved with the required verification.
- Frontier: select an open, unclaimed ticket whose blockers are satisfied; prefer the agreed priority, then ticket number. Check external session-release conditions explicitly.
- Claim: save Status: claimed before implementation, then update its completion progress.
- Resolve: append the result/evidence, set Status: resolved, update the completion checkbox, and add a concise result/link to the map's Decisions-so-far.
