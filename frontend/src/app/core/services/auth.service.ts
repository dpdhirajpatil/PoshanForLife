import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { CurrentUser, LoginResponse, Role } from '../models/user.model';
import { ApiService } from './api.service';

const TOKEN_KEY = 'pfl.token';
const USER_KEY = 'pfl.user';

/**
 * Session state for the portal. The login endpoint itself arrives with the
 * users/auth feature prompt; the token/user plumbing here is final.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  private readonly _currentUser = signal<CurrentUser | null>(this.restoreUser());

  readonly currentUser = this._currentUser.asReadonly();
  readonly isLoggedIn = computed(() => this._currentUser() !== null);
  readonly isAdmin = computed(() => this._currentUser()?.role === 'ADMIN');

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  hasAnyRole(...roles: Role[]): boolean {
    const user = this._currentUser();
    return user !== null && roles.includes(user.role);
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.api
      .post<LoginResponse>('/auth/login', { email, password })
      .pipe(tap((res) => this.storeSession(res)));
  }

  logout(): void {
    this.clearSession();
    this.router.navigate(['/login']);
  }

  /** Called by the interceptor on 401 — drop the stale session and re-login. */
  handleSessionExpired(): void {
    this.clearSession();
    this.router.navigate(['/login'], {
      queryParams: { returnUrl: this.router.url },
    });
  }

  private storeSession(res: LoginResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    this._currentUser.set(res.user);
  }

  private clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._currentUser.set(null);
  }

  private restoreUser(): CurrentUser | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw || !localStorage.getItem(TOKEN_KEY)) return null;
    try {
      return JSON.parse(raw) as CurrentUser;
    } catch {
      return null;
    }
  }
}
