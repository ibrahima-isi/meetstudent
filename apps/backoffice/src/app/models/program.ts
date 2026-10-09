import { Media, MediaCategory } from './entities';
import { Accreditation } from './accreditation';
import { Course } from './course';

/** Categories the programs page uploads: a program photo and a course photo (both public). */
export type ProgramMediaCategory = Extract<MediaCategory, 'PROGRAM_PHOTO' | 'COURSE_PHOTO'>;

/** Mirrors the API `ProgramDTO`. `courses` is embedded; `photoMediaId` only is set on those. */
export interface Program {
  id: number;
  code?: string | null;
  name: string;
  /** In years. */
  duration?: number | null;
  schoolId?: number | null;
  photoMediaId?: number | null;
  photo?: (Pick<Media, 'id'> & Partial<Media>) | null;
  averageRate?: number | null;
  courses?: Course[] | null;
}

/**
 * Body of POST / PUT `/programs`. The API patches on PUT: an omitted `code`,
 * `duration`, `schoolId` or `photoMediaId` keeps the stored value (it can never be cleared).
 */
export interface ProgramInput {
  code?: string;
  name: string;
  duration?: number;
  schoolId?: number;
  photoMediaId?: number;
}

/** A link between a program and an accreditation, with the years it is valid. */
export interface ProgramAccreditation {
  programId: number;
  accreditationId: number;
  accreditation?: Accreditation | null;
  startsAt?: number | null;
  endsAt?: number | null;
}
