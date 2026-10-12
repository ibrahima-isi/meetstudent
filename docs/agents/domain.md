# Domain Docs

## Before exploring

- Read the root `GLOSSARY.md` for shared domain vocabulary.
- Read relevant decisions in root `docs/adr/`.
- If a referenced glossary or ADR directory is absent, proceed silently. Domain-modeling creates documentation when terms or decisions are resolved.

## Layout

Single-context: `GLOSSARY.md` and `docs/adr/` at the repository root, shared by `apps/api`, `apps/web` and `apps/backoffice`.

## Vocabulary and decisions

Use glossary terms in issue titles, proposals, hypotheses and test names. If a needed concept is missing, check whether existing vocabulary fits; otherwise note the gap for domain-modeling.

Explicitly flag conflicts with existing ADRs, naming the decision and explaining why it should be reopened.
