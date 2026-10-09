export interface Role {
  id?: number;
  name: string;
}

export interface User {
  id?: number;
  firstname: string;
  lastname: string;
  email: string;
  role: Role;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
}

export const ROLE_ADMIN = 'ROLE_ADMIN';

export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';

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

/** Mirrors the API `MediaDTO`. It carries neither owner nor upload date. */
export interface Media {
  id: number;
  category: MediaCategory;
  visibility: 'PUBLIC' | 'PRIVATE';
  verificationStatus: VerificationStatus | null;
  rejectionReason: string | null;
  originalFilename: string | null;
  contentType: string | null;
  sizeBytes: number | null;
  publicUrl: string | null;
}

/** Spring Data `Page` (only the fields the back office reads). */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
