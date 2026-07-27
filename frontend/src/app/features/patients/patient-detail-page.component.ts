import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../core/models/api-response.model';
import { PatientDetail, ageFrom } from '../../core/models/patient.model';
import { ToastService } from '../../core/services/toast.service';
import { openSidePanel } from '../../shared/side-panel';
import { AssignedDoctorsPanelComponent } from '../assignments/assigned-doctors-panel.component';
import { HealthRecordsPanelComponent } from './health-records-panel.component';
import { PatientFormDialogComponent } from './patient-form-dialog.component';
import { PatientProgrammesPanelComponent } from './patient-programmes-panel.component';
import { PatientsService } from './patients.service';

/**
 * Patient detail: profile header + tabs. Programmes and Reports are visible
 * stubs — Reports arrives in its own prompt; Health Records now hosts the
 * full trend-chart/table view via HealthRecordsPanelComponent.
 */
@Component({
  selector: 'app-patient-detail-page',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatCardModule,
    MatTabsModule,
    MatIconModule,
    MatButtonModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    AssignedDoctorsPanelComponent,
    PatientProgrammesPanelComponent,
    HealthRecordsPanelComponent,
  ],
  template: `
    @if (loading()) {
      <div class="center"><mat-spinner diameter="40" /></div>
    }
    @if (patient(); as p) {
      <a mat-button routerLink="/patients">
        <mat-icon>arrow_back</mat-icon>
        Patients
      </a>

      <mat-card appearance="outlined" class="header-card">
        <div class="avatar">
          <mat-icon>account_circle</mat-icon>
        </div>
        <div class="header-info">
          <h1>
            {{ p.name }}
            <span class="status-badge" [class.inactive]="!p.isActive">
              {{ p.isActive ? 'Active' : 'Inactive' }}
            </span>
          </h1>
          <div class="header-meta">
            <span>{{ p.email }}</span>
            @if (p.phone) {
              <span>· {{ p.phone }}</span>
            }
            @if (age(p) !== null) {
              <span>· {{ age(p) }} years</span>
            }
            @if (p.assignedDoctors.length) {
              <span>· Doctor: {{ doctorNames(p) }}</span>
            }
          </div>
        </div>
        <button mat-stroked-button (click)="openEdit()">
          <mat-icon>edit</mat-icon>
          Edit
        </button>
      </mat-card>

      <mat-tab-group class="detail-tabs">
        <mat-tab label="Overview">
          <div class="tab-body">
            <div class="overview-grid">
              <mat-card appearance="outlined" class="stat-card">
                <span class="stat-value">{{ latest()?.weightKg ?? '—' }}</span>
                <span class="stat-label">Weight (kg)</span>
              </mat-card>
              <mat-card appearance="outlined" class="stat-card">
                <span class="stat-value">
                  {{ latest()?.bmi != null ? (latest()!.bmi | number: '1.1-1') : '—' }}
                </span>
                <span class="stat-label">BMI</span>
              </mat-card>
              <mat-card appearance="outlined" class="stat-card">
                <span class="stat-value">
                  {{ latest()?.bodyFatPct != null ? latest()!.bodyFatPct + '%' : '—' }}
                </span>
                <span class="stat-label">Body fat</span>
              </mat-card>
              <mat-card appearance="outlined" class="stat-card">
                <span class="stat-value">{{ p.heightCm ?? '—' }}</span>
                <span class="stat-label">Height (cm)</span>
              </mat-card>
            </div>

            <app-assigned-doctors-panel
              [patientId]="p.id"
              [fallbackDoctors]="p.assignedDoctors"
              (changed)="reload()"
            />

            <mat-card appearance="outlined" class="info-card">
              <mat-card-header>
                <mat-card-title>Medical info</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <dl class="info-grid">
                  <div><dt>Date of birth</dt><dd>{{ p.dateOfBirth ? (p.dateOfBirth | date: 'mediumDate') : '—' }}</dd></div>
                  <div><dt>Gender</dt><dd>{{ p.gender ?? '—' }}</dd></div>
                  <div><dt>Blood group</dt><dd>{{ p.bloodGroup ?? '—' }}</dd></div>
                  <div><dt>Emergency contact</dt><dd>{{ p.emergencyContact ?? '—' }}</dd></div>
                  <div class="span-2"><dt>Medical history</dt><dd>{{ p.medicalHistory || '—' }}</dd></div>
                </dl>
              </mat-card-content>
            </mat-card>
          </div>
        </mat-tab>

        <mat-tab label="Programmes">
          <div class="tab-body">
            <app-patient-programmes-panel [patientId]="p.id" [patientName]="p.name" />
          </div>
        </mat-tab>

        <mat-tab label="Reports">
          <div class="tab-body">
            <mat-card appearance="outlined">
              <mat-card-content>
                <p class="stub">Report list and upload arrive with the reports feature prompt.</p>
              </mat-card-content>
            </mat-card>
          </div>
        </mat-tab>

        <mat-tab label="Health Records">
          <div class="tab-body">
            <app-health-records-panel [patientId]="p.id" [patientName]="p.name" />
          </div>
        </mat-tab>
      </mat-tab-group>
    }
  `,
  styles: `
    .center {
      display: grid;
      place-content: center;
      padding: 64px;
    }
    .header-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
      margin: 8px 0 16px;
    }
    .avatar mat-icon {
      font-size: 56px;
      width: 56px;
      height: 56px;
      color: var(--muted-foreground);
    }
    .header-info {
      flex: 1;
    }
    .header-info h1 {
      margin: 0 0 4px;
      font-size: 1.35rem;
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .header-meta {
      color: var(--muted-foreground);
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
    .status-badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.75rem;
      font-weight: 500;
      background: var(--badge-green-bg);
      color: var(--badge-green-fg);
    }
    .status-badge.inactive {
      background: var(--badge-red-bg);
      color: var(--badge-red-fg);
    }
    .tab-body {
      padding: 16px 0;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .overview-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 16px;
    }
    .stat-card {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .stat-value {
      font-size: 1.6rem;
      font-weight: 600;
    }
    .stat-label {
      color: var(--muted-foreground);
      font-size: 0.85rem;
    }
    .info-grid {
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin: 0;
    }
    .info-grid dt {
      font-size: 0.8rem;
      color: var(--muted-foreground);
    }
    .info-grid dd {
      margin: 2px 0 0;
    }
    .span-2 {
      grid-column: span 2;
    }
    .stub {
      color: var(--muted-foreground);
      margin: 8px 0;
    }
    table {
      width: 100%;
    }
  `,
})
export class PatientDetailPageComponent implements OnInit {
  /** Bound from the :id route param via withComponentInputBinding. */
  readonly id = input.required<string>();

  private readonly patientsService = inject(PatientsService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  protected readonly patient = signal<PatientDetail | null>(null);
  protected readonly loading = signal(true);

  ngOnInit(): void {
    this.load(this.id());
  }

  /** Re-fetch after assignment changes so the header doctor list stays fresh. */
  protected reload(): void {
    this.load(this.id());
  }

  protected age = (p: PatientDetail) => ageFrom(p.dateOfBirth);

  protected doctorNames(p: PatientDetail): string {
    return p.assignedDoctors.map((d) => d.name).join(', ');
  }

  protected latest() {
    return this.patient()?.healthRecords[0] ?? null;
  }

  protected openEdit(): void {
    const current = this.patient();
    if (!current) return;
    openSidePanel(this.dialog, PatientFormDialogComponent, { data: { patient: current } })
      .afterClosed()
      .subscribe((updated) => updated && this.load(current.id));
  }

  private load(id: string): void {
    this.loading.set(true);
    this.patientsService.get(id).subscribe({
      next: (patient) => {
        this.patient.set(patient);
        this.loading.set(false);
      },
      error: (err: ApiError) => {
        this.loading.set(false);
        this.toast.error(err.error);
      },
    });
  }
}
