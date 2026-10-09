import { Media, MediaCategory } from './entities';

export interface Tag {
  id?: number;
  name: string;
}

export type SchoolMediaCategory = Extract<MediaCategory, 'SCHOOL_LOGO' | 'SCHOOL_COVER'>;

/** The media embedded in a school: only `id` and `publicUrl` are relied upon. */
export type SchoolMedia = Pick<Media, 'id'> & Partial<Media>;

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
  logo?: SchoolMedia | null;
  cover?: SchoolMedia | null;
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
