import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { NotificationListResponse } from '../models/notification.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly api = inject(ApiService);

  list(limit = 20, unread = false): Observable<NotificationListResponse> {
    return this.api.get<NotificationListResponse>('/notifications', { limit, unread });
  }

  markAllRead(): Observable<{ updated: boolean }> {
    return this.api.patch<{ updated: boolean }>('/notifications', {});
  }
}
