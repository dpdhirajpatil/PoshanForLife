import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Paged } from '../../core/models/api-response.model';
import { Role, UserDetail } from '../../core/models/user.model';
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

  /** All active patients, for the assign-patients picker. */
  listPatients(): Observable<UserDetail[]> {
    return this.api
      .getPaged<UserDetail[]>('/users', { role: 'PATIENT', page: 1, limit: 500 })
      .pipe(map((res) => res.data.filter((p) => p.isActive)));
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

  assignedPatients(doctorId: string): Observable<UserDetail[]> {
    return this.api.get<UserDetail[]>(`/users/${doctorId}/patients`);
  }

  assignPatients(doctorId: string, patientIds: string[]): Observable<UserDetail[]> {
    return this.api.post<UserDetail[]>(`/users/${doctorId}/assign-patients`, { patientIds });
  }
}
