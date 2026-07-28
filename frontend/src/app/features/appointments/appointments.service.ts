import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Paged } from '../../core/models/api-response.model';
import { Appointment, AppointmentStatus } from '../../core/models/appointment.model';
import { ApiService } from '../../core/services/api.service';

export interface AppointmentListQuery {
  /** ADMIN only — DOCTOR/PATIENT are hard-scoped to their own appointments server-side. */
  practitionerId?: string;
  patientId?: string;
  status?: AppointmentStatus;
  /** ISO dates (yyyy-MM-dd), inclusive. */
  dateFrom?: string;
  dateTo?: string;
  page: number;
  limit: number;
}

export interface UpdateAppointmentPayload {
  scheduledAt?: string;
  status?: AppointmentStatus;
  notes?: string;
}

/**
 * Booking (POST) happens on the mobile app only — this portal only reads
 * and updates (status/notes/reschedule) existing appointments, plus an
 * ADMIN-only hard delete for cleanup.
 */
@Injectable({ providedIn: 'root' })
export class AppointmentsService {
  private readonly api = inject(ApiService);

  list(query: AppointmentListQuery): Observable<Paged<Appointment[]>> {
    return this.api.getPaged<Appointment[]>('/appointments', { ...query });
  }

  update(id: string, payload: UpdateAppointmentPayload): Observable<Appointment> {
    return this.api.patch<Appointment>(`/appointments/${id}`, payload);
  }

  /** ADMIN-only cleanup tool — a cancelled-status update() is the normal path otherwise. */
  delete(id: string): Observable<unknown> {
    return this.api.delete(`/appointments/${id}`);
  }
}
