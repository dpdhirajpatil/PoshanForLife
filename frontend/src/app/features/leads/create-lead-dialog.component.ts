import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ApiError } from '../../core/models/api-response.model';
import { LEAD_SOURCES, LeadSource } from '../../core/models/lead.model';
import { Gender } from '../../core/models/patient.model';
import { UserDetail } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { UsersService } from '../users/users.service';
import { LeadsService } from './leads.service';

/** name + source required; everything else optional — a fast, low-friction capture form. */
@Component({
  selector: 'app-create-lead-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Add lead</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="grid">
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" />
          @if (form.controls.name.invalid && form.controls.name.touched) {
            <mat-error>Name is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Source</mat-label>
          <mat-select formControlName="source">
            @for (s of sources; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Phone (optional)</mat-label>
          <input matInput formControlName="phone" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Email (optional)</mat-label>
          <input matInput formControlName="email" />
          @if (form.controls.email.hasError('email')) {
            <mat-error>Enter a valid email</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>City (optional)</mat-label>
          <input matInput formControlName="city" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Age (optional)</mat-label>
          <input matInput type="number" formControlName="age" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Gender (optional)</mat-label>
          <mat-select formControlName="gender">
            <mat-option [value]="null">—</mat-option>
            @for (g of genders; track g) {
              <mat-option [value]="g">{{ g }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        @if (isAdmin) {
          <mat-form-field appearance="outline">
            <mat-label>Assign to (optional)</mat-label>
            <mat-select formControlName="assignedPractitionerId">
              <mat-option [value]="null">— Unassigned —</mat-option>
              @for (doc of doctors(); track doc.id) {
                <mat-option [value]="doc.id">{{ doc.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }

        <mat-form-field appearance="outline" class="span-2">
          <mat-label>Health goal (optional)</mat-label>
          <input matInput formControlName="healthGoal" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="span-2">
          <mat-label>Notes (optional)</mat-label>
          <textarea matInput formControlName="notes" rows="3"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()" [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Add lead
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      width: 100%;
      max-width: 560px;
    }
    .grid {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .span-2 {
      width: 100%;
    }
  `,
})
export class CreateLeadDialogComponent {
  protected readonly ref = inject(MatDialogRef<CreateLeadDialogComponent>);
  private readonly leadsService = inject(LeadsService);
  private readonly usersService = inject(UsersService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly sources = LEAD_SOURCES;
  protected readonly genders: Gender[] = ['MALE', 'FEMALE', 'OTHER'];
  protected readonly isAdmin = inject(AuthService).isAdmin();
  protected readonly saving = signal(false);
  protected readonly doctors = signal<UserDetail[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    source: ['website' as LeadSource, Validators.required],
    phone: [''],
    email: ['', Validators.email],
    city: [''],
    age: [null as number | null, [Validators.min(0), Validators.max(150)]],
    gender: [null as Gender | null],
    assignedPractitionerId: [null as string | null],
    healthGoal: [''],
    notes: [''],
  });

  constructor() {
    if (this.isAdmin) {
      this.usersService.listDoctors().subscribe((doctors) => this.doctors.set(doctors));
    }
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.leadsService
      .create({
        name: v.name,
        source: v.source,
        phone: v.phone || undefined,
        email: v.email || undefined,
        city: v.city || undefined,
        age: v.age ?? undefined,
        gender: v.gender ?? undefined,
        assignedPractitionerId: v.assignedPractitionerId ?? undefined,
        healthGoal: v.healthGoal || undefined,
        notes: v.notes || undefined,
      })
      .subscribe({
        next: (lead) => {
          this.toast.success('Lead added');
          this.ref.close(lead);
        },
        error: (err: ApiError) => {
          this.saving.set(false);
          this.toast.error(err.error);
        },
      });
  }
}
