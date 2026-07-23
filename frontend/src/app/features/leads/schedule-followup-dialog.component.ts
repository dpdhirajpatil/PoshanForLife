import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiError } from '../../core/models/api-response.model';
import { LeadDetail } from '../../core/models/lead.model';
import { ToastService } from '../../core/services/toast.service';
import { SidePanelHandleComponent } from '../../shared/side-panel-handle.component';
import { LeadsService } from './leads.service';

export interface ScheduleFollowupDialogData {
  leadId: string;
  leadName: string;
}

/** Date + time picker + an optional custom notification message for the assigned practitioner. */
@Component({
  selector: 'app-schedule-followup-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    SidePanelHandleComponent,
  ],
  template: `
    <app-side-panel-handle />
    <h2 mat-dialog-title>Schedule follow-up for {{ data.leadName }}</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="grid">
        <mat-form-field appearance="outline">
          <mat-label>Date</mat-label>
          <input matInput [matDatepicker]="picker" formControlName="date" />
          <mat-datepicker-toggle matIconSuffix [for]="picker" />
          <mat-datepicker #picker />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Time</mat-label>
          <input matInput type="time" formControlName="time" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="span-2">
          <mat-label>Message (optional)</mat-label>
          <textarea matInput formControlName="message" rows="3"
                    placeholder="Custom note sent to the assigned practitioner"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()" [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Schedule
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      width: 100%;
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
export class ScheduleFollowupDialogComponent {
  protected readonly ref = inject(MatDialogRef<ScheduleFollowupDialogComponent>);
  protected readonly data = inject<ScheduleFollowupDialogData>(MAT_DIALOG_DATA);
  private readonly leadsService = inject(LeadsService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly saving = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    date: [new Date(), Validators.required],
    time: [roundedNextHourString(), [Validators.required, Validators.pattern(/^\d{2}:\d{2}$/)]],
    message: [''],
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const [hours, minutes] = v.time.split(':').map(Number);
    const followupAt = new Date(v.date);
    followupAt.setHours(hours, minutes, 0, 0);

    this.saving.set(true);
    this.leadsService
      .scheduleFollowup(this.data.leadId, {
        followupAt: followupAt.toISOString(),
        message: v.message || undefined,
      })
      .subscribe({
        next: (lead: LeadDetail) => {
          this.toast.success('Follow-up scheduled');
          this.ref.close(lead);
        },
        error: (err: ApiError) => {
          this.saving.set(false);
          this.toast.error(err.error);
        },
      });
  }
}

function roundedNextHourString(): string {
  const d = new Date();
  d.setHours(d.getHours() + 1, 0, 0, 0);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}
