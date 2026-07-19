import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Paged } from '../../core/models/api-response.model';
import { CatalogueItem, CatalogueStatus, CatalogueType } from '../../core/models/catalogue.model';
import { ApiService } from '../../core/services/api.service';

export interface CatalogueListQuery {
  status?: CatalogueStatus;
  search?: string;
  page: number;
  limit: number;
}

/**
 * Shared save payload — include only the duration/goal field matching the
 * item's type; the backend validates the rest.
 */
export interface SaveCatalogueItemPayload {
  name: string;
  serviceCode: string;
  type: string;
  priceInr: number;
  description?: string;
  /** "" clears the stored image on update. */
  coverImageUrl?: string;
  status?: CatalogueStatus;
  durationWeeks?: number;
  durationMinutes?: number;
  durationDays?: number;
  goalDescription?: string;
}

/** One service for all three catalogue resource groups, keyed by type. */
@Injectable({ providedIn: 'root' })
export class CatalogueService {
  private readonly api = inject(ApiService);

  list(type: CatalogueType, query: CatalogueListQuery): Observable<Paged<CatalogueItem[]>> {
    return this.api.getPaged<CatalogueItem[]>(`/catalogue/${type}`, { ...query });
  }

  get(type: CatalogueType, id: string): Observable<CatalogueItem> {
    return this.api.get<CatalogueItem>(`/catalogue/${type}/${id}`);
  }

  create(type: CatalogueType, payload: SaveCatalogueItemPayload): Observable<CatalogueItem> {
    return this.api.post<CatalogueItem>(`/catalogue/${type}`, payload);
  }

  update(
    type: CatalogueType,
    id: string,
    payload: Partial<SaveCatalogueItemPayload>,
  ): Observable<CatalogueItem> {
    return this.api.patch<CatalogueItem>(`/catalogue/${type}/${id}`, payload);
  }

  /** Blocked by the backend while active patient assignments reference the item. */
  remove(type: CatalogueType, id: string): Observable<unknown> {
    return this.api.delete(`/catalogue/${type}/${id}`);
  }

  /** Uploads a cover image and resolves to its public URL. */
  uploadCover(type: CatalogueType, file: File): Observable<{ url: string }> {
    const form = new FormData();
    form.append('file', file);
    return this.api.post<{ url: string }>(`/catalogue/${type}/upload`, form);
  }
}
