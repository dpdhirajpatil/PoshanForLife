import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';
import { AuthResponse, CurrentUser, Role } from '../models/user.model';
import { ApiService } from './api.service';
import { ToastService } from './toast.service';

/**
 * Session state for the portal.
 *
 * Token storage strategy: the short-lived access token and the user live in
 * memory only (immune to persistent XSS harvesting of storage). The refresh
 * token is kept in localStorage so the session survives reloads; its exposure
 * is capped by server-side rotation (each use invalidates it) and revocation
 * on logout. Hardening upgrade later: have the backend set it as an httpOnly
 * cookie instead — only this service would change.
 */
const REFRESH_TOKEN_KEY = 'pfl.refresh';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  private accessToken: string | null = null;
  private refreshInFlight$: Observable<void> | null = null;

  private readonly _currentUser = signal<CurrentUser | null>(null);

  readonly currentUser = this._currentUser.asReadonly();
  readonly isLoggedIn = computed(() => this._currentUser() !== null);
  readonly isAdmin = computed(() => this._currentUser()?.role === 'ADMIN');

  get token(): string | null {
    return this.accessToken;
  }

  hasAnyRole(...roles: Role[]): boolean {
    const user = this._currentUser();
    return user !== null && roles.includes(user.role);
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.api
      .post<AuthResponse>('/auth/login', { email, password })
      .pipe(tap((res) => this.storeSession(res)));
  }

  /**
   * Restores the session on app startup: if a refresh token survived the
   * reload, silently exchange it for a fresh access token. Always resolves —
   * a failed restore just means the user starts logged out.
   */
  initSession(): Observable<void> {
    if (!localStorage.getItem(REFRESH_TOKEN_KEY)) {
      return of(void 0);
    }
    return this.refreshSession().pipe(catchError(() => of(void 0)));
  }

  /**
   * Exchanges the stored refresh token for a new access token (rotating the
   * refresh token). Concurrent callers share one in-flight request.
   */
  refreshSession(): Observable<void> {
    if (!this.refreshInFlight$) {
      const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
      if (!refreshToken) {
        return throwError(() => new Error('No refresh token'));
      }
      this.refreshInFlight$ = this.api
        .post<AuthResponse>('/auth/refresh', { refreshToken })
        .pipe(
          tap((res) => this.storeSession(res)),
          map(() => void 0),
          catchError((err) => {
            this.clearSession();
            return throwError(() => err);
          }),
          finalize(() => (this.refreshInFlight$ = null)),
          shareReplay(1),
        );
    }
    return this.refreshInFlight$;
  }

  logout(): void {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (refreshToken) {
      // Best-effort server-side revocation; the local session dies regardless.
      this.api.post('/auth/logout', { refreshToken }).subscribe({ error: () => undefined });
    }
    this.clearSession();
    this.router.navigate(['/login']);
  }

  /** Called by the interceptor when a 401 survives the silent-refresh attempt. */
  handleSessionExpired(): void {
    this.clearSession();
    this.toast.error('Your session has expired. Please sign in again.');
    this.router.navigate(['/login'], {
      queryParams: { returnUrl: this.router.url },
    });
  }

  private storeSession(res: AuthResponse): void {
    this.accessToken = res.accessToken;
    localStorage.setItem(REFRESH_TOKEN_KEY, res.refreshToken);
    this._currentUser.set(res.user);
  }

  private clearSession(): void {
    this.accessToken = null;
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this._currentUser.set(null);
  }
}
