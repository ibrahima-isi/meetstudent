# 05: Premier déploiement réel et vérifications

**What to build:** La première promotion `dev` → `main` déploie la production via Coolify, et toutes les vérifications de la phase 6 de la feuille de route sont faites et consignées, y compris une restauration réelle.

**Blocked by:** [coolify-deploy/04](04-deploy-webhook-and-docs.md) ; côté propriétaire : phases 1, 2, 4 et 5 de la [feuille de route](../../../docs/deploy/coolify-roadmap.html) (VPS, Coolify, ressource, bucket R2)

**Status:** claimed (owner + agent, 2026-10-31; 6 of 9 checks done, backups/restore and rollback left)

**Worktree:** aucun (opérations sur la production)

Spec : [coolify-deploy](../spec.md), « Testing Decisions », point 3 ; [DEPLOY.md §9](../../../docs/DEPLOY.md) et la feuille de route.

- [x] Promotion `git push origin origin/dev:refs/heads/main` (voir `docs/MONOREPO.md`) ; le run CI atteint le job `deploy`, les quatre images sont sur GHCR, le webhook répond
- [x] Paquets GHCR passés en public
- [x] Coolify tire les images `latest`, tous les conteneurs sont sains
- [x] Certificats émis pour `DOMAIN` et `BO_DOMAIN`, `http://` redirige vers `https://`
- [ ] `docs/DEPLOY.md` §4 et §5 ; l'admin de `ADMIN_EMAIL` se connecte au back-office
- [x] Depuis l'extérieur : HSTS et `X-Content-Type-Options` présents, une rafale d'une vingtaine de connexions finit en 429, un upload de 11 Mo est refusé
- [ ] Sauvegarde lancée à la main, puis restaurée dans une stack jetable ; un document uploadé s'ouvre
- [ ] Retour arrière testé une fois : `IMAGE_TAG=sha-<commit précédent>`, redéploiement, puis remise à `latest`
- [ ] Résultats consignés dans `.scratch/coolify-deploy/completion.md`

## Comments

- 2026-10-31 : le nettoyage d'avant-prod [prod-cleanup](../../prod-cleanup/spec.md) est terminé et fusionné dans `dev` (#254 à #265) : fichiers et docs morts supprimés, dépendances patch/minor à jour (Spring Boot 3.5.16, Angular 20.3.34, `npm audit` à 0 dans le web et le back-office), Dependabot npm et Maven actif. Ce prérequis est levé ; avant la promotion, ouvrir aussi un PDF privé avec la CSP active ([DEPLOY.md §9](../../../docs/DEPLOY.md)).
- 2026-10-31 : **production en ligne**, sur `app.meetstudent.me` et `admin.meetstudent.me`. `main` = `dev` = `b20546b`.
  - **Mise en place.**
    - DNS chez Cloudflare en « DNS only », racine `meetstudent.me` non touchée (MX/SPF).
    - Pare-feu Cloud Hetzner : seuls 80 et 443 sont ouverts. SSH passe par Tailscale ; `ufw` est inactif et ne protégerait de toute façon pas les ports de Docker.
    - Coolify v4.5.3 :
      - 2FA, inscription fermée, mise à jour automatique coupée, notifications Discord ;
      - jeton API `github-deploy` limité à « Deploy », qui expire en octobre 2027 ;
      - proxy : fichier dynamique copié, réseau `meetstudent-api-edge` en 10.250.77.0/24, pas de `forwardedHeaders.insecure`.
    - Ressource : Raw, Auto Deploy coupé, Predefined Network coupé.
    - GitHub : environnement `production` restreint à `main`, avec ses 2 secrets.
  - **Trois problèmes trouvés au premier déploiement, tous corrigés et fusionnés.**
    - Le webhook répondait 405 : depuis la v4.5, Coolify exige un POST (#272).
    - Tous les hôtes répondaient 503 avec le certificat par défaut de Traefik : l'option Coolify « Escape special characters in labels » laissait `${DOMAIN}` tel quel dans les labels. Option à couper, doc et test ajoutés (#273).
    - Les pages étaient rendues côté navigateur : `@angular/ssr` 20.3 retombe en CSR sur les en-têtes `X-Forwarded-*` qu'il ne connaît pas. `trustProxyHeaders` est maintenant configuré (#274).
  - **Preuves.**
    - Run [41161613086](https://github.com/ibrahima-isi/meetstudent/actions/runs/41161613086) sur `fd7bd96` : images poussées, webhook en 405, puis déploiement manuel depuis Coolify.
    - Run [41165305825](https://github.com/ibrahima-isi/meetstudent/actions/runs/41165305825) sur `b20546b` : tous les jobs verts, déploiement automatique par le webhook.
    - Les 4 images `latest` se tirent de façon anonyme (manifest 200).
    - Les 5 conteneurs sont `healthy`, sauf `backup` qui n'a pas de healthcheck.
    - Certificats Let's Encrypt (YR2) pour les deux hôtes. `http://` répond 308 vers `https://`.
    - `/fr`, `/en/schools` et `/fr/forum` répondent 200 avec `ng-server-context="ssr"`.
    - HSTS, `nosniff`, `X-Frame-Options: DENY` et la CSP sont présents sur les deux hôtes.
    - 20 connexions ratées : `6×404 14×429`.
    - Upload de 11 Mo : 413. Un petit upload anonyme : 401.
    - Réseaux de l'API : `backend`, `ssr`, `meetstudent-api-edge` et le réseau interne `<uuid>` que Coolify crée pour la stack, mais pas `coolify`. Le réseau `<uuid>` ne réunit que les conteneurs de cette stack ; `docs/DEPLOY.md` §9.1 ne s'attend qu'à trois réseaux.
  - **Non vérifié.**
    - Connexion de l'admin `ADMIN_EMAIL` au back-office (`docs/DEPLOY.md` §4 et §5) : à faire par le propriétaire.
    - Ouverture d'un PDF privé avec la CSP active.
    - Test `docker compose up --build` en local après la promotion : la vraie prod a été testée à la place.
  - **Suite**, dans cet ordre :
    1. Le propriétaire se connecte au back-office et ouvre un PDF privé.
    2. Bucket R2 et variables `R2_*` et `BACKUP_CRYPT_*` (`docs/DEPLOY.md` §9.4), tâche planifiée Coolify, sauvegarde lancée à la main, puis restauration dans une stack jetable (§9.5).
    3. Retour arrière avec `IMAGE_TAG=sha-fd7bd96`, puis remise à `latest` (§9.6).
    4. Environnement dev (`demo`/`bo`), qui demande un changement de code non encore approuvé : noms de routeurs Traefik propres à chaque stack, tag d'image `dev`, job de déploiement pour `dev`, second réseau `api-edge`.
