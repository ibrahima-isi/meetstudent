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

export interface User extends AbstractEntity {
  firstname: string;
  lastname: string;
  birthday?: string;
  email: string;
  role: Role;
  qualification?: string;
  diplomas?: string[];
  certificates?: string[];
  presentationVideoUrl?: string;
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
  logoUrl?: string;
  coverPhotoUrl?: string;
  programs?: Program[];
  schoolRates?: SchoolRate[];
  tags?: Tag[];
  accreditations?: Accreditation[];
  // Transient fields for UI compatibility
  type?: string;
  description?: string;
  rating?: number;
  reviewCount?: number;
}

export interface Program extends BaseEntity {
  duration: number; // In years
  photoUrl?: string;
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
}

export interface Course extends BaseEntity {
  photoUrl?: string;
  program?: Program;
  courseRates?: CourseRate[];
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
