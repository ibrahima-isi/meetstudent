# 04: Web : nouvelle page Contact

**What to build:** Page `/:lang/contact` refaite : formulaire au design soigné (e-mail, sujet, message, champ piège caché), envoi vers `POST /api/v1/contact`, états chargement / succès / erreur (400 par champ, 429, réseau) ; boutons des réseaux (WhatsApp, Telegram, Discord, Facebook, X, Instagram, TikTok) lus dans les paramètres du site, un bouton sans lien n'est pas affiché ; `?school=` préremplit le sujet comme aujourd'hui. fr/en, clair/sombre, mobile.

**Blocked by:** [contact-rework/01](01-api-contact-messages.md), [contact-rework/02](02-site-settings-social-links.md).

**Status:** ready-for-agent

- [ ] Formulaire accessible (labels, erreurs reliées aux champs, `aria-live` pour le résultat), champ piège invisible et hors tabulation.
- [ ] Succès : message de confirmation et formulaire vidé ; 400 : erreurs par champ ; 429 et réseau : message adapté, formulaire conservé.
- [ ] 7 boutons : liens `https` valides, `rel="noopener noreferrer"`, nom accessible ; absents quand le lien est vide.
- [ ] `?school=` préremplit le sujet (assaini) ; fr/en dans les deux fichiers i18n.
- [ ] Tests en échec d'abord, puis suite web et build verts ; vérifié en navigateur réel (clair/sombre, 375 px et bureau).
