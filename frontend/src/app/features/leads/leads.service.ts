import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Paged } from '../../core/models/api-response.model';
import {
  ConvertLeadResponse,
  LeadActivity,
  LeadActivityType,
  LeadDetail,
  LeadListResponse,
  LeadSource,
  LeadStage,
  ServiceType,
} from '../../core/models/lead.model';
import { Gender } from '../../core/models/patient.model';
import { ApiService } from '../../core/services/api.service';

export interface LeadListQuery {
  stage?: LeadStage;
  search?: string;
  /** ADMIN only — the backend hard-scopes DOCTOR callers regardless. */
  practitionerId?: string;
  source?: LeadSource;
  followupToday?: boolean;
  page: number;
  limit: number;
}

export interface CreateLeadPayload {
  name: string;
  phone?: string;
  email?: string;
  city?: string;
  age?: number;
  gender?: Gender;
  source: LeadSource;
  healthGoal?: string;
  notes?: string;
  assignedPractitionerId?: string;
  interestedProgrammeId?: string;
  nextFollowupAt?: string;
}

export type UpdateLeadPayload = Partial<Omit<CreateLeadPayload, 'source'>> & { stage?: LeadStage };

export interface CreateLeadActivityPayload {
  activityType: LeadActivityType;
  description: string;
}

export interface ScheduleFollowupPayload {
  followupAt: string;
  message?: string;
}

export interface ConvertLeadPayload {
  name?: string;
  phone?: string;
  email?: string;
  dateOfBirth?: string;
  bloodGroup?: string;
  heightCm?: number;
  assignServiceId?: string;
  serviceType?: ServiceType;
  startDate?: string;
  price?: number;
}

@Injectable({ providedIn: 'root' })
export class LeadsService {
  private readonly api = inject(ApiService);

  list(query: LeadListQuery): Observable<Paged<LeadListResponse>> {
    return this.api.getPaged<LeadListResponse>('/leads', { ...query });
  }

  get(id: string): Observable<LeadDetail> {
    return this.api.get<LeadDetail>(`/leads/${id}`);
  }

  create(payload: CreateLeadPayload): Observable<LeadDetail> {
    return this.api.post<LeadDetail>('/leads', payload);
  }

  update(id: string, payload: UpdateLeadPayload): Observable<LeadDetail> {
    return this.api.patch<LeadDetail>(`/leads/${id}`, payload);
  }

  addActivity(id: string, payload: CreateLeadActivityPayload): Observable<LeadActivity> {
    return this.api.post<LeadActivity>(`/leads/${id}/activities`, payload);
  }

  convert(id: string, payload: ConvertLeadPayload): Observable<ConvertLeadResponse> {
    return this.api.post<ConvertLeadResponse>(`/leads/${id}/convert`, payload);
  }

  scheduleFollowup(id: string, payload: ScheduleFollowupPayload): Observable<LeadDetail> {
    return this.api.post<LeadDetail>(`/leads/${id}/schedule-followup`, payload);
  }
}
