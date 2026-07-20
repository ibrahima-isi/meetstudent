# Design — Contrôle d'accès des documents média

**Date :** 2026-07-20
**Statut :** Approuvé (design), en attente de plan d'implémentation
**Périmètre :** Phase 1 (contrôle d'accès, rôles d'upload, modération, idempotence). Phase 2 (upload résumable par chunks) documentée mais hors périmètre.

## Problème / Objectif

Aujourd'hui, tout fichier uploadé est stocké sous `uploads/{entityType}/{uuid}.ext` et servi en **statique public** via le handler `/uploads/**` (`WebConfig`). Il n'existe aucune table média ni notion de propriétaire : les diplômes et certificats sont de simples `List<String>` d'URLs sur `UserEntity`. Conséquence : n'importe qui connaissant (ou devinant) une URL peut lire un diplôme ou un certificat d'un autre utilisateur, et l'admin n'a aucun moyen de modérer ou de vérifier l'authenticité des documents.

Objectif : rendre les documents personnels **privés** (accessibles au seul propriétaire et à l'admin), restreindre l'upload par rôle, introduire un workflow de modération d'authenticité, et rendre l'upload robuste aux retries.

## Décisions cadrées

- **Public vs privé.** Privés : `DIPLOMA`, `CERTIFICATE`, `BULLETIN` (à venir), `PRESENTATION_VIDEO`. Publics : `SCHOOL_LOGO`, `SCHOOL_COVER`, `USER_PHOTO`.
- **Droits d'upload.** Documents personnels : `STUDENT`, `EXPERT`, `ADMIN`. Médias d'école : `ADMIN` uniquement. Photo de profil : tout utilisateur authentifié. Le propriétaire d'un média personnel est **toujours** le principal JWT, jamais un identifiant fourni par le client.
- **Retry.** Phase 1 : idempotence + robustesse serveur (écriture atomique, pas d'orphelin). Phase 2 : upload résumable par chunks (séparé).
- **Modération.** Statut `PENDING → VERIFIED / REJECTED` avec motif, visible du propriétaire, non bloquant pour le reste de l'application à ce stade.
- **Fichiers existants.** Déplacés physiquement de `uploads/users/` vers `storage/private/` lors de la migration, pour couper l'accès statique.

## Architecture

### Entité `Media` (table `media`)

| Colonne | Type | Rôle |
|---|---|---|
| `id` | bigint PK | identifiant du média |
| `storage_key` | text | chemin relatif interne (ex. `private/{uuid}.pdf` ou `uploads/schools/{uuid}.png`), jamais exposé au client |
| `original_filename` | varchar | nom d'origine, pour affichage/téléchargement |
| `content_type` | varchar | MIME validé |
| `size_bytes` | bigint | taille |
| `category` | varchar (enum) | `DIPLOMA`, `CERTIFICATE`, `BULLETIN`, `PRESENTATION_VIDEO`, `SCHOOL_LOGO`, `SCHOOL_COVER`, `USER_PHOTO` |
| `visibility` | varchar (enum) | `PUBLIC` / `PRIVATE`, dérivée de la catégorie |
| `owner_id` | bigint FK users (nullable) | propriétaire ; `null` pour les médias d'école gérés par admin |
| `verification_status` | varchar (enum, nullable) | `PENDING` / `VERIFIED` / `REJECTED` ; `null` (non applicable) pour les médias publics |
| `rejection_reason` | text (nullable) | motif si `REJECTED` |
| `idempotency_key` | varchar (nullable) | dédup des retries |
| `created_at` / `updated_at` | timestamp | audit |

Contrainte unique partielle : `(owner_id, idempotency_key)` lorsque `idempotency_key` est non null.

La visibilité et l'applicabilité du statut de vérification sont **dérivées de la catégorie** (une seule source de vérité, via un enum `MediaCategory` portant `visibility` et `moderated`).

### Lien avec `UserEntity`

Les champs `diplomas` / `certificates` (`List<String>`), `presentationVideoUrl` et `photoUrl` sont remplacés par des références vers `Media`. Les diplômes et certificats deviennent une relation `@OneToMany` filtrée par catégorie (côté requête/service), la photo et la vidéo des références vers un `media.id`. Les médias d'école (logo, couverture) référencent aussi `media.id`.

### Stockage

- **Public** : reste sous `uploads/` (mappé par `WebConfig` `/uploads/**`).
- **Privé** : nouveau dossier `storage/private/`, **jamais** mappé par un handler de ressources statiques. Servi exclusivement par l'endpoint contrôlé.

### Endpoints

**Upload** — `POST /api/v1/media?category={CATEGORY}` (multipart, un fichier)
- Autorisation par catégorie (docs perso : STUDENT/EXPERT/ADMIN ; école : ADMIN ; photo : authentifié).
- Propriétaire = principal JWT pour les médias personnels.
- En-tête `Idempotency-Key` optionnel : si `(owner_id, idempotency_key)` existe déjà, renvoie le média existant (200) au lieu d'en créer (201).
- Réponse : `{ id, category, visibility, verificationStatus, originalFilename, contentType, sizeBytes }`. Aucun chemin disque exposé.

**Téléchargement** — `GET /api/v1/media/{id}`
- `PUBLIC` → accessible à tous, y compris non authentifié.
- `PRIVATE` → propriétaire ou ADMIN uniquement (403 sinon, 404 si média inexistant). Streame depuis `storage/private/` avec `Content-Disposition` et le vrai `Content-Type`.

**Modération** (ADMIN, `@PreAuthorize("hasRole('ADMIN')")`)
- `PATCH /api/v1/media/{id}/verification`, body `{ "status": "VERIFIED" }` ou `{ "status": "REJECTED", "reason": "..." }`.
- `GET /api/v1/media?status=PENDING` → file de modération paginée.

**Consultation propriétaire**
- `GET /api/v1/media/mine` → médias du principal avec statut et motif.

**Suppression** — `DELETE /api/v1/media/{id}` → propriétaire ou ADMIN ; supprime la ligne et le fichier disque.

### Sécurité (WebSecurityConfig)

- `GET /api/v1/media/{id}` : `permitAll` au niveau filtre (l'autorisation fine PUBLIC/PRIVATE se fait dans le service, car un média public doit rester accessible à l'anonyme et un privé exige propriétaire/admin).
- `POST /api/v1/media`, `DELETE`, `GET /media/mine` : authentifié.
- `PATCH /media/{id}/verification`, `GET /media?status=` : ADMIN (via method security).
- Le handler statique `/uploads/**` ne mappe que le dossier public ; `storage/private/` n'est jamais servi statiquement.

## Robustesse & gestion d'erreur

- **Écriture atomique** : écriture dans un `.tmp` puis rename atomique. Échec → suppression du `.tmp`, aucune ligne `media` créée. La ligne DB n'est persistée qu'après succès de l'écriture, dans la transaction de service.
- **Idempotence** : contrainte unique `(owner_id, idempotency_key)` ; retry avec même clé → 200 même média.
- **Validation** (réutilise `MediaService`) : whitelist extension/MIME par catégorie, taille max par catégorie (les vidéos autorisent des fichiers plus gros), magic bytes, nom UUID, garde anti-traversal.
- **Codes** : 201 créé / 200 retry idempotent / 400 invalide / 401 non authentifié / 403 rôle ou accès refusé / 404 média inexistant.

## Migration

- `V14__create_media_table.sql` : création de la table `media` et des contraintes.
- `V15__migrate_existing_media.sql` : pour chaque URL existante (`diplomas`, `certificates`, `presentation_video_url`, `photo_url`, logos/couvertures d'école), insertion d'une ligne `media` (catégorie et visibilité déduites, `PENDING` pour les docs privés existants, propriétaire = utilisateur), puis remplacement des références.
- **Déplacement des fichiers** : un composant de migration au démarrage (ou étape dédiée) déplace les fichiers privés existants de `uploads/users/` vers `storage/private/` et met à jour `storage_key`. Idempotent (ne re-déplace pas ce qui l'est déjà).

## Tests (action + opposé)

**Unitaires** (`MediaService`, service d'accès média)
- Autorisation upload par catégorie/rôle (autorisé et refusé).
- Propriétaire résolu = principal (et rejet d'un ownerId client divergent).
- Transitions de statut PENDING → VERIFIED / REJECTED.
- Dédup idempotente (même clé → même média ; clés différentes → médias distincts).
- Écriture atomique + rollback sur échec (pas de ligne ni de fichier orphelin).
- Garde anti-traversal.

**Intégration** (`MediaControllerIntegrationTests` étendu)
- Student/expert uploade un doc perso → 201 ; anonyme → 401 ; student uploade un logo d'école → 403.
- Téléchargement privé : propriétaire → 200 ; autre utilisateur → 403 ; admin → 200. Public : anonyme → 200.
- Retry même `Idempotency-Key` → 200, même id.
- Modération : admin VERIFIED/REJECTED → 200 ; non-admin → 403.
- `GET /media/mine` ne renvoie que les médias du principal.

## Phase 2 — hors périmètre (documentée)

Upload résumable par chunks (protocole type tus) : `POST /media/uploads` (création de session), `PATCH /media/uploads/{id}` (chunk avec offset), reprise sur coupure. Réutilise la table `media` (statut interne `INCOMPLETE` → `PENDING` à la finalisation). Spec et implémentation dans un cycle séparé.

## Hors périmètre explicite

- Upload multi-fichiers simultané (arrivera avec les bulletins).
- Blocage fonctionnel sur documents `REJECTED` (non bloquant pour l'instant).
- Antivirus / scan de contenu au-delà des magic bytes.
- Stockage objet distant (S3, etc.) — le design garde une abstraction de stockage permettant de l'ajouter plus tard.
