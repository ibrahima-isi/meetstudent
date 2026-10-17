# 02: Liens des réseaux sociaux dans les paramètres du site

**What to build:** 7 champs optionnels dans les paramètres du site (WhatsApp, Telegram, Discord, Facebook, X, Instagram, TikTok) : migration `V26`, `SiteSettings`, `SiteSettingsDTO`, `UpdateSiteSettingsRequest`, service, lecture publique, édition au back office (`settings-page.ts`). URL `https` seulement, longueur bornée. Une chaîne vide efface un lien (null reste « inchangé »).

**Blocked by:** None.

**Status:** resolved

- [x] `GET /api/v1/site-settings` renvoie les 7 liens (null si non renseigné).
- [x] `PATCH` : chaîne vide efface, null garde, URL non `https` ou trop longue : 400 par champ.
- [x] Back office : 7 champs éditables, erreurs affichées, seuls les champs modifiés sont envoyés (specs ; pas de navigateur réel).
- [x] Migration vérifiée sur PostgreSQL ; tests API et back office en échec d'abord, puis verts, builds verts.

## Comments

2026-10-17 : noms JSON `whatsappUrl`, `telegramUrl`, `discordUrl`, `facebookUrl`, `twitterUrl`, `instagramUrl`, `tiktokUrl` (le ticket 04 les lit sous ces noms). Le schéma doit être `https` en minuscules (`HTTPS://x` est refusé), l'hôte ne peut pas contenir de caractère non ASCII ni de tiret bas (`java.net.URI` n'en extrait pas d'hôte).
