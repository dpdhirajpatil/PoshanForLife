import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Paged } from '../../core/models/api-response.model';
import { NotificationPrefs, Role, UserDetail } from '../../core/models/user.model';
import { ApiService } from '../../core/services/api.service';

export interface UserListQuery {
  role?: Role;
  search?: string;
  page: number;
  limit: number;
}

export interface CreateUserPayload {
  name: string;
  email: string;
  password: string;
  role: Role;
  phone?: string;
}

export interface UpdateUserPayload {
  name?: string;
  phone?: string;
  role?: Role;
  isActive?: boolean;
  dateOfBirth?: string;
}

export interface ChangePasswordPayload {
  currentPassword?: string;
  newPassword: string;
  confirmPassword: string;
}

@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly api = inject(ApiService);

  list(query: UserListQuery): Observable<Paged<UserDetail[]>> {
    return this.api.getPaged<UserDetail[]>('/users', { ...query });
  }

  /** The signed-in user's own record — avoids needing its id ahead of a token refresh. */
  me(): Observable<UserDetail> {
    return this.api.get<UserDetail>('/users/me');
  }

  /** All active patients, for the assign-patients picker. */
  listPatients(): Observable<UserDetail[]> {
    return this.api
      .getPaged<UserDetail[]>('/users', { role: 'PATIENT', page: 1, limit: 500 })
      .pipe(map((res) => res.data.filter((p) => p.isActive)));
  }

  /** All active doctors, for the lead-assignment and practitioner-filter pickers. */
  listDoctors(): Observable<UserDetail[]> {
    return this.api
      .getPaged<UserDetail[]>('/users', { role: 'DOCTOR', page: 1, limit: 500 })
      .pipe(map((res) => res.data.filter((d) => d.isActive)));
  }

  create(payload: CreateUserPayload): Observable<UserDetail> {
    return this.api.post<UserDetail>('/users', payload);
  }

  update(id: string, payload: UpdateUserPayload): Observable<UserDetail> {
    return this.api.patch<UserDetail>(`/users/${id}`, payload);
  }

  /** Soft delete — the backend sets isActive=false. */
  deactivate(id: string): Observable<unknown> {
    return this.api.delete(`/users/${id}`);
  }

  changePassword(id: string, payload: ChangePasswordPayload): Observable<unknown> {
    return this.api.patch(`/users/${id}/password`, payload);
  }

  /** Partial update — omitted fields keep their current value (backend merge semantics). */
  updateNotificationPrefs(id: string, payload: Partial<NotificationPrefs>): Observable<UserDetail> {
    return this.api.patch<UserDetail>(`/users/${id}/notification-prefs`, payload);
  }

  uploadAvatar(file: File): Observable<UserDetail> {
    const form = new FormData();
    form.append('file', file);
    return this.api.post<UserDetail>('/users/me/avatar', form);
  }

  assignedPatients(doctorId: string): Observable<UserDetail[]> {
    return this.api.get<UserDetail[]>(`/users/${doctorId}/patients`);
  }

  assignPatients(doctorId: string, patientIds: string[]): Observable<UserDetail[]> {
    return this.api.post<UserDetail[]>(`/users/${doctorId}/assign-patients`, { patientIds });
  }
}
