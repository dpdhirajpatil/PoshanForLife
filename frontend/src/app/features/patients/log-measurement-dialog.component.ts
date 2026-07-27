import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiError } from '../../core/models/api-response.model';
import { ToastService } from '../../core/services/toast.service';
import { applyServerFieldErrors } from '../../core/utils/form-errors';
import { SidePanelHandleComponent } from '../../shared/side-panel-handle.component';
import { HealthRecordsService } from './health-records.service';

export interface LogMeasurementDialogData {
  patientId: string;
}

/**
 * ADMIN/DOCTOR quick-entry — a single data point without a full InBody PDF.
 * Posts to the same upsert endpoint the mobile app's manual-tracking screen
 * and Health Connect sync use; the backend forces source=manual for this
 * caller regardless of what's sent, and merges by calendar day.
 */
@Component({
  selector: 'app-log-measurement-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    SidePanelHandleComponent,
  ],
  template: `
    <app-side-panel-handle />
    <h2 mat-dialog-title>Log a measurement</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="measurement-form">
        <mat-form-field appearance="outline" class="span-2">
          <mat-label>Date</mat-label>
          <input matInput [matDatepicker]="picker" formControlName="recordDate" />
          <mat-datepicker-toggle matIconSuffix [for]="picker" />
          <mat-datepicker #picker />
          <mat-hint>Defaults to today · merges into that day's record if one exists</mat-hint>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Weight (kg)</mat-label>
          <input matInput type="number" step="0.1" formControlName="weightKg" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Body fat %</mat-label>
          <input matInput type="number" step="0.1" formControlName="bodyFatPct" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Skeletal muscle mass (kg)</mat-label>
          <input matInput type="number" step="0.1" formControlName="skeletalMuscleMassKg" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>BMI</mat-label>
          <input matInput type="number" step="0.1" formControlName="bmi" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Visceral fat level</mat-label>
          <input matInput type="number" step="0.1" formControlName="visceralFatLevel" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Body water (L)</mat-label>
          <input matInput type="number" step="0.1" formControlName="bodyWaterL" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Protein (kg)</mat-label>
          <input matInput type="number" step="0.1" formControlName="proteinKg" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Mineral (kg)</mat-label>
          <input matInput type="number" step="0.01" formControlName="mineralKg" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Basal metabolic rate</mat-label>
          <input matInput type="number" step="0.1" formControlName="basalMetabolicRate" />
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()" [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Save
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .measurement-form {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px 12px;
      padding-top: 8px;
    }
    .span-2 {
      grid-column: span 2;
    }
  `,
})
export class LogMeasurementDialogComponent {
  protected readonly ref = inject(MatDialogRef<LogMeasurementDialogComponent>);
  protected readonly data = inject<LogMeasurementDialogData>(MAT_DIALOG_DATA);
  private readonly healthRecordsService = inject(HealthRecordsService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly saving = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    recordDate: [new Date()],
    weightKg: [null as number | null, [Validators.min(0)]],
    bodyFatPct: [null as number | null, [Validators.min(0)]],
    skeletalMuscleMassKg: [null as number | null],
    bmi: [null as number | null],
    visceralFatLevel: [null as number | null],
    bodyWaterL: [null as number | null],
    proteinKg: [null as number | null],
    mineralKg: [null as number | null],
    basalMetabolicRate: [null as number | null],
  });

  protected save(): void {
    if (this.saving()) return;
    const v = this.form.getRawValue();
    const recordDate = v.recordDate ? this.toIsoDate(v.recordDate) : undefined;

    this.saving.set(true);
    this.healthRecordsService
      .logMeasurement({
        patientId: this.data.patientId,
        recordDate,
        weightKg: v.weightKg ?? undefined,
        bodyFatPct: v.bodyFatPct ?? undefined,
        skeletalMuscleMassKg: v.skeletalMuscleMassKg ?? undefined,
        bmi: v.bmi ?? undefined,
        visceralFatLevel: v.visceralFatLevel ?? undefined,
        bodyWaterL: v.bodyWaterL ?? undefined,
        proteinKg: v.proteinKg ?? undefined,
        mineralKg: v.mineralKg ?? undefined,
        basalMetabolicRate: v.basalMetabolicRate ?? undefined,
      })
      .subscribe({
        next: (result) => {
          this.saving.set(false);
          this.toast.success(result.upserted ? 'Measurement updated' : 'Measurement logged');
          this.ref.close(result.record);
        },
        error: (err: ApiError) => {
          this.saving.set(false);
          if (!applyServerFieldErrors(this.form, err)) {
            this.toast.error(err.error);
          }
        },
      });
  }

  private toIsoDate(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
