# 05: Menu mobile : boutons Log in / Sign up trop serrés

**What to build:** Constat visuel du live test de main : dans le menu mobile (375 px), le texte des boutons « Log in » / « Sign up » est serré et noir. À confirmer dans le navigateur (clair/sombre), puis corriger l'espacement et le contraste.

**Blocked by:** None.

**Status:** resolved

- [x] Constat reproduit ou écarté, avec capture ou mesure.
- [x] Si confirmé : espacement et contraste corrects en clair et sombre, cibles ≥ 44 px.
- [x] Spec en échec d'abord, puis verte ; suite web et build verts.

## Comments

2026-10-17 : constat confirmé en navigateur réel (375 px, SSR, fr/en, clair/sombre). Cause : le panneau du menu est une colonne flex à `max-height` avec défilement ; ses boutons `btn-lg` (3 rem) étaient écrasés à 19 px de haut (cible tactile très inférieure à 44 px, texte collé au bord, « Créer un compte » coupé). Le contraste n'est pas en cause : texte blanc sur fond indigo pour « Se connecter », texte du thème (sombre en clair, clair en sombre) pour le bouton secondaire. Correctif : `.panel .btn { flex-shrink: 0 }` ; les boutons mesurent 51 px, le reste du menu défile.
