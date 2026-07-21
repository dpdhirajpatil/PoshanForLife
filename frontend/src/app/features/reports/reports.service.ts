import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Paged } from '../../core/models/api-response.model';
import {
  InBodyData,
  ReportDetail,
  ReportListResponse,
  ReportStatus,
  ReportType,
  ReportUploadResponse,
} from '../../core/models/report.model';
import { ApiService } from '../../core/services/api.service';

export interface ReportListQuery {
  status?: ReportStatus;
  type?: ReportType;
  search?: string;
  dateFrom?: string;
  dateTo?: string;
  page: number;
  limit: number;
}

export interface CreateReportPayload {
  patientId: string;
  title: string;
  type: ReportType;
  notes?: string;
}

export interface UpdateReportPayload {
  title?: string;
  notes?: string;
  status?: ReportStatus;
  /** Corrections from the upload review screen — re-upserts the linked HealthRecord too. */
  parsedData?: InBodyData;
}

/**
 * Manual report records + the AI-powered InBody PDF upload pipeline. Reads
 * are ADMIN+DOCTOR (DOCTOR scoped server-side); delete is ADMIN-only.
 */
@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly api = inject(ApiService);

  list(query: ReportListQuery): Observable<Paged<ReportListResponse>> {
    return this.api.getPaged<ReportListResponse>('/reports', { ...query });
  }

  get(id: string): Observable<ReportDetail> {
    return this.api.get<ReportDetail>(`/reports/${id}`);
  }

  create(payload: CreateReportPayload): Observable<ReportDetail> {
    return this.api.post<ReportDetail>('/reports', payload);
  }

  update(id: string, payload: UpdateReportPayload): Observable<ReportDetail> {
    return this.api.patch<ReportDetail>(`/reports/${id}`, payload);
  }

  remove(id: string): Observable<unknown> {
    return this.api.delete(`/reports/${id}`);
  }

  /** Uploads an InBody PDF; the backend runs the full extraction pipeline before responding. */
  upload(patientId: string, file: File): Observable<ReportUploadResponse> {
    const form = new FormData();
    form.append('patientId', patientId);
    form.append('file', file);
    return this.api.post<ReportUploadResponse>('/reports/upload', form);
  }
}
