import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiError } from '../../core/models/api-response.model';
import { UserDetail } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { applyServerFieldErrors } from '../../core/utils/form-errors';
import { UsersService } from './users.service';

export interface ChangePasswordDialogData {
  user: UserDetail;
}

function matchValidator(control: AbstractControl): ValidationErrors | null {
  const newPassword = control.get('newPassword')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;
  if (newPassword && confirmPassword && newPassword !== confirmPassword) {
    control.get('confirmPassword')?.setErrors({ mismatch: true });
    return { mismatch: true };
  }
  return null;
}

/**
 * Change/reset password. currentPassword is only shown (and sent) when the
 * caller is changing their own password — an admin resetting someone else's
 * doesn't need it, matching the backend rule.
 */
@Component({
  selector: 'app-change-password-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>
      {{ isSelf ? 'Change your password' : 'Reset password — ' + data.user.name }}
    </h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="pwd-form" (ngSubmit)="save()">
        @if (isSelf) {
          <mat-form-field appearance="outline">
            <mat-label>Current password</mat-label>
            <input matInput formControlName="currentPassword" type="password" required />
            @if (form.controls.currentPassword.hasError('server')) {
              <mat-error>{{ form.controls.currentPassword.getError('server') }}</mat-error>
            } @else if (form.controls.currentPassword.invalid) {
              <mat-error>Current password is required</mat-error>
            }
          </mat-form-field>
        }

        <mat-form-field appearance="outline">
          <mat-label>New password</mat-label>
          <input matInput formControlName="newPassword" type="password" required />
          @if (form.controls.newPassword.hasError('server')) {
            <mat-error>{{ form.controls.newPassword.getError('server') }}</mat-error>
          } @else if (form.controls.newPassword.invalid) {
            <mat-error>At least 8 characters, including a digit</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Confirm new password</mat-label>
          <input matInput formControlName="confirmPassword" type="password" required />
          @if (form.controls.confirmPassword.hasError('mismatch')) {
            <mat-error>Passwords do not match</mat-error>
          } @else if (form.controls.confirmPassword.hasError('server')) {
            <mat-error>{{ form.controls.confirmPassword.getError('server') }}</mat-error>
          } @else if (form.controls.confirmPassword.invalid) {
            <mat-error>Please confirm the new password</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()" [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Update password
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .pwd-form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: 360px;
      padding-top: 8px;
    }
  `,
})
export class ChangePasswordDialogComponent {
  protected readonly ref = inject(MatDialogRef<ChangePasswordDialogComponent>);
  protected readonly data = inject<ChangePasswordDialogData>(MAT_DIALOG_DATA);
  private readonly usersService = inject(UsersService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly isSelf = this.auth.currentUser()?.id === this.data.user.id;
  protected readonly saving = signal(false);

  protected readonly form = this.fb.nonNullable.group(
    {
      currentPassword: ['', this.isSelf ? [Validators.required] : []],
      newPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/\d/)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: matchValidator },
  );

  save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.usersService
      .changePassword(this.data.user.id, {
        currentPassword: this.isSelf ? value.currentPassword : undefined,
        newPassword: value.newPassword,
        confirmPassword: value.confirmPassword,
      })
      .subscribe({
        next: () => {
          this.toast.success('Password updated');
          this.ref.close(true);
        },
        error: (err: ApiError) => {
          this.saving.set(false);
          if (!applyServerFieldErrors(this.form, err)) {
            this.toast.error(err.error);
          }
        },
      });
  }
}
