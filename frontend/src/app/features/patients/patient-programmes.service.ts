import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PatientProgramme, ServiceType } from '../../core/models/patient-programme.model';
import { ApiService } from '../../core/services/api.service';

/**
 * Assign a service: include only the id field matching serviceType. The
 * backend derives endDate from the catalogue item's duration and creates the
 * order (+ activation transaction for priced items) in the same DB
 * transaction.
 */
export interface AssignServicePayload {
  serviceType: ServiceType;
  programmeId?: string;
  sessionId?: string;
  challengeId?: string;
  /** ISO date; defaults to today on the backend. */
  startDate?: string;
  /** Overrides the catalogue price. */
  priceInr?: number;
  notes?: string;
}

export interface UpdateAssignmentPayload {
  status?: 'active' | 'completed' | 'cancelled';
  startDate?: string;
  endDate?: string;
  /** "" clears the stored notes. */
  notes?: string;
}

@Injectable({ providedIn: 'root' })
export class PatientProgrammesService {
  private readonly api = inject(ApiService);

  list(patientId: string): Observable<PatientProgramme[]> {
    return this.api.get<PatientProgramme[]>(`/patients/${patientId}/programmes`);
  }

  get(patientId: string, id: string): Observable<PatientProgramme> {
    return this.api.get<PatientProgramme>(`/patients/${patientId}/programmes/${id}`);
  }

  create(patientId: string, payload: AssignServicePayload): Observable<PatientProgramme> {
    return this.api.post<PatientProgramme>(`/patients/${patientId}/programmes`, payload);
  }

  update(
    patientId: string,
    id: string,
    payload: UpdateAssignmentPayload,
  ): Observable<PatientProgramme> {
    return this.api.patch<PatientProgramme>(`/patients/${patientId}/programmes/${id}`, payload);
  }

  /** Blocked by the backend once a non-refund transaction exists. */
  remove(patientId: string, id: string): Observable<unknown> {
    return this.api.delete(`/patients/${patientId}/programmes/${id}`);
  }
}
