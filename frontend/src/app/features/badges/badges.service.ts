import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Badge, BadgeCriteriaType } from '../../core/models/badge.model';
import { ApiService } from '../../core/services/api.service';

export interface CreateBadgePayload {
  name: string;
  description?: string;
  iconKey: string;
  criteriaType: BadgeCriteriaType;
  criteriaValue: number;
}

export interface UpdateBadgePayload {
  name?: string;
  description?: string;
  iconKey?: string;
  criteriaType?: BadgeCriteriaType;
  criteriaValue?: number;
}

/** The badge catalog is small and unpaginated (ADMIN-only management, not a patient-facing feed). */
@Injectable({ providedIn: 'root' })
export class BadgesService {
  private readonly api = inject(ApiService);

  list(): Observable<Badge[]> {
    return this.api.get<Badge[]>('/badges');
  }

  create(payload: CreateBadgePayload): Observable<Badge> {
    return this.api.post<Badge>('/badges', payload);
  }

  update(id: string, payload: UpdateBadgePayload): Observable<Badge> {
    return this.api.patch<Badge>(`/badges/${id}`, payload);
  }

  delete(id: string): Observable<unknown> {
    return this.api.delete(`/badges/${id}`);
  }
}
