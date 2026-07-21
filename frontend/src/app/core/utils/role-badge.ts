/** Shared role → badge styling/label, used by the topbar, sidebar, and settings page. */
export const ROLE_BADGE_CLASSES: Record<string, string> = {
  ADMIN: 'bg-red-100 text-red-700',
  DOCTOR: 'bg-primary-100 text-primary-700',
  PATIENT: 'bg-primary-50 text-primary-600',
};

export const ROLE_BADGE_LABELS: Record<string, string> = {
  ADMIN: 'Admin',
  DOCTOR: 'Practitioner',
  PATIENT: 'Patient',
};
