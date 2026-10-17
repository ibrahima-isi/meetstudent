# Spec: écarts relevés par le live test final de #129

Status: needs-triage

## Problem Statement

Le live test final de dev (5428c0b, section #129 de `docs/completion.md`) a relevé trois écarts, laissés hors périmètre :

1. Les boutons d'étoiles de notation n'ont pas de nom accessible et portent `outline-none` (pas de focus visible).
2. Le bouton « Postuler maintenant » de la fiche établissement ne fait rien.
3. Le bouton « Créer un compte gratuit » en haut de l'accueil reste visible une fois connecté.

## Solution

Un ticket par écart, chacun en red → green sur le composant concerné, avec les règles de `apps/web/.claude/CLAUDE.md`.

## Out of scope

Le bloc d'inscription du bas de l'accueil (déjà masqué par #115) et toute nouvelle fonction de candidature au-delà de ce qui est décidé au ticket 02.
