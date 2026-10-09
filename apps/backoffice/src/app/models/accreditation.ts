export interface Accreditation {
  id: number;
  code?: string | null;
  name: string;
  description?: string | null;
}

/** Body of POST / PUT `/accreditations`. */
export interface AccreditationInput {
  code?: string;
  name: string;
  description?: string;
}
