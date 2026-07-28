import { DatePipe, TitleCasePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { ApiError } from '../../core/models/api-response.model';
import {
  APPOINTMENT_STATUSES,
  APPOINTMENT_STATUS_LABELS,
  Appointment,
  AppointmentStatus,
} from '../../core/models/appointment.model';
import { ToastService } from '../../core/services/toast.service';
import { AppointmentsService } from './appointments.service';

/**
 * Shared detail/edit view for both the practitioner schedule and the admin
 * overview — full field access (status + notes), matching the backend's
 * DOCTOR/ADMIN update rule (unlike the mobile PATIENT flow, which is
 * restricted to reschedule/cancel only). The list and detail DTOs are the
 * same shape here, so the whole object is passed in rather than re-fetching
 * by id.
 */
@Component({
  selector: 'app-appointment-detail-dialog',
  standalone: true,
  imports: [
    DatePipe,
    TitleCasePipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>Appointment detail</h2>

    <mat-dialog-content>
      <div class="detail">
        <div class="row">
          <span class="label">Patient</span>
          <span>
            {{ appointment.patient.name }}
            <button mat-button class="link" (click)="viewPatient()">View profile</button>
          </span>
        </div>
        <div class="row">
          <span class="label">Practitioner</span>
          <span>{{ appointment.practitioner.name }}</span>
        </div>
        <div class="row">
          <span class="label">When</span>
          <span>
            {{ appointment.scheduledAt | date: 'medium' }}
            <span class="muted">· {{ appointment.durationMinutes }} min</span>
            @if (appointment.isVideo) {
              <span class="badge status-scheduled">
                <mat-icon inline>videocam</mat-icon>
                Video
              </span>
            }
          </span>
        </div>
        <div class="row">
          <span class="label">Booked by</span>
          <span>{{ appointment.createdBy?.name ?? '—' }}</span>
        </div>

        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Status</mat-label>
          <mat-select [formControl]="status">
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ statusLabel(s) }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Notes</mat-label>
          <textarea matInput [formControl]="notes" rows="4" placeholder="Post-appointment notes"></textarea>
        </mat-form-field>
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close(changed())">Close</button>
      <button
        mat-flat-button
        color="primary"
        [disabled]="saving() || !isDirty()"
        (click)="save()"
      >
        {{ saving() ? 'Saving…' : 'Save' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .detail {
      display: flex;
      flex-direction: column;
      gap: 14px;
      padding: 8px 0;
      width: 100%;
      max-width: 420px;
    }
    .row {
      display: flex;
      gap: 12px;
      align-items: baseline;
    }
    .label {
      width: 90px;
      flex-shrink: 0;
      font-size: 0.82rem;
      color: var(--muted-foreground);
    }
    .muted {
      color: var(--muted-foreground);
    }
    .link {
      padding: 0 4px;
      min-width: 0;
      vertical-align: baseline;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      margin-left: 6px;
    }
    .status-scheduled {
      background: var(--badge-blue-bg);
      color: var(--badge-blue-fg);
    }
    mat-form-field {
      width: 100%;
    }
  `,
})
export class AppointmentDetailDialogComponent {
  protected readonly ref = inject(MatDialogRef<AppointmentDetailDialogComponent>);
  protected readonly appointment = inject<Appointment>(MAT_DIALOG_DATA);
  private readonly appointmentsService = inject(AppointmentsService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly statuses = APPOINTMENT_STATUSES;
  protected readonly saving = signal(false);
  protected readonly changed = signal(false);

  protected readonly status = new FormControl<AppointmentStatus>(this.appointment.status, {
    nonNullable: true,
  });
  protected readonly notes = new FormControl(this.appointment.notes ?? '', { nonNullable: true });

  protected statusLabel(status: AppointmentStatus): string {
    return APPOINTMENT_STATUS_LABELS[status];
  }

  protected isDirty(): boolean {
    return (
      this.status.value !== this.appointment.status ||
      this.notes.value !== (this.appointment.notes ?? '')
    );
  }

  protected viewPatient(): void {
    this.ref.close(this.changed());
    this.router.navigate(['/patients', this.appointment.patient.id]);
  }

  protected save(): void {
    if (!this.isDirty() || this.saving()) return;
    this.saving.set(true);
    this.appointmentsService
      .update(this.appointment.id, { status: this.status.value, notes: this.notes.value })
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          this.changed.set(true);
          this.toast.success('Appointment updated');
          this.ref.close(true);
          void updated;
        },
        error: (err: ApiError) => {
          this.saving.set(false);
          this.toast.error(err.error);
        },
      });
  }
}
