import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ApiError } from '../../core/models/api-response.model';
import { AssignmentStatus, PatientProgramme } from '../../core/models/patient-programme.model';
import { ToastService } from '../../core/services/toast.service';
import { SidePanelHandleComponent } from '../../shared/side-panel-handle.component';
import { PatientProgrammesService } from './patient-programmes.service';

export interface EditAssignmentDialogData {
  patientId: string;
  assignment: PatientProgramme;
}

/**
 * Edit an existing assignment: status, dates and notes only. The service
 * itself is shown read-only — assigning a different catalogue item is a new
 * assignment, matching the backend rule.
 */
@Component({
  selector: 'app-edit-assignment-dialog',
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
    SidePanelHandleComponent,
  ],
  template: `
    <app-side-panel-handle />
    <h2 mat-dialog-title>Edit assignment</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="grid" (ngSubmit)="save()">
        <mat-form-field appearance="outline" class="span-2" subscriptSizing="dynamic">
          <mat-label>Service</mat-label>
          <input matInput [value]="serviceLabel" disabled />
          <mat-hint>Cannot be changed — assign a new service instead</mat-hint>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select formControlName="status">
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Start date</mat-label>
          <input matInput formControlName="startDate" [matDatepicker]="startPicker" />
          <mat-datepicker-toggle matIconSuffix [for]="startPicker" />
          <mat-datepicker #startPicker />
        </mat-form-field>

        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>End date</mat-label>
          <input matInput formControlName="endDate" [matDatepicker]="endPicker" />
          <mat-datepicker-toggle matIconSuffix [for]="endPicker" />
          <mat-datepicker #endPicker />
          <mat-hint>Leave as-is to re-derive when the start date moves</mat-hint>
        </mat-form-field>

        <mat-form-field appearance="outline" class="span-2">
          <mat-label>Notes</mat-label>
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
          Save changes
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .grid {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding-top: 8px;
      width: 100%;
    }
    .span-2 {
      width: 100%;
    }
    mat-select,
    mat-option {
      text-transform: capitalize;
    }
  `,
})
export class EditAssignmentDialogComponent {
  protected readonly ref = inject(MatDialogRef<EditAssignmentDialogComponent>);
  private readonly data = inject<EditAssignmentDialogData>(MAT_DIALOG_DATA);
  private readonly programmesService = inject(PatientProgrammesService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly saving = signal(false);
  protected readonly statuses: AssignmentStatus[] = ['active', 'completed', 'cancelled'];

  protected readonly serviceLabel =
    `${this.data.assignment.catalogueItem?.name ?? 'Deleted item'} (${this.data.assignment.serviceType})`;

  protected readonly form = this.fb.nonNullable.group({
    status: [this.data.assignment.status],
    startDate: [new Date(this.data.assignment.startDate)],
    endDate: [this.data.assignment.endDate ? new Date(this.data.assignment.endDate) : null],
    notes: [this.data.assignment.notes ?? ''],
  });

  save(): void {
    if (this.saving()) return;
    const v = this.form.getRawValue();
    const original = this.data.assignment;
    const startDate = toIsoDate(v.startDate);
    const endDate = v.endDate ? toIsoDate(v.endDate) : undefined;

    this.saving.set(true);
    this.programmesService
      .update(this.data.patientId, original.id, {
        status: v.status,
        startDate: startDate !== original.startDate ? startDate : undefined,
        // only send endDate when the user actually moved it, so a start-date
        // change lets the backend re-derive the end from the duration
        endDate: endDate !== (original.endDate ?? undefined) ? endDate : undefined,
        notes: v.notes !== (original.notes ?? '') ? v.notes : undefined,
      })
      .subscribe({
        next: (updated) => {
          this.toast.success('Assignment updated');
          this.ref.close(updated);
        },
        error: (err: ApiError) => {
          this.saving.set(false);
          this.toast.error(err.error);
        },
      });
  }
}

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
