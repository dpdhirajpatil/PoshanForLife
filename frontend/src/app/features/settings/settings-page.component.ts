import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ApiError } from '../../core/models/api-response.model';
import { NotificationPrefs, UserDetail } from '../../core/models/user.model';
import { ToastService } from '../../core/services/toast.service';
import { initials } from '../../core/utils/initials';
import { ROLE_BADGE_CLASSES, ROLE_BADGE_LABELS } from '../../core/utils/role-badge';
import { ThemeToggleComponent } from '../../shared/theme-toggle.component';
import { ChangePasswordDialogComponent } from '../users/change-password-dialog.component';
import { UsersService } from '../users/users.service';

const MAX_AVATAR_BYTES = 5 * 1024 * 1024;
const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];

const PREF_LABELS: Record<keyof NotificationPrefs, string> = {
  inbodyReport: 'InBody report processed',
  patientAssigned: 'Patient assigned to you',
  processingErrors: 'Report processing errors',
  systemAnnouncements: 'System announcements',
};

/**
 * Profile + account settings. Deliberately simple — a low-traffic screen:
 * one fetch (GET /users/me) drives the profile form, avatar, account info,
 * and prefs toggles; "Change password" reuses the existing admin dialog
 * (ChangePasswordDialogComponent already handles the self-vs-admin
 * currentPassword distinction). Email is read-only: the backend has no field
 * to change it at all (UpdateUserRequest has no email), since it's the login
 * identifier — supporting changes would mean re-verifying uniqueness and
 * probably a confirmation-email flow, out of scope here.
 */
