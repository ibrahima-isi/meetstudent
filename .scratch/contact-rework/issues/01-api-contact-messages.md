# 01: API : messages de contact

**What to build:** `POST /api/v1/contact` (public) : e-mail de l'expéditeur, sujet, message, champ piège `website`, langue optionnelle. Le message est enregistré (migration `V25__create_contact_messages.sql`, entité, repository, service) puis envoyé par e-mail à `CONTACT_RECIPIENT` après le commit, via un modèle dédié (pas `AccountEmailRequested`, propre aux comptes). Limite par IP (429 + `Retry-After`) en plus de la limite Traefik. Champ piège rempli : 202, rien enregistré ni envoyé. Validation : 400 `{champ: message}`, longueurs bornées. Route ajoutée à `WebSecurityConfig` avant `anyRequest().authenticated()`.

**Blocked by:** None.

**Status:** ready-for-agent

- [ ] 202 pour un message valide : une ligne en base et un mail capturé au bon destinataire, sujet et corps corrects.
- [ ] Champ piège rempli : 202, aucune ligne, aucun mail.
- [ ] Validation : 400 par champ (e-mail invalide, sujet ou message vide ou trop long).
- [ ] Limite par IP : la requête en trop répond 429 avec `Retry-After`.
- [ ] Mail désactivé (`MAIL_ENABLED=false`) : le message est quand même enregistré, la réponse reste 202.
- [ ] Migration vérifiée sur PostgreSQL ; tests en échec d'abord, puis `./mvnw clean verify` vert.
