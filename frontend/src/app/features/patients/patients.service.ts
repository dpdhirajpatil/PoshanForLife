import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Paged } from '../../core/models/api-response.model';
import {
  Gender,
  PatientDetail,
  PatientStats,
  PatientSummary,
} from '../../core/models/patient.model';
import { ApiService } from '../../core/services/api.service';

export interface PatientListQuery {
  search?: string;
  /** ADMIN only — the backend hard-scopes DOCTOR callers regardless. */
  doctorId?: string;
  page: number;
  limit: number;
}

export interface CreatePatientPayload {
  name: string;
  email: string;
  /** Omit to have the backend generate a temp password (returned once). */
  password?: string;
  phone?: string;
  dateOfBirth?: string;
  gender?: Gender;
  bloodGroup?: string;
  heightCm?: number;
  emergencyContact?: string;
  medicalHistory?: string;
  assignedDoctorId?: string;
}

export type UpdatePatientPayload = Partial<Omit<CreatePatientPayload, 'email' | 'password' | 'assignedDoctorId'>>;

@Injectable({ providedIn: 'root' })
export class PatientsService {
  private readonly api = inject(ApiService);

  list(query: PatientListQuery): Observable<Paged<PatientSummary[]>> {
    return this.api.getPaged<PatientSummary[]>('/patients', { ...query });
  }

  stats(): Observable<PatientStats> {
    return this.api.get<PatientStats>('/patients/stats');
  }

  get(id: string): Observable<PatientDetail> {
    return this.api.get<PatientDetail>(`/patients/${id}`);
  }

  create(payload: CreatePatientPayload): Observable<PatientDetail> {
    return this.api.post<PatientDetail>('/patients', payload);
  }

  update(id: string, payload: UpdatePatientPayload): Observable<PatientDetail> {
    return this.api.patch<PatientDetail>(`/patients/${id}`, payload);
  }

  /** Soft delete (ADMIN only) — history is kept, account is deactivated. */
  deactivate(id: string): Observable<unknown> {
    return this.api.delete(`/patients/${id}`);
  }
}