@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    MatDialogModule,
    ThemeToggleComponent,
  ],
  template: `
    <div class="page-header">
      <h1>Settings</h1>
    </div>

    @if (loading()) {
      <mat-card appearance="outlined"><p class="stub">Loading…</p></mat-card>
    } @else {
      @let u = user()!;
      <div class="settings-grid">
        <mat-card appearance="outlined" class="profile-card">
          <h2>Profile</h2>

          <div class="avatar-row">
            <div class="avatar">
              @if (u.avatarUrl) {
                <img [src]="u.avatarUrl" alt="" />
              } @else {
                <span>{{ initialsOf(u.name) }}</span>
              }
            </div>
            <div class="avatar-actions">
              <button mat-stroked-button type="button" [disabled]="uploadingAvatar()" (click)="fileInput.click()">
                @if (uploadingAvatar()) {
                  <mat-spinner diameter="18" />
                } @else {
                  Change photo
                }
              </button>
              <input #fileInput type="file" accept="image/*" hidden (change)="onAvatarSelected($event)" />
              <p class="hint">JPEG, PNG, WebP or GIF, up to 5 MB</p>
            </div>
          </div>

          <form [formGroup]="profileForm" class="profile-form" (ngSubmit)="saveProfile()">
            <mat-form-field appearance="outline">
              <mat-label>Name</mat-label>
              <input matInput formControlName="name" required />
              @if (profileForm.controls.name.hasError('server')) {
                <mat-error>{{ profileForm.controls.name.getError('server') }}</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Phone</mat-label>
              <input matInput formControlName="phone" />
            </mat-form-field>

            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>Email</mat-label>
              <input matInput [value]="u.email" disabled />
              <mat-hint>Email is your login identifier and can't be changed here.</mat-hint>
            </mat-form-field>

            <button
              mat-flat-button
              color="primary"
              type="submit"
              [disabled]="profileForm.invalid || profileForm.pristine || savingProfile()"
            >
              @if (savingProfile()) {
                <mat-spinner diameter="18" />
              } @else {
                Save changes
              }
            </button>
          </form>
        </mat-card>

        <mat-card appearance="outlined" class="account-card">
          <h2>Account</h2>
          <div class="account-row">
            <span class="label">Role</span>
            <span class="badge" [class]="roleBadgeClass(u.role)">{{ roleBadgeLabel(u.role) }}</span>
          </div>
          <div class="account-row">
            <span class="label">Account created</span>
            <span>{{ u.createdAt | date: 'mediumDate' }}</span>
          </div>

          <div class="divider"></div>

          <h2>Password</h2>
          <button mat-stroked-button type="button" (click)="openChangePassword(u)">Change password</button>
        </mat-card>

        <mat-card appearance="outlined" class="appearance-card">
          <h2>Appearance</h2>
          <p class="hint appearance-hint">Choose how Poshan for Life looks on this device.</p>
          <app-theme-toggle />
        </mat-card>

        <mat-card appearance="outlined" class="prefs-card">
          <h2>Notification preferences</h2>
          <div class="prefs-list">
            @for (key of prefKeys; track key) {
              <div class="pref-row">
                <span>{{ prefLabel(key) }}</span>
                <mat-slide-toggle
                  [checked]="u.notificationPrefs[key]"
                  [disabled]="savingPrefs().has(key)"
                  (change)="togglePref(key, $event.checked)"
                />
              </div>
            }
          </div>
        </mat-card>
      </div>
    }
  `,
  styles: `
    .page-header {
      margin-bottom: 16px;
    }
    .page-header h1 {
      margin: 0;
      font-size: 1.5rem;
    }
    .settings-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 16px;
      align-items: start;
    }
    mat-card {
      padding: 20px;
    }
    h2 {
      margin: 0 0 16px;
      font-size: 1rem;
      font-weight: 600;
    }
    .avatar-row {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 20px;
    }
    .avatar {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      overflow: hidden;
      background: var(--primary-100, var(--muted));
      color: var(--primary-700, var(--foreground));
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 600;
      flex-shrink: 0;
    }
    .avatar img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .avatar-actions .hint,
    .appearance-hint {
      margin: 6px 0 0;
      font-size: 0.75rem;
      color: var(--muted-foreground);
    }
    .appearance-card app-theme-toggle {
      display: block;
      margin-top: 12px;
    }
    .profile-form {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .account-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 0;
    }
    .account-row .label {
      color: var(--muted-foreground);
      font-size: 0.9rem;
    }
    .badge {
      border-radius: 999px;
      padding: 2px 10px;
      font-size: 0.75rem;
      font-weight: 500;
    }
    .divider {
      height: 1px;
      background: var(--border);
      margin: 16px 0;
    }
    .prefs-list {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .pref-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 0;
      border-bottom: 1px solid var(--border);
    }
    .pref-row:last-child {
      border-bottom: none;
    }
    .stub {
      color: var(--muted-foreground);
    }
  `,
})
export class SettingsPageComponent {
  private readonly usersService = inject(UsersService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(MatDialog);
  private readonly fb = inject(FormBuilder);

  protected readonly prefKeys = Object.keys(PREF_LABELS) as (keyof NotificationPrefs)[];

  protected readonly loading = signal(true);
  protected readonly user = signal<UserDetail | null>(null);
  protected readonly savingProfile = signal(false);
  protected readonly uploadingAvatar = signal(false);
  protected readonly savingPrefs = signal<Set<keyof NotificationPrefs>>(new Set());

  protected readonly profileForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    phone: [''],
  });

  constructor() {
    this.usersService.me().subscribe({
      next: (u) => {
        this.user.set(u);
        this.profileForm.setValue({ name: u.name, phone: u.phone ?? '' });
        this.profileForm.markAsPristine();
        this.loading.set(false);
      },
      error: (err: ApiError) => {
        this.loading.set(false);
        this.toast.error(err.error);
      },
    });
  }

  protected initialsOf(name: string): string {
    return initials(name);
  }

  protected roleBadgeClass(role: string): string {
    return ROLE_BADGE_CLASSES[role] ?? 'bg-muted text-muted-foreground';
  }

  protected roleBadgeLabel(role: string): string {
    return ROLE_BADGE_LABELS[role] ?? role;
  }

  protected prefLabel(key: keyof NotificationPrefs): string {
    return PREF_LABELS[key];
  }

  protected saveProfile(): void {
    const current = this.user();
    if (!current || this.profileForm.invalid || this.savingProfile()) return;

    const { name, phone } = this.profileForm.getRawValue();
    this.savingProfile.set(true);
    this.usersService.update(current.id, { name, phone: phone || undefined }).subscribe({
      next: (updated) => {
        this.user.set(updated);
        this.profileForm.markAsPristine();
        this.savingProfile.set(false);
        this.toast.success('Profile updated');
      },
      error: (err: ApiError) => {
        this.savingProfile.set(false);
        this.toast.error(err.error);
      },
    });
  }

  protected onAvatarSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
      this.toast.error('Only JPEG, PNG, WebP or GIF images are allowed');
      return;
    }
    if (file.size > MAX_AVATAR_BYTES) {
      this.toast.error('Image must be 5 MB or smaller');
      return;
    }

    this.uploadingAvatar.set(true);
    this.usersService.uploadAvatar(file).subscribe({
      next: (updated) => {
        this.user.set(updated);
        this.uploadingAvatar.set(false);
        this.toast.success('Photo updated');
      },
      error: (err: ApiError) => {
        this.uploadingAvatar.set(false);
        this.toast.error(err.error);
      },
    });
  }

  protected openChangePassword(user: UserDetail): void {
    this.dialog.open(ChangePasswordDialogComponent, { data: { user } });
  }

  protected togglePref(key: keyof NotificationPrefs, checked: boolean): void {
    const current = this.user();
    if (!current) return;

    this.savingPrefs.update((set) => new Set(set).add(key));
    this.usersService.updateNotificationPrefs(current.id, { [key]: checked }).subscribe({
      next: (updated) => {
        this.user.set(updated);
        this.savingPrefs.update((set) => {
          const next = new Set(set);
          next.delete(key);
          return next;
        });
      },
      error: (err: ApiError) => {
        this.savingPrefs.update((set) => {
          const next = new Set(set);
          next.delete(key);
          return next;
        });
        this.toast.error(err.error);
      },
    });
  }
}
