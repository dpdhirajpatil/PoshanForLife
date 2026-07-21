import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import { PatientSummary } from '../../core/models/patient.model';
import { InBodyData } from '../../core/models/report.model';
import { ToastService } from '../../core/services/toast.service';
import { PatientsService } from '../patients/patients.service';
import { INBODY_FIELD_GROUPS } from './inbody-field-groups';
import { ReportsService } from './reports.service';

const MAX_BYTES = 10 * 1024 * 1024;

type Step = 'patient' | 'upload' | 'review';

/**
 * Upload pipeline dialog: pick a patient, drag-drop (or browse) the InBody
 * PDF — the backend runs extraction synchronously and this dialog shows an
 * indeterminate progress bar while it works — then a review screen with the
 * extracted fields, editable since confidence may be "low". Saving corrected
 * values PATCHes the report, which re-upserts the linked HealthRecord too.
 */
@Component({
  selector: 'app-upload-report-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Upload InBody report</h2>

    <mat-dialog-content>
      @if (step() === 'patient') {
        <mat-form-field appearance="outline" class="full" subscriptSizing="dynamic">
          <mat-label>Search patients by name or email</mat-label>
          <input matInput [formControl]="search" placeholder="e.g. Neha Kapoor" />
          <mat-icon matPrefix>search</mat-icon>
        </mat-form-field>

        @if (loadingPatients()) {
          <mat-progress-bar mode="indeterminate" />
        }

        <div class="patient-list" role="listbox" aria-label="Patients">
          @for (p of patients(); track p.id) {
            <button type="button" class="patient-row" (click)="selectPatient(p)">
              <div class="patient-main">
                <span class="patient-name">{{ p.name }}</span>
                <span class="patient-meta">{{ p.email }}</span>
              </div>
              <mat-icon>chevron_right</mat-icon>
            </button>
          } @empty {
            @if (!loadingPatients() && search.value) {
              <p class="muted">No patients match "{{ search.value }}".</p>
            }
          }
        </div>
      }

      @if (step() === 'upload') {
        <div class="selected-patient">
          <div><strong>{{ selectedPatient()!.name }}</strong> <span class="muted">· {{ selectedPatient()!.email }}</span></div>
          <button mat-button type="button" (click)="step.set('patient')">Change</button>
        </div>

        <div
          class="dropzone"
          [class.dragover]="dragOver()"
          (dragover)="onDragOver($event)"
          (dragleave)="dragOver.set(false)"
          (drop)="onDrop($event)"
          (click)="fileInput.click()"
          role="button"
          tabindex="0"
          (keydown.enter)="fileInput.click()"
          aria-label="Upload InBody PDF"
        >
          @if (uploading()) {
            <mat-spinner diameter="32" />
            <span class="hint">Extracting report data — this can take a few seconds…</span>
          } @else {
            <mat-icon>picture_as_pdf</mat-icon>
            <span class="hint">Drag a PDF here or click to browse</span>
            <span class="sub-hint">PDF only · max 10 MB</span>
          }
        </div>
        @if (uploading()) {
          <mat-progress-bar mode="indeterminate" />
        }
        <input #fileInput type="file" hidden accept="application/pdf" (change)="onFileSelected($event)" />
      }

      @if (step() === 'review') {
        @if (warnings().length > 0) {
          <div class="warning-banner">
            <mat-icon>warning</mat-icon>
            <div>
              @for (w of warnings(); track w) {
                <p>{{ w }}</p>
              }
            </div>
          </div>
        }

        <p class="muted extraction-meta">
          Extraction method: {{ extractionMethod() }} · confidence:
          <strong [class.low-confidence]="confidence() === 'low'">{{ confidence() }}</strong>
          · {{ extractedFieldCount() }} of 20 fields found
        </p>

        @for (group of fieldGroups; track group.label) {
          <h3 class="group-label">{{ group.label }}</h3>
          <div class="field-grid">
            @for (f of group.fields; track f.key) {
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>{{ f.label }}{{ f.unit ? ' (' + f.unit + ')' : '' }}</mat-label>
                <input matInput type="number" step="0.1" [formControl]="fieldControls[f.key]" />
              </mat-form-field>
            }
          </div>
        }
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close(saved())" [disabled]="uploading() || saving()">
        {{ step() === 'review' ? 'Close' : 'Cancel' }}
      </button>
      @if (step() === 'review') {
        <button mat-flat-button color="primary" (click)="saveCorrections()" [disabled]="saving()">
          @if (saving()) {
            <mat-spinner diameter="18" />
          } @else {
            Save
          }
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      min-width: 560px;
      max-width: 640px;
    }
    .full {
      width: 100%;
    }
    .patient-list {
      display: flex;
      flex-direction: column;
      gap: 6px;
      max-height: 320px;
      overflow-y: auto;
      margin-top: 10px;
    }
    .patient-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 12px;
      border: 1px solid var(--border);
      border-radius: 8px;
      background: none;
      cursor: pointer;
      text-align: left;
      font: inherit;
      color: inherit;
    }
    .patient-row:hover {
      background: var(--muted);
    }
    .patient-main {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .patient-name {
      font-weight: 500;
    }
    .patient-meta {
      font-size: 0.8rem;
      color: var(--muted-foreground);
    }
    .selected-patient {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 12px;
      border-radius: 8px;
      background: var(--primary-tint);
      margin-bottom: 12px;
    }
    .dropzone {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 6px;
      min-height: 160px;
      border: 2px dashed var(--border);
      border-radius: 8px;
      cursor: pointer;
      padding: 16px;
      text-align: center;
      transition: border-color 0.15s, background 0.15s;
    }
    .dropzone.dragover {
      border-color: var(--primary);
      background: var(--primary-tint);
    }
    .hint {
      font-size: 0.9rem;
      color: var(--muted-foreground);
    }
    .sub-hint {
      font-size: 0.75rem;
      color: var(--muted-foreground);
    }
    .muted {
      color: var(--muted-foreground);
    }
    .warning-banner {
      display: flex;
      gap: 10px;
      background: var(--badge-amber-bg);
      color: var(--badge-amber-fg);
      border-radius: 8px;
      padding: 10px 12px;
      margin-bottom: 12px;
    }
    .warning-banner p {
      margin: 0;
      font-size: 0.85rem;
    }
    .extraction-meta {
      font-size: 0.8rem;
      margin: 0 0 12px;
    }
    .low-confidence {
      color: var(--badge-amber-fg);
    }
    .group-label {
      margin: 14px 0 6px;
      font-size: 0.85rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--muted-foreground);
    }
    .field-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 4px 16px;
    }
  `,
})
export class UploadReportDialogComponent {
  protected readonly ref = inject(MatDialogRef<UploadReportDialogComponent>);
  private readonly patientsService = inject(PatientsService);
  private readonly reportsService = inject(ReportsService);
  private readonly toast = inject(ToastService);

  protected readonly fieldGroups = INBODY_FIELD_GROUPS;

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly patients = signal<PatientSummary[]>([]);
  protected readonly loadingPatients = signal(false);
  protected readonly selectedPatient = signal<PatientSummary | null>(null);

  protected readonly step = signal<Step>('patient');
  protected readonly dragOver = signal(false);
  protected readonly uploading = signal(false);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);

  private reportId = '';
  protected readonly confidence = signal<'high' | 'low'>('high');
  protected readonly extractedFieldCount = signal(0);
  protected readonly extractionMethod = signal('');
  protected readonly warnings = signal<string[]>([]);

  protected readonly fieldControls: Record<keyof InBodyData, FormControl<number | null>> = Object.fromEntries(
    INBODY_FIELD_GROUPS.flatMap((g) => g.fields).map((f) => [f.key, new FormControl<number | null>(null)]),
  ) as Record<keyof InBodyData, FormControl<number | null>>;

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((term) => this.loadPatients(term));
  }

  protected selectPatient(patient: PatientSummary): void {
    this.selectedPatient.set(patient);
    this.step.set('upload');
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) this.upload(file);
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) this.upload(file);
  }

  protected saveCorrections(): void {
    const parsedData: InBodyData = Object.fromEntries(
      Object.entries(this.fieldControls).map(([key, control]) => [key, control.value]),
    ) as unknown as InBodyData;

    this.saving.set(true);
    this.reportsService.update(this.reportId, { parsedData }).subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.set(true);
        this.toast.success('Report saved');
        this.ref.close(true);
      },
      error: (err: ApiError) => {
        this.saving.set(false);
        this.toast.error(err.error);
      },
    });
  }

  private upload(file: File): void {
    if (file.type !== 'application/pdf') {
      this.toast.error('Only PDF files are allowed');
      return;
    }
    if (file.size > MAX_BYTES) {
      this.toast.error('File must be 10 MB or smaller');
      return;
    }
    this.uploading.set(true);
    this.reportsService.upload(this.selectedPatient()!.id, file).subscribe({
      next: (result) => {
        this.uploading.set(false);
        this.reportId = result.reportId;
        this.confidence.set(result.confidence);
        this.extractedFieldCount.set(result.extractedFieldCount);
        this.extractionMethod.set(result.extractionMethod);
        this.warnings.set(result.warnings ?? []);
        for (const [key, control] of Object.entries(this.fieldControls)) {
          control.setValue((result.parsedData as unknown as Record<string, number | null>)[key]);
        }
        this.saved.set(true);
        this.step.set('review');
      },
      error: (err: ApiError) => {
        this.uploading.set(false);
        this.toast.error(
          err.code === 'OCR_FAILED'
            ? `Could not extract this report: ${err.error}. You can try a clearer scan, or add it as a manual record instead.`
            : err.code === 'RATE_LIMIT_EXCEEDED'
              ? 'Too many uploads — please wait a while before trying again.'
              : err.error,
        );
      },
    });
  }

  private loadPatients(search: string): void {
    if (!search) {
      this.patients.set([]);
      return;
    }
    this.loadingPatients.set(true);
    this.patientsService.list({ search, page: 1, limit: 20 }).subscribe({
      next: (result) => {
        this.patients.set(result.data);
        this.loadingPatients.set(false);
      },
      error: (err: ApiError) => {
        this.loadingPatients.set(false);
        this.toast.error(err.error);
      },
    });
  }
}
