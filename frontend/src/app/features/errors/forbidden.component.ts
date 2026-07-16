import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/** 403 page — shown when an authenticated user lacks the required role. */
@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, RouterLink],
  template: `
    <div class="forbidden-wrap">
      <mat-icon class="forbidden-icon">block</mat-icon>
      <h1>403 — Access denied</h1>
      <p>Your account ({{ auth.currentUser()?.email }}) does not have permission to view this page.</p>
      <div class="forbidden-actions">
        <a mat-flat-button color="primary" routerLink="/dashboard">Go to dashboard</a>
        <button mat-button (click)="auth.logout()">Sign in as someone else</button>
      </div>
    </div>
  `,
  styles: `
    .forbidden-wrap {
      min-height: 100vh;
      display: grid;
      place-content: center;
      justify-items: center;
      text-align: center;
      gap: 8px;
      padding: 24px;
    }
    .forbidden-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
      color: var(--mat-sys-error, #b3261e);
    }
    .forbidden-actions {
      display: flex;
      gap: 12px;
      margin-top: 8px;
    }
  `,
})
export class ForbiddenComponent {
  protected readonly auth = inject(AuthService);
}
