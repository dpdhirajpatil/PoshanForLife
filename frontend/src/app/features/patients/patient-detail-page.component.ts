import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../core/models/api-response.model';
import { PatientDetail, ageFrom } from '../../core/models/patient.model';
import { ToastService } from '../../core/services/toast.service';
import { PatientFormDialogComponent } from './patient-form-dialog.component';
import { PatientsService } from './patients.service';

/**
 * Patient detail: profile header + tabs. Programmes and Reports are visible
 * stubs — those features arrive in their own prompts; Health Records shows
 * the raw table (trend charts come with the health-records prompt).
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
    MatTableModule,
    MatDialogModule,
    MatProgressSpinnerModule,
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
            <mat-card appearance="outlined">
              <mat-card-content>
                <p class="stub">Programme assignments arrive with the programmes feature (prompt 06).</p>
              </mat-card-content>
            </mat-card>
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
            @if (p.healthRecords.length === 0) {
              <mat-card appearance="outlined">
                <mat-card-content>
                  <p class="stub">No health records yet. Trend charts arrive with the health-records feature prompt.</p>
                </mat-card-content>
              </mat-card>
            } @else {
              <mat-card appearance="outlined">
                <table mat-table [dataSource]="p.healthRecords">
                  <ng-container matColumnDef="recordedAt">
                    <th mat-header-cell *matHeaderCellDef>Recorded</th>
                    <td mat-cell *matCellDef="let r">{{ r.recordedAt | date: 'medium' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="weightKg">
                    <th mat-header-cell *matHeaderCellDef>Weight (kg)</th>
                    <td mat-cell *matCellDef="let r">{{ r.weightKg ?? '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="bmi">
                    <th mat-header-cell *matHeaderCellDef>BMI</th>
                    <td mat-cell *matCellDef="let r">
                      {{ r.bmi != null ? (r.bmi | number: '1.1-1') : '—' }}
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="bodyFatPct">
                    <th mat-header-cell *matHeaderCellDef>Body fat %</th>
                    <td mat-cell *matCellDef="let r">{{ r.bodyFatPct ?? '—' }}</td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="recordColumns"></tr>
                  <tr mat-row *matRowDef="let row; columns: recordColumns"></tr>
                </table>
              </mat-card>
            }
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
      color: rgba(0, 0, 0, 0.35);
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
      color: rgba(0, 0, 0, 0.6);
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
    .status-badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.75rem;
      font-weight: 500;
      background: #e8f5e9;
      color: #2e7d32;
    }
    .status-badge.inactive {
      background: #fbe9e7;
      color: #c62828;
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
      color: rgba(0, 0, 0, 0.6);
      font-size: 0.85rem;
    }
    .info-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px 24px;
      margin: 0;
    }
    .info-grid dt {
      font-size: 0.8rem;
      color: rgba(0, 0, 0, 0.6);
    }
    .info-grid dd {
      margin: 2px 0 0;
    }
    .span-2 {
      grid-column: span 2;
    }
    .stub {
      color: rgba(0, 0, 0, 0.6);
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
  protected readonly recordColumns = ['recordedAt', 'weightKg', 'bmi', 'bodyFatPct'];

  ngOnInit(): void {
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
    this.dialog
      .open(PatientFormDialogComponent, { data: { patient: current } })
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
