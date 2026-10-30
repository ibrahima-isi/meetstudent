# 01: Harnais Playwright de bout en bout sur la stack native

**What to build:** Un développeur lance une seule commande qui démarre la stack native (Postgres jetable, API en profil prod, web, back-office), exécute des parcours Playwright dans un vrai navigateur, puis arrête tout et nettoie. Un premier parcours fumée prouve que le harnais marche : connexion d'un étudiant sur le web, puis approbation d'un document par l'admin dans le back-office. Les tickets web suivants y ajoutent leurs parcours.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (harnais e2e) et « Testing Decisions » (bout en bout).

- [ ] Une commande documentée démarre la stack sur des ports libres (pas 4200/4300/8080/5432), avec des identifiants de test générés et jamais affichés
- [ ] Le parcours fumée passe : login étudiant (web), upload d'un document, approbation par l'admin (back-office)
- [ ] La commande arrête tous les processus et supprime le conteneur Postgres, même si un parcours échoue
- [ ] Le parcours échoue bien (rouge vu) si l'API est arrêtée ou si l'approbation ne change pas le statut
- [ ] Mode d'emploi court ajouté à la doc du dépôt ; aucun job CI requis n'est ajouté ni renommé

## Comments
