import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ChartConfiguration, ChartData, TooltipItem } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { ApiError } from '../../core/models/api-response.model';
import { HealthRecordEntry } from '../../core/models/patient.model';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { openSidePanel } from '../../shared/side-panel';
import { HealthRecordsService } from './health-records.service';
import { LogMeasurementDialogComponent, LogMeasurementDialogData } from './log-measurement-dialog.component';

type ViewMode = 'chart' | 'table';

interface TrendConfig {
  key: 'weightKg' | 'bodyFatPct' | 'bmi' | 'skeletalMuscleMassKg';
  deltaKey: 'weightKgDelta' | 'bodyFatPctDelta' | 'bmiDelta' | 'skeletalMuscleMassKgDelta';
  label: string;
  unit: string;
  color: string;
}

function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

/**
 * The patient detail page's "Health trends" section: latest-vitals summary,
 * chart/table toggle, manual quick-entry (ADMIN/DOCTOR), and delete
 * (ADMIN only). Records come back oldest-first from the backend (deltas are
 * computed server-side against each record's immediate predecessor).
 */
@Component({
  selector: 'app-health-records-panel',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    MatCardModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatTableModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatDialogModule,
    BaseChartDirective,
  ],
  template: `
    <div class="panel-header">
      <h3>Health trends</h3>
      <div class="header-actions">
        <mat-button-toggle-group [value]="viewMode()" (change)="viewMode.set($event.value)">
          <mat-button-toggle value="chart"><mat-icon>show_chart</mat-icon></mat-button-toggle>
          <mat-button-toggle value="table"><mat-icon>table_rows</mat-icon></mat-button-toggle>
        </mat-button-toggle-group>
        @if (canManage) {
          <button mat-flat-button color="primary" (click)="openLogMeasurement()">
            <mat-icon>add</mat-icon>
            Log a measurement
          </button>
        }
      </div>
    </div>

    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (!loading() && records().length === 0) {
      <mat-card appearance="outlined">
        <mat-card-content>
          <p class="stub">No health records yet.</p>
        </mat-card-content>
      </mat-card>
    } @else {
      @if (latest(); as l) {
        <div class="vitals-grid">
          <mat-card appearance="outlined" class="vital-card">
            <span class="vital-value">{{ l.weightKg ?? '—' }}</span>
            <span class="vital-label">Weight (kg)</span>
          </mat-card>
          <mat-card appearance="outlined" class="vital-card">
            <span class="vital-value">{{ l.bmi != null ? (l.bmi | number: '1.1-1') : '—' }}</span>
            <span class="vital-label">BMI</span>
          </mat-card>
          <mat-card appearance="outlined" class="vital-card">
            <span class="vital-value">{{ l.bodyFatPct != null ? l.bodyFatPct + '%' : '—' }}</span>
            <span class="vital-label">Body fat</span>
          </mat-card>
          <mat-card appearance="outlined" class="vital-card">
            <span class="vital-value">{{ l.skeletalMuscleMassKg ?? '—' }}</span>
            <span class="vital-label">Muscle mass (kg)</span>
          </mat-card>
          <span class="vital-asof">as of {{ l.recordDate | date: 'mediumDate' }}</span>
        </div>
      }

      @if (viewMode() === 'chart') {
        <div class="chart-grid">
          @for (trend of trends; track trend.key) {
            <mat-card appearance="outlined" class="chart-card">
              <h4>{{ trend.label }}</h4>
              <div class="chart-box">
                <canvas baseChart [type]="'line'" [data]="chartData(trend)" [options]="chartOptions(trend)"></canvas>
              </div>
            </mat-card>
          }
        </div>
      } @else {
        <mat-card appearance="outlined">
          <div class="table-scroll">
            <table mat-table [dataSource]="tableRows()">
              <ng-container matColumnDef="recordDate">
                <th mat-header-cell *matHeaderCellDef>Date</th>
                <td mat-cell *matCellDef="let r">{{ r.recordDate | date: 'mediumDate' }}</td>
              </ng-container>
              <ng-container matColumnDef="weightKg">
                <th mat-header-cell *matHeaderCellDef>Weight (kg)</th>
                <td mat-cell *matCellDef="let r">{{ cellWithDelta(r.weightKg, r.weightKgDelta) }}</td>
              </ng-container>
              <ng-container matColumnDef="bmi">
                <th mat-header-cell *matHeaderCellDef>BMI</th>
                <td mat-cell *matCellDef="let r">{{ cellWithDelta(r.bmi, r.bmiDelta) }}</td>
              </ng-container>
              <ng-container matColumnDef="bodyFatPct">
                <th mat-header-cell *matHeaderCellDef>Body fat %</th>
                <td mat-cell *matCellDef="let r">{{ cellWithDelta(r.bodyFatPct, r.bodyFatPctDelta) }}</td>
              </ng-container>
              <ng-container matColumnDef="skeletalMuscleMassKg">
                <th mat-header-cell *matHeaderCellDef>Muscle mass (kg)</th>
                <td mat-cell *matCellDef="let r">{{ cellWithDelta(r.skeletalMuscleMassKg, r.skeletalMuscleMassKgDelta) }}</td>
              </ng-container>
              <ng-container matColumnDef="source">
                <th mat-header-cell *matHeaderCellDef>Source</th>
                <td mat-cell *matCellDef="let r"><span class="source-badge">{{ sourceLabel(r.source) }}</span></td>
              </ng-container>
              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef></th>
                <td mat-cell *matCellDef="let r">
                  @if (isAdmin) {
                    <button mat-icon-button (click)="confirmDelete(r)" aria-label="Delete record" matTooltip="Delete">
                      <mat-icon>delete_outline</mat-icon>
                    </button>
                  }
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="tableColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: tableColumns"></tr>
            </table>
          </div>
        </mat-card>
      }
    }
  `,
  styles: `
    .panel-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 12px;
    }
    .panel-header h3 {
      margin: 0;
      font-size: 1.05rem;
    }
    .header-actions {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .stub {
      color: var(--muted-foreground);
      margin: 8px 0;
    }
    .vitals-grid {
      position: relative;
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
      gap: 12px;
    }
    .vital-card {
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .vital-value {
      font-size: 1.4rem;
      font-weight: 600;
    }
    .vital-label {
      color: var(--muted-foreground);
      font-size: 0.82rem;
    }
    .vital-asof {
      grid-column: 1 / -1;
      color: var(--muted-foreground);
      font-size: 0.78rem;
    }
    .chart-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 16px;
    }
    .chart-card {
      padding: 12px 16px 16px;
    }
    .chart-card h4 {
      margin: 0 0 8px;
      font-size: 0.92rem;
      color: var(--muted-foreground);
    }
    .chart-box {
      position: relative;
      height: 220px;
    }
    table {
      width: 100%;
    }
    .source-badge {
      font-size: 0.78rem;
      color: var(--muted-foreground);
      text-transform: capitalize;
    }
  `,
})
export class HealthRecordsPanelComponent implements OnInit {
  readonly patientId = input.required<string>();
  readonly patientName = input.required<string>();

