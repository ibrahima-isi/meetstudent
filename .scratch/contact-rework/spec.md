# Spec: refonte du module Contact

Status: ready-for-agent

Reprend et remplace `post-129-followups/03` (needs-info) : les décisions du propriétaire sont prises.

## Problem Statement

La page Contact n'offre qu'un lien `mailto:`. Le propriétaire veut un vrai formulaire au design soigné (e-mail de l'expéditeur, sujet, message) qui envoie un mail automatiquement, et des boutons vers les réseaux sociaux : WhatsApp, Telegram, Discord, Facebook, X (Twitter), Instagram, TikTok.

## Decisions (propriétaire, 2026-10-17)

- Chaque message part par e-mail **et** est conservé en base, relisible dans le back office.
- Anti-spam : champ piège (`website`) plus limite par IP. Champ piège rempli : l'API répond 202 comme si c'était accepté, sans rien enregistrer ni envoyer.
- Les 7 liens de réseaux sont dans les paramètres du site, éditables en back office. Un bouton sans lien renseigné n'est pas affiché. Une chaîne vide efface un lien (pour ces 7 champs seulement).
- Destinataire : variable d'environnement `CONTACT_RECIPIENT`, par défaut `contact@meetstudent.com`, distincte de l'adresse affichée (`contactEmail` des paramètres du site).
- Le nom d'école de `?school=` préremplit le sujet (comportement actuel conservé, nom assaini, 100 caractères).

## Solution

Quatre tickets, une PR chacun, red → green, fusionnés dans `dev` quand la CI est verte : 01 API messages, 02 liens des réseaux (API + back office), 03 liste des messages au back office, 04 page Contact web.

## Out of scope

Réponse aux messages depuis le back office, pièces jointes, captcha externe, envoi de mails à l'expéditeur (accusé de réception).
