import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ApiError } from '../../core/models/api-response.model';
import { Gender, PatientDetail } from '../../core/models/patient.model';
import { UserDetail } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { applyServerFieldErrors } from '../../core/utils/form-errors';
import { UsersService } from '../users/users.service';
import { CreatePatientPayload, PatientsService } from './patients.service';

export interface PatientFormDialogData {
  patient?: PatientDetail; // absent → create mode
}

const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

/**
 * Create/edit patient. Two sections: account info and medical info.
 * The assigned-doctor select only exists for ADMIN callers — doctors are
 * auto-assigned by the backend. Password is optional on create; when blank
 * the backend generates a temp password which the list page shows once.
 */
@Component({
  selector: 'app-patient-form-dialog',
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
    <h2 mat-dialog-title>{{ isEdit ? 'Edit patient' : 'Add patient' }}</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="patient-form" (ngSubmit)="save()">
        <h3>Account info</h3>
        <div class="grid">
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

          <mat-form-field appearance="outline">
            <mat-label>Phone</mat-label>
            <input matInput formControlName="phone" />
          </mat-form-field>

          @if (!isEdit) {
            <mat-form-field appearance="outline">
              <mat-label>Password (optional)</mat-label>
              <input matInput formControlName="password" type="password" />
              <mat-hint>Leave blank to auto-generate a temporary password</mat-hint>
              @if (form.controls.password.hasError('server')) {
                <mat-error>{{ form.controls.password.getError('server') }}</mat-error>
              } @else if (form.controls.password.invalid) {
                <mat-error>At least 8 characters, including a digit</mat-error>
              }
            </mat-form-field>
          }

          @if (!isEdit && isAdmin) {
            <mat-form-field appearance="outline">
              <mat-label>Assign to doctor (optional)</mat-label>
              <mat-select formControlName="assignedDoctorId">
                <mat-option [value]="null">— None —</mat-option>
                @for (doc of doctors(); track doc.id) {
                  <mat-option [value]="doc.id">{{ doc.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          }
        </div>

        <h3>Medical info</h3>
        <div class="grid">
          <mat-form-field appearance="outline">
            <mat-label>Date of birth</mat-label>
            <input matInput formControlName="dateOfBirth" [matDatepicker]="dobPicker" />
            <mat-datepicker-toggle matIconSuffix [for]="dobPicker" />
            <mat-datepicker #dobPicker />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Gender</mat-label>
            <mat-select formControlName="gender">
              <mat-option [value]="null">—</mat-option>
              @for (g of genders; track g) {
                <mat-option [value]="g">{{ g }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Blood group</mat-label>
            <mat-select formControlName="bloodGroup">
              <mat-option [value]="null">—</mat-option>
              @for (bg of bloodGroups; track bg) {
                <mat-option [value]="bg">{{ bg }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Height (cm)</mat-label>
            <input matInput formControlName="heightCm" type="number" min="1" />
            @if (form.controls.heightCm.hasError('server')) {
              <mat-error>{{ form.controls.heightCm.getError('server') }}</mat-error>
            } @else if (form.controls.heightCm.invalid) {
              <mat-error>Height must be a positive number</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Emergency contact</mat-label>
            <input matInput formControlName="emergencyContact" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Medical history</mat-label>
            <textarea matInput formControlName="medicalHistory" rows="3"></textarea>
          </mat-form-field>
        </div>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()" [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          {{ isEdit ? 'Save changes' : 'Create patient' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .patient-form {
      padding-top: 8px;
      min-width: 560px;
    }
    .patient-form h3 {
      margin: 8px 0;
      font-size: 0.95rem;
      color: rgba(0, 0, 0, 0.65);
    }
    .grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 4px 16px;
    }
    .span-2 {
      grid-column: span 2;
    }
  `,
})
export class PatientFormDialogComponent {
  protected readonly ref = inject(MatDialogRef<PatientFormDialogComponent>);
  private readonly data = inject<PatientFormDialogData>(MAT_DIALOG_DATA);
  private readonly patientsService = inject(PatientsService);
  private readonly usersService = inject(UsersService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly isAdmin = inject(AuthService).isAdmin();
  protected readonly isEdit = !!this.data.patient;
  protected readonly saving = signal(false);
  protected readonly doctors = signal<UserDetail[]>([]);
  protected readonly genders: Gender[] = ['MALE', 'FEMALE', 'OTHER'];
  protected readonly bloodGroups = BLOOD_GROUPS;

  protected readonly form = this.fb.nonNullable.group({
    name: [this.data.patient?.name ?? '', [Validators.required, Validators.minLength(2)]],
    email: [
      { value: this.data.patient?.email ?? '', disabled: this.isEdit },
      [Validators.required, Validators.email],
    ],
    phone: [this.data.patient?.phone ?? ''],
    password: ['', [Validators.minLength(8), Validators.pattern(/\d/)]],
    assignedDoctorId: [null as string | null],
    dateOfBirth: [this.data.patient?.dateOfBirth ? new Date(this.data.patient.dateOfBirth) : null],
    gender: [this.data.patient?.gender ?? null],
    bloodGroup: [this.data.patient?.bloodGroup ?? null],
    heightCm: [this.data.patient?.heightCm ?? (null as number | null), Validators.min(1)],
    emergencyContact: [this.data.patient?.emergencyContact ?? ''],
    medicalHistory: [this.data.patient?.medicalHistory ?? ''],
  });

  constructor() {
    if (this.isAdmin && !this.isEdit) {
      this.usersService
        .list({ role: 'DOCTOR', page: 1, limit: 100 })
        .subscribe((res) => this.doctors.set(res.data.filter((d) => d.isActive)));
    }
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const medical = {
      phone: v.phone || undefined,
      dateOfBirth: v.dateOfBirth ? toIsoDate(v.dateOfBirth) : undefined,
      gender: v.gender ?? undefined,
      bloodGroup: v.bloodGroup ?? undefined,
      heightCm: v.heightCm ?? undefined,
      emergencyContact: v.emergencyContact || undefined,
      medicalHistory: v.medicalHistory || undefined,
    };

    const request = this.isEdit
      ? this.patientsService.update(this.data.patient!.id, { name: v.name, ...medical })
      : this.patientsService.create({
          name: v.name,
          email: v.email,
          password: v.password || undefined,
          assignedDoctorId: v.assignedDoctorId ?? undefined,
          ...medical,
        } satisfies CreatePatientPayload);

    this.saving.set(true);
    request.subscribe({
      next: (patient) => {
        this.toast.success(this.isEdit ? 'Patient updated' : 'Patient created');
        this.ref.close(patient);
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
