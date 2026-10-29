# 09: Web : OnPush partout, page école sans course, stockage local tolérant

**What to build:** Dans le web (application sans zone), tous les composants utilisent `OnPush` et l'affichage reste à jour. Un étudiant qui passe vite d'une école à l'autre voit toujours l'école demandée : la réponse tardive d'une page quittée est ignorée. Un `localStorage` abîmé (utilisateur illisible) ne bloque plus le démarrage : la session est vidée et l'utilisateur peut se reconnecter.

**Blocked by:** [p2-backlog/01](01-e2e-harness.md)

**Status:** ready-for-agent

Spec : [p2-backlog](../spec.md), « Implementation Decisions » (web). Composants concernés à l'audit : formulaire de connexion, accueil, profil, détail école, catalogue, panier de souhaits, étoiles de note.

- [ ] Les 7 composants sont `OnPush` ; specs vertes et rendu vérifié (garde dark-mode incluse)
- [ ] Détail école : navigation 7 → 8 avec réponse de 7 retardée → la page affiche 8 (spec rouge puis verte) ; repli `loadPrograms` mort corrigé ou supprimé
- [ ] Utilisateur stocké illisible → démarrage normal, session vidée (spec)
- [ ] Parcours Playwright : rechargement avec un stockage abîmé, puis connexion

## Comments
