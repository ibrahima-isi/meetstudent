# Suivi : p2-backlog

Spec : [spec.md](spec.md) — origine : audit du 2026-10-27 et correctifs P0/P1 (#227 à #241), revérifiés sur `922ff98`.

Règle : une entrée reste décochée jusqu'à ce que l'implémentation et sa vérification soient faites (tests rouges puis verts, CI verte). `Status: resolved` dans le ticket ne dit que la fin de l'implémentation ; la fusion se note à part.

- [ ] [p2-backlog/01](issues/01-e2e-harness.md) : Harnais Playwright de bout en bout sur la stack native. État : ready-for-agent.
- [ ] [p2-backlog/02](issues/02-api-error-contract.md) : Contrat d'erreur API : corps sur 400/404, statut de modération invalide en 400, rôle avec ou sans préfixe. État : ready-for-agent.
- [ ] [p2-backlog/03](issues/03-user-by-email-no-oracle.md) : Lecture d'un utilisateur par e-mail sans révéler l'existence du compte, e-mail encodé côté web. État : ready-for-agent.
- [ ] [p2-backlog/04](issues/04-school-catalogue-n-plus-one.md) : Catalogue d'écoles sans N+1, nombre de requêtes borné et testé. État : ready-for-agent.
- [ ] [p2-backlog/05](issues/05-rate-service.md) : Une seule implémentation transactionnelle de « une note par utilisateur et par cible ». État : ready-for-agent.
- [ ] [p2-backlog/06](issues/06-create-ignores-client-id.md) : Une création ignore l'id envoyé par le client. État : ready-for-agent.
- [ ] [p2-backlog/07](issues/07-entity-image-db-uniqueness.md) : Unicité en base des médias d'images d'entités (V38) et conflit en 409. État : ready-for-agent.
- [ ] [p2-backlog/08](issues/08-dead-code-and-logging.md) : Ménage : code mort, journalisation slf4j, configuration de test hors du jar. État : ready-for-agent.
- [ ] [p2-backlog/09](issues/09-web-robustness.md) : Web : OnPush partout, page école sans course, stockage local tolérant. État : ready-for-agent.
- [ ] [p2-backlog/10](issues/10-backoffice-tab-sync.md) : Back-office : synchronisation des jetons entre onglets. État : ready-for-agent.
- [ ] [p2-backlog/11](issues/11-cross-tab-refresh-lock.md) : Verrou de rafraîchissement partagé entre onglets (web et back-office). État : ready-for-agent.
- [ ] [p2-backlog/12](issues/12-logout-by-old-refresh-token.md) : Un logout avec un ancien refresh token ferme la Session. État : ready-for-agent.
- [ ] [p2-backlog/13](issues/13-web-csp-enforcing.md) : CSP du web bloquante avec nonce SSR et endpoint de rapport. État : ready-for-agent.
- [ ] [p2-backlog/14](issues/14-legacy-media-report.md) : Rapport admin des médias orphelins et hérités non conformes. État : ready-for-agent.
- [ ] [p2-backlog/15](issues/15-claude-md-project-state.md) : Mettre à jour « Project state » et les limites connues du CLAUDE.md. État : ready-for-agent.
