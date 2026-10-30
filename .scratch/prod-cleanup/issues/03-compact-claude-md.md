# 03: Compacter les CLAUDE.md (règles stables et renvois)

**What to build:** Un agent qui ouvre le dépôt lit des règles stables, sans état daté ni doublon. Le `CLAUDE.md` racine garde le working agreement, la carte du dépôt, les commandes et le fonctionnement des deux apps ensemble, et renvoie vers les guides d'app pour le reste. La section « Project state (…) and lessons » perd son état daté (hash de commit, dates de smoke tests) ; seules les limites connues et les leçons de process encore valables restent, sous un titre non daté. Les « API contract quirks » ne vivent plus qu'à un seul endroit, dans `apps/api/CLAUDE.md` (ou un doc de `apps/api/docs/` vers lequel il renvoie), et la racine n'y fait qu'un renvoi. Les faits répétés entre la racine, `apps/api/CLAUDE.md` et `apps/web/.claude/CLAUDE.md` n'apparaissent plus qu'une fois. Objectif : environ la moitié de la taille actuelle (23 Ko + 37 Ko).

**Blocked by:** [prod-cleanup/01](01-remove-dead-files.md), [prod-cleanup/02](02-remove-historical-docs.md)

**Status:** resolved

Spec : [prod-cleanup](../spec.md), point 3. Remplace la partie CLAUDE.md de [p2-backlog/15](../../p2-backlog/issues/15-claude-md-project-state.md) : à la fusion, ajouter un commentaire dans ce ticket qui le signale, sans le fermer à la place du propriétaire.

- [x] Plus aucun hash de commit ni « Project state (date) » dans les CLAUDE.md
- [x] Chaque règle non négociable du working agreement est toujours présente, mot pour mot ou plus courte, jamais affaiblie
- [x] Les quirks du contrat API existent à un seul endroit ; la racine y renvoie
- [x] Tailles avant/après notées dans la PR ; chaque lien relatif pointe vers un fichier existant
- [x] `apps/api/AGENTS.md` reste un symlink vers `apps/api/CLAUDE.md`

## Comments

- Tailles avant/apres (octets) : `CLAUDE.md` 22916 -> 18554, `apps/api/CLAUDE.md` 38019 -> 7747, `apps/web/.claude/CLAUDE.md` 2416 -> 1954. Les 38 Ko de detail API (media, mail, tokens, forum, experts, quirks) sont deplaces tels quels dans `apps/api/docs/reference.md` (33789), charge a la demande.
- Quirks du contrat API : uniquement dans `apps/api/docs/reference.md` ; la racine et `apps/api/CLAUDE.md` y renvoient.
- Working agreement (lignes 5-39 de la racine) : identique octet pour octet (diff vide). Plus de hash ni de "Project state" ; liens verifies ; `apps/api/AGENTS.md` toujours un symlink.
