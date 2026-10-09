import { Media } from './entities';

/** Mirrors the API `CourseDTO`. Embedded in a program, only `id`, `name`, `code` and `photoMediaId` are reliable. */
export interface Course {
  id: number;
  code?: string | null;
  name: string;
  programId?: number | null;
  photoMediaId?: number | null;
  photo?: (Pick<Media, 'id'> & Partial<Media>) | null;
  averageRate?: number | null;
}

/**
 * Body of POST / PUT `/courses`. The API patches on PUT: an omitted `code` or
 * `photoMediaId` keeps the stored value (it can never be cleared).
 */
export interface CourseInput {
  code?: string;
  name: string;
  programId: number;
  photoMediaId?: number;
}
