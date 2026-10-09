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
