# 03: Vrai formulaire de contact

**What to build:** Aujourd'hui la page Contact et le bouton « Postuler maintenant » n'offrent qu'un lien `mailto:`. Un vrai formulaire (champs, envoi, anti-spam, école pré-remplie) demande un backend d'envoi.

**Blocked by:** Décision du propriétaire (SMTP/produit).

**Status:** resolved

- [x] Décision produit et SMTP consignée (Comments) : `MAIL_ENABLED`, destinataire, anti-spam, stockage ou non des messages.
- [x] Périmètre API et web défini avant tout code.

## Comments

2026-10-17 : décisions prises par le propriétaire (mail + base, champ piège + limite par IP, réseaux dans les paramètres du site, destinataire `CONTACT_RECIPIENT`). Le travail est repris par la spec `contact-rework` (4 tickets) ; ce ticket est clos sans code.
