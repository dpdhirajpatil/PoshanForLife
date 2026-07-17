export type Gender = 'MALE' | 'FEMALE' | 'OTHER';

export interface DoctorRef {
  id: string;
  name: string;
}

export interface PatientSummary {
  id: string;
  name: string;
  email: string;
  phone: string | null;
  dateOfBirth: string | null;
  isActive: boolean;
  assignedDoctors: DoctorRef[];
  createdAt: string;
}

export interface HealthRecordEntry {
  id: string;
  weightKg: number | null;
  bodyFatPct: number | null;
  bmi: number | null;
  recordedAt: string;
}

export interface PatientDetail {
  id: string;
  name: string;
  email: string;
  phone: string | null;
  dateOfBirth: string | null;
  isActive: boolean;
  gender: Gender | null;
  bloodGroup: string | null;
  heightCm: number | null;
  emergencyContact: string | null;
  medicalHistory: string | null;
  doctorNotes: string | null;
  assignedDoctors: DoctorRef[];
  healthRecords: HealthRecordEntry[];
  reports: unknown[];
  /** Present once on the create response when the password was auto-generated. */
  tempPassword: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PatientStats {
  totalPatients: number;
  activeThisMonth: number;
  averageBmi: number | null;
  averageBodyFatPct: number | null;
}

/** Derives age in whole years from an ISO date string; null when unknown. */
export function ageFrom(dateOfBirth: string | null): number | null {
  if (!dateOfBirth) return null;
  const dob = new Date(dateOfBirth);
  if (isNaN(dob.getTime())) return null;
  const now = new Date();
  let age = now.getFullYear() - dob.getFullYear();
  const beforeBirthday =
    now.getMonth() < dob.getMonth() ||
    (now.getMonth() === dob.getMonth() && now.getDate() < dob.getDate());
  return beforeBirthday ? age - 1 : age;
}
