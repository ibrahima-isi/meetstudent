# 01: Champs de la formation (description, places, frais, niveau)

**What to build:** L'administrateur renseigne description, places disponibles, frais annuels et niveau d'une formation depuis le back office ; l'API les stocke et les renvoie.

**Blocked by:** None (can start immediately)

**Status:** resolved

**Worktree:** `program-details-01`

- [x] Migration : colonnes optionnelles (description ≤1000, places entier ≥0, frais décimal ≥0 en euros, niveau liste fermée BTS/DUT, Licence, Master, Doctorat, Autre)
- [x] DTO et PATCH : null garde la valeur ; validation 400 par champ (négatifs, trop long, niveau inconnu)
- [x] Écriture ADMIN seulement ; lecture publique ; formations sans ces champs inchangées
- [x] Back office : formulaire de formation étendu
- [x] Contrat documenté (CLAUDE.md « API contract quirks » et note « no capacity… »)
- [x] Tests d'intégration et specs du formulaire, rouge d'abord
