/** Spring Data `Page` envelope, as returned by every paginated endpoint. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  /** Zero-based page index. */
  number: number;
  size: number;
}

export interface Tag {
  id?: number;
  name: string;
}

export type SchoolMediaCategory = 'SCHOOL_LOGO' | 'SCHOOL_COVER';

export interface Media {
  id: number;
  category?: string;
  originalFilename?: string | null;
  contentType?: string | null;
  sizeBytes?: number | null;
  /** Relative to the SERVER root (not to `/api/v1`); null for private media. */
  publicUrl?: string | null;
}

export interface Address {
  location?: string | null;
  city?: string | null;
  country?: string | null;
}

export interface School {
  id: number;
  code?: string | null;
  name: string;
  address?: Address | null;
  logoMediaId?: number | null;
  coverMediaId?: number | null;
  logo?: Media | null;
  cover?: Media | null;
  averageRate?: number | null;
  tags?: Tag[] | null;
}

/** Body of POST / PUT `/schools`. Media ids are omitted to keep the current image. */
export interface SchoolInput {
  code?: string;
  name: string;
  address: { location: string; city: string; country: string };
  tags: Tag[];
  logoMediaId?: number;
  coverMediaId?: number;
}
