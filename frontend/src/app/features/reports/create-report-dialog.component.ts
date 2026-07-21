import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import { PatientSummary } from '../../core/models/patient.model';
import { ReportType } from '../../core/models/report.model';
import { ToastService } from '../../core/services/toast.service';
import { PatientsService } from '../patients/patients.service';
import { ReportsService } from './reports.service';

const MANUAL_TYPES: ReportType[] = ['lab', 'prescription', 'other'];

/** Manual, file-less report record — for LAB/PRESCRIPTION/OTHER types. */
@Component({
  selector: 'app-create-report-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Create report</h2>

    <mat-dialog-content>
      @if (!selectedPatient()) {
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
            <button type="button" class="patient-row" (click)="selectedPatient.set(p)">
              <div class="patient-main">
                <span class="patient-name">{{ p.name }}</span>
                <span class="patient-meta">{{ p.email }}</span>
              </div>
            </button>
          } @empty {
            @if (!loadingPatients() && search.value) {
              <p class="muted">No patients match "{{ search.value }}".</p>
            }
          }
        </div>
      } @else {
        <div class="selected-patient">
          <div><strong>{{ selectedPatient()!.name }}</strong> <span class="muted">· {{ selectedPatient()!.email }}</span></div>
          <button mat-button type="button" (click)="selectedPatient.set(null)">Change</button>
        </div>

        <form [formGroup]="form" class="grid">
          <mat-form-field appearance="outline">
            <mat-label>Type</mat-label>
            <mat-select formControlName="type">
              @for (t of types; track t) {
                <mat-option [value]="t">{{ t }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Title</mat-label>
            <input matInput formControlName="title" />
            @if (form.controls.title.invalid && form.controls.title.touched) {
              <mat-error>Title is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Notes (optional)</mat-label>
            <textarea matInput formControlName="notes" rows="3"></textarea>
          </mat-form-field>
        </form>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()" [disabled]="saving()">Cancel</button>
      @if (selectedPatient()) {
        <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
          @if (saving()) {
            <mat-spinner diameter="18" />
          } @else {
            Create report
          }
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      min-width: 480px;
    }
    .full {
      width: 100%;
    }
    .patient-list {
      display: flex;
      flex-direction: column;
      gap: 6px;
      max-height: 280px;
      overflow-y: auto;
      margin-top: 10px;
    }
    .patient-row {
      display: flex;
      align-items: center;
      padding: 10px 12px;
      border: 1px solid rgba(0, 0, 0, 0.15);
      border-radius: 8px;
      background: none;
      cursor: pointer;
      text-align: left;
      font: inherit;
      color: inherit;
    }
    .patient-row:hover {
      background: rgba(0, 0, 0, 0.03);
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
      color: rgba(0, 0, 0, 0.6);
    }
    .selected-patient {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 12px;
      border-radius: 8px;
      background: rgba(45, 138, 104, 0.07);
      margin-bottom: 12px;
    }
    .muted {
      color: rgba(0, 0, 0, 0.55);
    }
    .grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px 16px;
    }
    .span-2 {
      grid-column: span 2;
    }
  `,
})
export class CreateReportDialogComponent {
  protected readonly ref = inject(MatDialogRef<CreateReportDialogComponent>);
  private readonly patientsService = inject(PatientsService);
  private readonly reportsService = inject(ReportsService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly types = MANUAL_TYPES;
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly patients = signal<PatientSummary[]>([]);
  protected readonly loadingPatients = signal(false);
  protected readonly selectedPatient = signal<PatientSummary | null>(null);
  protected readonly saving = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    type: ['lab' as ReportType, Validators.required],
    title: ['', [Validators.required, Validators.maxLength(255)]],
    notes: ['', Validators.maxLength(5000)],
  });

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((term) => this.loadPatients(term));
  }

  protected save(): void {
    const patient = this.selectedPatient();
    if (!patient || this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.reportsService
      .create({ patientId: patient.id, title: v.title, type: v.type, notes: v.notes || undefined })
      .subscribe({
        next: (report) => {
          this.toast.success('Report created');
          this.ref.close(report);
        },
        error: (err: ApiError) => {
          this.saving.set(false);
          this.toast.error(err.error);
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
