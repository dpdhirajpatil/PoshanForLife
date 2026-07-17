import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DoctorRef } from '../../core/models/patient.model';
import { ApiService } from '../../core/services/api.service';

/** One doctor–patient assignment with names populated. ADMIN-only API. */
export interface Assignment {
  id: string;
  doctor: DoctorRef;
  patient: DoctorRef;
  assignedAt: string;
}

@Injectable({ providedIn: 'root' })
export class AssignmentsService {
  private readonly api = inject(ApiService);

  list(filter: { doctorId?: string; patientId?: string }): Observable<Assignment[]> {
    return this.api.get<Assignment[]>('/assignments', { ...filter });
  }

  /** Creates the pair; the backend notifies the doctor in-app. */
  create(doctorId: string, patientId: string): Observable<Assignment> {
    return this.api.post<Assignment>('/assignments', { doctorId, patientId });
  }

  remove(id: string): Observable<unknown> {
    return this.api.delete(`/assignments/${id}`);
  }
}
