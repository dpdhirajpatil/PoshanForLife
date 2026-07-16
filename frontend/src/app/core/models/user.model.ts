export type Role = 'ADMIN' | 'DOCTOR' | 'PATIENT';

export interface CurrentUser {
  id: string;
  email: string;
  name: string;
  role: Role;
}

/** Body of /auth/login and /auth/refresh (inside the standard envelope). */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: CurrentUser;
}
