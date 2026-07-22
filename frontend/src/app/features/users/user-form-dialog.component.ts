import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatNativeDateModule } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ApiError } from '../../core/models/api-response.model';
import { Role, UserDetail } from '../../core/models/user.model';
import { ToastService } from '../../core/services/toast.service';
import { applyServerFieldErrors } from '../../core/utils/form-errors';
import { UsersService } from './users.service';

export interface UserFormDialogData {
  user?: UserDetail; // absent → create mode
}

/** Create/edit user form. Validation mirrors the backend rules. */
@Component({
  selector: 'app-user-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ isEdit ? 'Edit user' : 'Add user' }}</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="user-form" (ngSubmit)="save()">
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" required />
          @if (form.controls.name.hasError('server')) {
            <mat-error>{{ form.controls.name.getError('server') }}</mat-error>
          } @else if (form.controls.name.invalid) {
            <mat-error>Name must be at least 2 characters</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Email</mat-label>
          <input matInput formControlName="email" required type="email" />
          @if (form.controls.email.hasError('conflict')) {
            <mat-error>An account with this email already exists</mat-error>
          } @else if (form.controls.email.hasError('server')) {
            <mat-error>{{ form.controls.email.getError('server') }}</mat-error>
          } @else if (form.controls.email.invalid) {
            <mat-error>A valid email is required</mat-error>
          }
        </mat-form-field>

        @if (!isEdit) {
          <mat-form-field appearance="outline">
            <mat-label>Password</mat-label>
            <input matInput formControlName="password" required type="password" />
            @if (form.controls.password.hasError('server')) {
              <mat-error>{{ form.controls.password.getError('server') }}</mat-error>
            } @else if (form.controls.password.invalid) {
              <mat-error>At least 8 characters, including a digit</mat-error>
            }
          </mat-form-field>
        }

        <mat-form-field appearance="outline">
          <mat-label>Role</mat-label>
          <mat-select formControlName="role" required>
            @for (role of roles; track role) {
              <mat-option [value]="role">{{ role }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Phone</mat-label>
          <input matInput formControlName="phone" />
          @if (form.controls.phone.hasError('server')) {
            <mat-error>{{ form.controls.phone.getError('server') }}</mat-error>
          }
        </mat-form-field>

        @if (isEdit) {
          <mat-form-field appearance="outline">
            <mat-label>Date of birth</mat-label>
            <input matInput formControlName="dateOfBirth" [matDatepicker]="dobPicker" />
            <mat-datepicker-toggle matIconSuffix [for]="dobPicker" />
            <mat-datepicker #dobPicker />
          </mat-form-field>
        }
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()" [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          {{ isEdit ? 'Save changes' : 'Create user' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .user-form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      width: 100%;
      max-width: 380px;
      padding-top: 8px;
    }
  `,
})
export class UserFormDialogComponent {
  protected readonly ref = inject(MatDialogRef<UserFormDialogComponent>);
  private readonly data = inject<UserFormDialogData>(MAT_DIALOG_DATA);
  private readonly usersService = inject(UsersService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly roles: Role[] = ['ADMIN', 'DOCTOR', 'PATIENT'];
  protected readonly isEdit = !!this.data.user;
  protected readonly saving = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    name: [this.data.user?.name ?? '', [Validators.required, Validators.minLength(2)]],
    email: [
      { value: this.data.user?.email ?? '', disabled: this.isEdit },
      [Validators.required, Validators.email],
    ],
    password: [
      '',
      this.isEdit ? [] : [Validators.required, Validators.minLength(8), Validators.pattern(/\d/)],
    ],
    role: [this.data.user?.role ?? ('DOCTOR' as Role), Validators.required],
    phone: [this.data.user?.phone ?? ''],
    dateOfBirth: [this.data.user?.dateOfBirth ? new Date(this.data.user.dateOfBirth) : null],
  });

  save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request = this.isEdit
      ? this.usersService.update(this.data.user!.id, {
          name: value.name,
          phone: value.phone || undefined,
          role: value.role,
          dateOfBirth: value.dateOfBirth ? toIsoDate(value.dateOfBirth) : undefined,
        })
      : this.usersService.create({
          name: value.name,
          email: value.email,
          password: value.password,
          role: value.role,
          phone: value.phone || undefined,
        });

    this.saving.set(true);
    request.subscribe({
      next: (user) => {
        this.toast.success(this.isEdit ? 'User updated' : 'User created');
        this.ref.close(user);
      },
      error: (err: ApiError) => {
        this.saving.set(false);
        if (err.code === 'EMAIL_CONFLICT') {
          this.form.controls.email.setErrors({ conflict: true });
          this.form.controls.email.markAsTouched();
        } else if (!applyServerFieldErrors(this.form, err)) {
          this.toast.error(err.error);
        }
      },
    });
  }
}

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
