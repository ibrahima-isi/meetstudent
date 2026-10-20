import { Media, MediaCategory } from './entities';
import { Accreditation } from './accreditation';
import { Course } from './course';

/** Categories the programs page uploads: a program photo and a course photo (both public). */
export type ProgramMediaCategory = Extract<MediaCategory, 'PROGRAM_PHOTO' | 'COURSE_PHOTO'>;

/** Closed list accepted by the API for `level`. */
export const PROGRAM_LEVELS = ['BTS_DUT', 'LICENCE', 'MASTER', 'DOCTORAT', 'AUTRE'] as const;
export type ProgramLevel = (typeof PROGRAM_LEVELS)[number];

/** Mirrors the API `ProgramDTO`. `courses` is embedded; `photoMediaId` only is set on those. */
export interface Program {
  id: number;
  code?: string | null;
  name: string;
  /** In years. */
  duration?: number | null;
  description?: string | null;
  availableSeats?: number | null;
  /** In euros. */
  annualFee?: number | null;
  level?: ProgramLevel | null;
  schoolId?: number | null;
  photoMediaId?: number | null;
  photo?: (Pick<Media, 'id'> & Partial<Media>) | null;
  averageRate?: number | null;
  courses?: Course[] | null;
}

/**
 * Body of POST / PUT `/programs`. The API patches on PUT: an omitted `code`,
 * `duration`, `description`, `availableSeats`, `annualFee`, `level`, `schoolId` or
 * `photoMediaId` keeps the stored value. Only `description` can be cleared, by sending an empty or blank string; the other fields can be replaced but never cleared.
 */
export interface ProgramInput {
  code?: string;
  name: string;
  duration?: number;
  description?: string;
  availableSeats?: number;
  annualFee?: number;
  level?: ProgramLevel;
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
