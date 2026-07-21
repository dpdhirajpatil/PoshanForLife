/** myPatients is DOCTOR-only (null for ADMIN); avgInBodyScore/pendingReviews/etc. are already caller-scoped by the backend. */
export interface DashboardKpis {
  totalPatients: number;
  activeDoctors: number;
  reportsThisMonth: number;
  avgInBodyScore: number | null;
  myPatients: number | null;
  pendingReviews: number;
  followupToday: number;
}

/** month is "yyyy-MM"; avgBodyFatPct is null for months with no records. */
export interface PbfTrendPoint {
  month: string;
  avgBodyFatPct: number | null;
}

export interface BmiBucket {
  label: 'Underweight' | 'Normal' | 'Overweight' | 'Obese';
  count: number;
}

export interface BodyCompositionPoint {
  patientId: string;
  patientName: string;
  x: number;
  y: number;
}

export type DashboardActivityType = 'patient_created' | 'report_created' | 'lead_stage_change';

export interface DashboardActivity {
  type: DashboardActivityType;
  message: string;
  occurredAt: string;
  patientId: string | null;
  leadId: string | null;
}

export type AttentionReason = 'never_recorded' | 'no_recent_report';

export interface AttentionPatient {
  patientId: string;
  patientName: string;
  lastRecordedAt: string | null;
  reason: AttentionReason;
}

export interface DoctorPatientCount {
  doctorId: string;
  doctorName: string;
  patientCount: number;
}

/** GET /dashboard/stats — attentionPatients (DOCTOR) / assignmentOverview (ADMIN) are mutually exclusive. */
export interface DashboardStats {
  kpis: DashboardKpis;
  pbfSixMonthTrend: PbfTrendPoint[];
  bmiDistribution: BmiBucket[];
  bodyCompositionScatter: BodyCompositionPoint[];
  recentActivity: DashboardActivity[];
  attentionPatients: AttentionPatient[] | null;
  assignmentOverview: DoctorPatientCount[] | null;
}
