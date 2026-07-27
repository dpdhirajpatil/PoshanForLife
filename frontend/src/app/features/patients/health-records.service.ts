import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HealthRecordEntry, HealthRecordSource } from '../../core/models/patient.model';
import { ApiService } from '../../core/services/api.service';

export interface HealthRecordsQuery {
  limit?: number;
  before?: string;
  fields?: string[];
}

/** Fields ADMIN/DOCTOR may set on a manual quick-entry; source is always forced to 'manual' server-side. */
export interface LogMeasurementPayload {
  patientId: string;
  recordDate?: string;
  weightKg?: number;
  bodyFatPct?: number;
  skeletalMuscleMassKg?: number;
  bmi?: number;
  visceralFatLevel?: number;
  bodyWaterL?: number;
  proteinKg?: number;
  mineralKg?: number;
  basalMetabolicRate?: number;
}

export interface UpsertHealthRecordResult {
  record: HealthRecordEntry;
  upserted: boolean;
}

@Injectable({ providedIn: 'root' })
export class HealthRecordsService {
  private readonly api = inject(ApiService);

  list(patientId: string, query: HealthRecordsQuery = {}): Observable<HealthRecordEntry[]> {
    return this.api.get<HealthRecordEntry[]>(`/health-records/${patientId}`, {
      limit: query.limit,
      before: query.before,
      fields: query.fields?.length ? query.fields.join(',') : undefined,
    });
  }

  logMeasurement(payload: LogMeasurementPayload): Observable<UpsertHealthRecordResult> {
    const source: HealthRecordSource = 'manual';
    return this.api.post<UpsertHealthRecordResult>('/health-records', { ...payload, source });
  }

  remove(patientId: string, recordId: string): Observable<unknown> {
    return this.api.delete(`/health-records/${patientId}?recordId=${recordId}`);
  }
}
