export type ReportType = 'inbody' | 'lab' | 'prescription' | 'other';
export type ReportStatus = 'pending' | 'processing' | 'done' | 'error';

/** Scaffold of the ~20 typical InBody-report fields Claude is asked to extract. Any may be null. */
export interface InBodyData {
  weightKg: number | null;
  bodyFatPercent: number | null;
  skeletalMuscleMassKg: number | null;
  bmi: number | null;
  visceralFatLevel: number | null;
  bodyWaterL: number | null;
  proteinKg: number | null;
  mineralKg: number | null;
  basalMetabolicRate: number | null;
  bodyFatMassKg: number | null;
  fatFreeMassKg: number | null;
  waistHipRatio: number | null;
  targetWeightKg: number | null;
  weightControlKg: number | null;
  fatControlKg: number | null;
  muscleControlKg: number | null;
  obesityDegreePercent: number | null;
  intracellularWaterL: number | null;
  extracellularWaterL: number | null;
  inbodyScore: number | null;
}

export interface ReportPatientRef {
  id: string;
  name: string;
  email: string;
  phone: string | null;
}

export interface ReportListItem {
  id: string;
  patient: ReportPatientRef;
  title: string;
  type: ReportType;
  status: ReportStatus;
  createdAt: string;
  createdBy: { id: string; name: string };
}

/** Computed over the CURRENT FILTER SET, not just the current page — same pattern as transactions. */
export interface ReportStats {
  total: number;
  pending: number;
  processing: number;
  done: number;
  error: number;
  thisMonth: number;
}

export interface ReportListResponse {
  reports: ReportListItem[];
  stats: ReportStats;
}

export interface ReportDetail {
  id: string;
  patient: ReportPatientRef;
  title: string;
  type: ReportType;
  notes: string | null;
  status: ReportStatus;
  /** Freshly-signed, short-lived download URL — null if this report has no file. */
  fileUrl: string | null;
  parsedData: InBodyData | null;
  confidence: 'high' | 'low' | null;
  extractedFieldCount: number | null;
  extractionMethod: string | null;
  createdBy: { id: string; name: string };
  createdAt: string;
}

export interface ReportUploadResponse {
  reportId: string;
  healthRecordId: string;
  parsedData: InBodyData;
  fileUrl: string;
  confidence: 'high' | 'low';
  extractedFieldCount: number;
  extractionMethod: string;
  warnings: string[] | null;
}
