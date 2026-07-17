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

export interface NotificationPrefs {
  inbodyReport: boolean;
  patientAssigned: boolean;
  processingErrors: boolean;
  systemAnnouncements: boolean;
}

/** Full user record from the users feature endpoints. */
export interface UserDetail {
  id: string;
  name: string;
  email: string;
  role: Role;
  phone: string | null;
  avatarUrl: string | null;
  dateOfBirth: string | null;
  isActive: boolean;
  notificationPrefs: NotificationPrefs;
  createdAt: string;
  updatedAt: string;
}
