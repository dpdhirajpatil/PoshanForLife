export type AppointmentStatus = 'scheduled' | 'completed' | 'cancelled' | 'no_show';

export const APPOINTMENT_STATUSES: AppointmentStatus[] = [
  'scheduled',
  'completed',
  'cancelled',
  'no_show',
];

export const APPOINTMENT_STATUS_LABELS: Record<AppointmentStatus, string> = {
  scheduled: 'Scheduled',
  completed: 'Completed',
  cancelled: 'Cancelled',
  no_show: 'No-show',
};

export interface AppointmentUserRef {
  id: string;
  name: string;
}

/**
 * The list and single-resource endpoints return the same shape — unlike
 * Orders/Documents there's no separate thin "list item" vs "detail" DTO.
 */
export interface Appointment {
  id: string;
  patient: AppointmentUserRef;
  practitioner: AppointmentUserRef;
  scheduledAt: string;
  durationMinutes: number;
  status: AppointmentStatus;
  notes: string | null;
  isVideo: boolean;
  videoRoomId: string | null;
  createdBy: AppointmentUserRef | null;
  createdAt: string;
}
