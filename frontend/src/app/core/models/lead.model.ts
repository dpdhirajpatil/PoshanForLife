import { Gender } from './patient.model';
import { ServiceType } from './patient-programme.model';

export type LeadSource =
  | 'referral'
  | 'social_media'
  | 'website'
  | 'walk_in'
  | 'phone'
  | 'whatsapp'
  | 'mobile_app'
  | 'other';

export const LEAD_SOURCES: LeadSource[] = [
  'referral',
  'social_media',
  'website',
  'walk_in',
  'phone',
  'whatsapp',
  'mobile_app',
  'other',
];

export type LeadStage = 'new' | 'contacted' | 'qualified' | 'proposed' | 'converted' | 'lost';

/** Pipeline order — drives the Kanban board's column order. */
export const LEAD_STAGES: LeadStage[] = [
  'new',
  'contacted',
  'qualified',
  'proposed',
  'converted',
  'lost',
];

export const LEAD_STAGE_LABELS: Record<LeadStage, string> = {
  new: 'New',
  contacted: 'Contacted',
  qualified: 'Qualified',
  proposed: 'Proposed',
  converted: 'Converted',
  lost: 'Lost',
};

export type LeadActivityType = 'note' | 'call' | 'whatsapp' | 'estimate_sent' | 'stage_change' | 'converted';

/** Offered by the "Log activity" quick-add — stage_change/converted are system-generated only. */
export const LOGGABLE_ACTIVITY_TYPES: LeadActivityType[] = ['note', 'call', 'whatsapp', 'estimate_sent'];

export const ACTIVITY_ICONS: Record<LeadActivityType, string> = {
  note: 'sticky_note_2',
  call: 'call',
  whatsapp: 'chat',
  estimate_sent: 'request_quote',
  stage_change: 'swap_horiz',
  converted: 'celebration',
};

export interface LeadServiceRef {
  id: string;
  name: string;
  serviceCode: string;
  durationWeeks?: number;
  durationMinutes?: number;
  durationDays?: number;
}

export interface LeadUserRef {
  id: string;
  name: string;
}

export interface LeadListItem {
  id: string;
  name: string;
  phone: string | null;
  email: string | null;
  city: string | null;
  age: number | null;
  gender: Gender | null;
  source: LeadSource;
  stage: LeadStage;
  assignedPractitioner: LeadUserRef | null;
  interestedProgramme: LeadServiceRef | null;
  nextFollowupAt: string | null;
  createdAt: string;
}

export interface LeadActivity {
  id: string;
  activityType: LeadActivityType;
  description: string;
  oldStage: LeadStage | null;
  newStage: LeadStage | null;
  createdBy: LeadUserRef;
  createdAt: string;
}

export interface LeadDetail {
  id: string;
  name: string;
  phone: string | null;
  email: string | null;
  city: string | null;
  age: number | null;
  gender: Gender | null;
  source: LeadSource;
  healthGoal: string | null;
  notes: string | null;
  stage: LeadStage;
  assignedPractitioner: LeadUserRef | null;
  interestedProgramme: LeadServiceRef | null;
  nextFollowupAt: string | null;
  convertedPatientId: string | null;
  createdBy: LeadUserRef;
  createdAt: string;
  updatedAt: string;
  activities: LeadActivity[];
}

/** Computed over the CURRENT FILTER SET (minus the stage filter) — same pattern as reports/transactions. */
export interface LeadSummary {
  stageCounts: Record<LeadStage, number>;
  followupToday: number;
  newThisWeek: number;
  converted: number;
  conversionRate: number;
}

export interface LeadListResponse {
  leads: LeadListItem[];
  summary: LeadSummary;
}

export interface ConvertLeadResponse {
  patientId: string;
  message: string;
}

export type { ServiceType };