  private readonly healthRecordsService = inject(HealthRecordsService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);

  protected readonly isAdmin = this.auth.isAdmin();
  protected readonly canManage = this.auth.hasAnyRole('ADMIN', 'DOCTOR');

  protected readonly records = signal<HealthRecordEntry[]>([]); // oldest-first, as returned by the backend
  protected readonly loading = signal(false);
  protected readonly viewMode = signal<ViewMode>('chart');
  protected readonly tableColumns = ['recordDate', 'weightKg', 'bmi', 'bodyFatPct', 'skeletalMuscleMassKg', 'source', 'actions'];

  protected readonly trends: TrendConfig[] = [
    { key: 'weightKg', deltaKey: 'weightKgDelta', label: 'Weight (kg)', unit: 'kg', color: '--chart-1' },
    { key: 'bodyFatPct', deltaKey: 'bodyFatPctDelta', label: 'Body fat %', unit: '%', color: '--chart-2' },
    { key: 'bmi', deltaKey: 'bmiDelta', label: 'BMI', unit: '', color: '--chart-3' },
    { key: 'skeletalMuscleMassKg', deltaKey: 'skeletalMuscleMassKgDelta', label: 'Skeletal muscle mass (kg)', unit: 'kg', color: '--chart-4' },
  ];

  protected readonly latest = computed<HealthRecordEntry | null>(() => {
    const r = this.records();
    return r.length ? r[r.length - 1] : null;
  });

  /** Table view reads more naturally newest-first. */
  protected readonly tableRows = computed(() => [...this.records()].reverse());

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.healthRecordsService.list(this.patientId()).subscribe({
      next: (records) => {
        this.records.set(records);
        this.loading.set(false);
      },
      error: (err: ApiError) => {
        this.loading.set(false);
        this.toast.error(err.error);
      },
    });
  }

  protected cellWithDelta(value: number | null, delta: number | null): string {
    if (value == null) return '—';
    if (delta == null || delta === 0) return `${value}`;
    const sign = delta > 0 ? '+' : '';
    return `${value} (${sign}${delta.toFixed(1)})`;
  }

  protected sourceLabel(source: HealthRecordEntry['source']): string {
    return source.replace('_', ' ');
  }

  protected chartData(trend: TrendConfig): ChartData<'line'> {
    const records = this.records();
    const color = cssVar(trend.color);
    return {
      labels: records.map((r) => r.recordDate),
      datasets: [
        {
          label: trend.label,
          data: records.map((r) => r[trend.key]),
          borderColor: color,
          backgroundColor: color,
          tension: 0.3,
          spanGaps: true,
          fill: false,
        },
      ],
    };
  }

  protected chartOptions(trend: TrendConfig): ChartConfiguration<'line'>['options'] {
    const records = this.records();
    const muted = cssVar('--muted-foreground');
    const gridColor = cssVar('--border');
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: (item: TooltipItem<'line'>) => {
              const record = records[item.dataIndex];
              const value = record?.[trend.key];
              const delta = record?.[trend.deltaKey];
              if (value == null) return 'No data';
              if (delta == null) return `${value}${trend.unit}`;
              const sign = delta > 0 ? '+' : '';
              return `${value}${trend.unit} (${sign}${delta.toFixed(1)} vs previous)`;
            },
          },
        },
      },
      scales: {
        x: { ticks: { color: muted }, grid: { color: gridColor } },
        y: { ticks: { color: muted }, grid: { color: gridColor } },
      },
    };
  }

  protected openLogMeasurement(): void {
    openSidePanel(this.dialog, LogMeasurementDialogComponent, {
      data: { patientId: this.patientId() } satisfies LogMeasurementDialogData,
    })
      .afterClosed()
      .subscribe((saved) => saved && this.load());
  }

  protected confirmDelete(record: HealthRecordEntry): void {
    const data: ConfirmDialogData = {
      title: 'Delete record',
      message: `The health record for ${this.patientName()} on ${record.recordDate} will be permanently removed.`,
      confirmLabel: 'Delete',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.healthRecordsService.remove(this.patientId(), record.id).subscribe({
          next: () => {
            this.toast.success('Record deleted');
            this.load();
          },
          error: (err: ApiError) => this.toast.error(err.error),
        });
      });
  }
}
