# 05: Premier déploiement réel et vérifications

**What to build:** La première promotion `dev` → `main` déploie la production via Coolify, et toutes les vérifications de la phase 6 de la feuille de route sont faites et consignées, y compris une restauration réelle.

**Blocked by:** [coolify-deploy/04](04-deploy-webhook-and-docs.md) ; côté propriétaire : phases 1, 2, 4 et 5 de la [feuille de route](../../../docs/deploy/coolify-roadmap.html) (VPS, Coolify, ressource, bucket R2)

**Status:** ready-for-human

**Worktree:** aucun (opérations sur la production)

Spec : [coolify-deploy](../spec.md), « Testing Decisions », point 3 ; [DEPLOY.md §9](../../../docs/DEPLOY.md) et la feuille de route.

- [ ] Promotion `git push origin dev:main` ; le run CI atteint le job `deploy`, les quatre images sont sur GHCR, le webhook répond
- [ ] Paquets GHCR passés en public
- [ ] Coolify tire les images `latest`, tous les conteneurs sont sains
- [ ] Certificats émis pour `DOMAIN` et `BO_DOMAIN`, `http://` redirige vers `https://`
- [ ] `docs/DEPLOY.md` §4 et §5 ; l'admin de `ADMIN_EMAIL` se connecte au back-office
- [ ] Depuis l'extérieur : HSTS et `X-Content-Type-Options` présents, une rafale d'une vingtaine de connexions finit en 429, un upload de 11 Mo est refusé
- [ ] Sauvegarde lancée à la main, puis restaurée dans une stack jetable ; un document uploadé s'ouvre
- [ ] Retour arrière testé une fois : `IMAGE_TAG=sha-<commit précédent>`, redéploiement, puis remise à `latest`
- [ ] Résultats consignés dans `.scratch/coolify-deploy/completion.md`

## Comments
