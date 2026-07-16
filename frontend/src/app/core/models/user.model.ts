export type Role = 'ADMIN' | 'DOCTOR' | 'PATIENT';

export interface CurrentUser {
  id: string;
  email: string;
  name: string;
  role: Role;
}

export interface LoginResponse {
  token: string;
  user: CurrentUser;
}
