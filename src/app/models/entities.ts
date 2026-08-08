export interface AbstractEntity {
  id?: number;
  createdAt?: string;
  modifiedAt?: string;
  createdBy?: number;
  modifiedBy?: number;
}

export interface BaseEntity extends AbstractEntity {
  code?: string;
  name: string;
}

export interface Role {
  id?: number;
  name: string;
  description?: string;
}

export interface Address {
  location: string;
  city: string;
  country: string;
}

export type MediaCategory =
  | 'DIPLOMA'
  | 'CERTIFICATE'
  | 'BULLETIN'
  | 'PRESENTATION_VIDEO'
  | 'SCHOOL_LOGO'
  | 'SCHOOL_COVER'
  | 'COURSE_PHOTO'
  | 'PROGRAM_PHOTO'
  | 'USER_PHOTO';

export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';

/**
 * A stored file. Uploads return this; entities reference it by id.
 *
 * `publicUrl` is set ONLY for PUBLIC media and is relative to the server root
 * (e.g. `/uploads/public/x.png`) — NOT to the `/api/v1` base. Resolve it with
 * `MediaUrlService.publicUrl()` rather than prefixing `environment.apiUrl`.
 *
 * PRIVATE media has `publicUrl: null` and must be fetched as a blob from
 * `GET /api/v1/media/{id}`; a plain `<img src>` sends no Authorization header
 * and gets a 403.
 */
export interface Media {
  id: number;
  category: MediaCategory;
  visibility: 'PUBLIC' | 'PRIVATE';
  verificationStatus: VerificationStatus | null;
  rejectionReason: string | null;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  publicUrl: string | null;
}

export interface User extends AbstractEntity {
  firstname: string;
  lastname: string;
  birthday?: string;
  email: string;
  role: Role;
  qualification?: string;
  /** Private media — fetch content via MediaService.blobUrl(id), not <img src>. */
  diplomas?: Media[];
  certificates?: Media[];
  presentationVideo?: Media | null;
  /** Still a plain URL string on the backend; not migrated to the media FK model. */
  photoUrl?: string;
  wishlist?: School[];
}

export interface Tag extends AbstractEntity {
  name: string;
}

export interface Accreditation extends AbstractEntity {
  name: string;
  description?: string;
  issuer?: string;
  logoUrl?: string;
}

export interface School extends BaseEntity {
  creation?: string;
  address: Address;
  /** Write: id of an already-uploaded media (ADMIN-only upload). */
  logoMediaId?: number;
  coverMediaId?: number;
  /** Read: resolved public media, carries `publicUrl`. */
  logo?: Media | null;
  cover?: Media | null;
  programs?: Program[];
  schoolRates?: SchoolRate[];
  tags?: Tag[];
  accreditations?: Accreditation[];
  // Transient fields for UI compatibility
  type?: string;
  description?: string;
  rating?: number;
  reviewCount?: number;
  /** Absolute image URLs resolved from `logo`/`cover` by the service layer. */
  logoImageUrl?: string;
  coverImageUrl?: string;
}

export interface Program extends BaseEntity {
  duration: number; // In years
  /** Write: media id. Read: resolved `photo`. */
  photoMediaId?: number;
  photo?: Media | null;
  schoolId?: number;
  school?: School;
  courses?: Course[];
  programRates?: ProgramRate[];
  accreditations?: Accreditation[];
  // Transient fields for UI compatibility
  level?: string;
  description?: string;
  rating?: number;
  reviewCount?: number;
  startDate?: string;
  capacity?: number;
  enrolled?: number;
  /** Absolute image URL resolved from `photo` by the service layer. */
  photoImageUrl?: string;
}

export interface Course extends BaseEntity {
  /** Write: media id. Read: resolved `photo`. */
  photoMediaId?: number;
  photo?: Media | null;
  programId?: number;
  program?: Program;
  courseRates?: CourseRate[];
  /** Absolute image URL resolved from `photo` by the service layer. */
  photoImageUrl?: string;
}

export interface Rate extends AbstractEntity {
  note: number;
  comment: string;
  user?: User;
}

export interface SchoolRate extends Rate {
  school?: School;
}

export interface ProgramRate extends Rate {
  program?: Program;
}

export interface CourseRate extends Rate {
  course?: Course;
}

export interface Page<T> {
  content: T[];
  pageable: {
    sort: {
      empty: boolean;
      sorted: boolean;
      unsorted: boolean;
    };
    offset: number;
    pageNumber: number;
    pageSize: number;
    paged: boolean;
    unpaged: boolean;
  };
  last: boolean;
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  sort: {
    empty: boolean;
    sorted: boolean;
    unsorted: boolean;
  };
  first: boolean;
  numberOfElements: number;
  empty: boolean;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user?: User;
}
