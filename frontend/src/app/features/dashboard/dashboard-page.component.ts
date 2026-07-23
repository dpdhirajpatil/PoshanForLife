import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import {
  AttentionPatient,
  DashboardActivity,
  DashboardActivityType,
  DashboardStats,
} from '../../core/models/dashboard.model';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';
import { ToastService } from '../../core/services/toast.service';
import { CreateLeadDialogComponent } from '../leads/create-lead-dialog.component';
import { openSidePanel } from '../../shared/side-panel';
import { DashboardService } from './dashboard.service';

const ACTIVITY_ICONS: Record<DashboardActivityType, string> = {
  patient_created: 'person_add',
  report_created: 'description',
  lead_stage_change: 'swap_horiz',
};

function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

/**
 * The default landing page after login. One aggregation call
 * (GET /dashboard/stats) drives everything below; ADMIN and DOCTOR callers
 * get different scoping (org-wide vs. own patients/leads) and different
 * role-conditional sections (assignment overview vs. attention patients) —
 * see DashboardService on the backend for exactly what's scoped how.
 */
@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatIconModule,
    BaseChartDirective,
  ],
  template: `
    <div class="page-header">
      <h1>Dashboard</h1>
      @if (!isDoctor()) {
        <button mat-flat-button color="primary" (click)="openCreateLead()">
          <mat-icon>person_add</mat-icon>
          Add lead
        </button>
      }
    </div>

    @if (loading()) {
      <div class="kpi-row">
        @for (i of skeletonKpis; track i) {
          <mat-card appearance="outlined" class="stat-card skeleton"></mat-card>
        }
      </div>
      <div class="charts-row">
        @for (i of skeletonCharts; track i) {
          <mat-card appearance="outlined" class="chart-card skeleton"></mat-card>
        }
      </div>
    } @else if (stats() === null) {
      <mat-card appearance="outlined">
        <p class="stub">Couldn't load the dashboard. Try refreshing the page.</p>
      </mat-card>
    } @else {
      @let s = stats()!;
      <div class="kpi-row">
        <mat-card appearance="outlined" class="stat-card">
          <span class="stat-value">{{ s.kpis.totalPatients }}</span>
          <span class="stat-label">Total patients</span>
        </mat-card>
        <mat-card appearance="outlined" class="stat-card">
          <span class="stat-value">{{ s.kpis.activeDoctors }}</span>
          <span class="stat-label">Active doctors</span>
        </mat-card>
        <mat-card appearance="outlined" class="stat-card">
          <span class="stat-value">{{ s.kpis.reportsThisMonth }}</span>
          <span class="stat-label">Reports this month</span>
        </mat-card>
        <mat-card appearance="outlined" class="stat-card">
          <span class="stat-value">{{ s.kpis.avgInBodyScore ?? '—' }}</span>
          <span class="stat-label">Avg. BMI</span>
        </mat-card>
        @if (isDoctor()) {
          <mat-card appearance="outlined" class="stat-card">
            <span class="stat-value">{{ s.kpis.myPatients ?? 0 }}</span>
            <span class="stat-label">My patients</span>
          </mat-card>
        }
        <mat-card appearance="outlined" class="stat-card">
          <span class="stat-value">{{ s.kpis.pendingReviews }}</span>
          <span class="stat-label">Pending reviews</span>
        </mat-card>
        <mat-card appearance="outlined" class="stat-card followup-stat">
          <span class="stat-value">{{ s.kpis.followupToday }}</span>
          <span class="stat-label">Follow-ups today</span>
        </mat-card>
      </div>

      <div class="charts-row">
        <mat-card appearance="outlined" class="chart-card">
          <h2>Body fat % — 6-month trend</h2>
          @if (hasTrendData()) {
            <div class="chart-wrap">
              <canvas baseChart [type]="'line'" [data]="pbfChartData()" [options]="lineOptions()"></canvas>
            </div>
          } @else {
            <p class="stub">No health records yet — the trend will appear once patients have data.</p>
          }
        </mat-card>

        <mat-card appearance="outlined" class="chart-card">
          <h2>BMI distribution</h2>
          @if (hasBmiData()) {
            <div class="chart-wrap">
              <canvas baseChart [type]="'bar'" [data]="bmiChartData()" [options]="barOptions()"></canvas>
            </div>
          } @else {
            <p class="stub">No BMI data yet — patients need a height and a health record.</p>
          }
        </mat-card>

        <mat-card appearance="outlined" class="chart-card">
          <h2>Body composition</h2>
          @if (hasScatterData()) {
            <div class="chart-wrap">
              <canvas baseChart [type]="'scatter'" [data]="scatterChartData()" [options]="scatterOptions()"></canvas>
            </div>
          } @else {
            <p class="stub">No body-composition records yet.</p>
          }
        </mat-card>
      </div>

      <div class="bottom-row">
        <mat-card appearance="outlined" class="activity-card">
          <h2>Recent activity</h2>
          @if (s.recentActivity.length === 0) {
            <p class="stub">Nothing to show yet.</p>
          } @else {
            <ul class="activity-list">
              @for (item of s.recentActivity; track item.occurredAt + item.message) {
                <li>
                  <mat-icon [class]="'activity-icon ' + item.type">{{ activityIcon(item) }}</mat-icon>
                  <div class="activity-body">
                    <span class="activity-message">{{ item.message }}</span>
                    <span class="activity-time">{{ item.occurredAt | date: 'medium' }}</span>
                  </div>
                </li>
              }
            </ul>
          }
        </mat-card>

        @if (isDoctor()) {
          <mat-card appearance="outlined" class="side-card">
            <h2>Needs attention</h2>
            @if (!s.attentionPatients || s.attentionPatients.length === 0) {
              <p class="stub">All caught up — no patients need attention right now.</p>
            } @else {
              <ul class="attention-list">
                @for (patient of s.attentionPatients; track patient.patientId) {
                  <li>
                    <a [routerLink]="['/patients', patient.patientId]">{{ patient.patientName }}</a>
                    <span class="attention-reason">{{ attentionLabel(patient) }}</span>
                  </li>
                }
              </ul>
            }
          </mat-card>
        } @else {
          <mat-card appearance="outlined" class="side-card">
            <h2>Assignment overview</h2>
            @if (!s.assignmentOverview || s.assignmentOverview.length === 0) {
              <p class="stub">No doctors yet.</p>
            } @else {
              <ul class="assignment-list">
                @for (row of s.assignmentOverview; track row.doctorId) {
                  <li>
                    <span class="doctor-name">{{ row.doctorName }}</span>
                    <span class="doctor-count">{{ row.patientCount }} patient{{ row.patientCount === 1 ? '' : 's' }}</span>
                  </li>
                }
              </ul>
            }
          </mat-card>
        }
      </div>
    }
  `,
  styles: `
    .page-header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 16px;
    }
    .page-header h1 {
      margin: 0;
      font-size: 1.5rem;
    }
    .kpi-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 16px;
      margin-bottom: 16px;
    }
    .stat-card {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-height: 72px;
    }
    .followup-stat .stat-value {
      color: var(--badge-amber-fg);
    }
    .stat-value {
      font-size: 1.7rem;
      font-weight: 600;
    }
    .stat-label {
      color: var(--muted-foreground);
      font-size: 0.85rem;
    }
    .charts-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 16px;
      margin-bottom: 16px;
    }
    .chart-card {
      padding: 16px;
      min-height: 280px;
      display: flex;
      flex-direction: column;
    }
    .chart-card h2 {
      margin: 0 0 12px;
      font-size: 1rem;
      font-weight: 600;
    }
    .chart-wrap {
      position: relative;
      flex: 1;
      min-height: 200px;
    }
    .bottom-row {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 16px;
    }
    @media (max-width: 900px) {
      .bottom-row {
        grid-template-columns: 1fr;
      }
    }
    .activity-card,
    .side-card {
      padding: 16px;
    }
    .activity-card h2,
    .side-card h2 {
      margin: 0 0 12px;
      font-size: 1rem;
      font-weight: 600;
    }
    .activity-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 12px;
      max-height: 420px;
      overflow-y: auto;
    }
    .activity-list li {
      display: flex;
      align-items: flex-start;
      gap: 10px;
    }
    .activity-icon {
      flex-shrink: 0;
      color: var(--muted-foreground);
      background: var(--muted);
      border-radius: 50%;
      padding: 6px;
      box-sizing: content-box;
      font-size: 18px;
      width: 18px;
      height: 18px;
    }
    .activity-icon.patient_created {
      color: var(--badge-green-fg);
      background: var(--badge-green-bg);
    }
    .activity-icon.report_created {
      color: var(--badge-blue-fg);
      background: var(--badge-blue-bg);
    }
    .activity-icon.lead_stage_change {
      color: var(--badge-purple-fg);
      background: var(--badge-purple-bg);
    }
    .activity-body {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .activity-message {
      font-size: 0.9rem;
    }
    .activity-time {
      font-size: 0.75rem;
      color: var(--muted-foreground);
    }
    .attention-list,
    .assignment-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .attention-list li {
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding-bottom: 8px;
      border-bottom: 1px solid var(--border);
    }
    .attention-list li:last-child {
      border-bottom: none;
      padding-bottom: 0;
    }
    .attention-list a {
      color: var(--primary);
      font-weight: 500;
      text-decoration: none;
    }
    .attention-list a:hover {
      text-decoration: underline;
    }
    .attention-reason {
      font-size: 0.8rem;
      color: var(--badge-red-fg);
    }
    .assignment-list li {
      display: flex;
      justify-content: space-between;
      padding-bottom: 8px;
      border-bottom: 1px solid var(--border);
    }
    .assignment-list li:last-child {
      border-bottom: none;
      padding-bottom: 0;
    }
    .doctor-count {
      color: var(--muted-foreground);
      font-size: 0.85rem;
    }
    .stub {
      color: var(--muted-foreground);
      margin: 8px 0;
    }
    .skeleton {
      position: relative;
      overflow: hidden;
      background: var(--muted);
    }
    .skeleton::after {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(90deg, transparent, rgb(255 255 255 / 0.25), transparent);
      animation: shimmer 1.4s infinite;
    }
    @keyframes shimmer {
      0% {
        transform: translateX(-100%);
      }
      100% {
        transform: translateX(100%);
      }
    }
  `,
})
export class DashboardPageComponent {
  private readonly dashboardService = inject(DashboardService);
  private readonly authService = inject(AuthService);
  private readonly themeService = inject(ThemeService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(MatDialog);

  protected readonly skeletonKpis = [1, 2, 3, 4, 5, 6];
  protected readonly skeletonCharts = [1, 2, 3];

  protected readonly loading = signal(true);
  protected readonly stats = signal<DashboardStats | null>(null);
  protected readonly isDoctor = computed(() => this.authService.currentUser()?.role === 'DOCTOR');

  protected readonly hasTrendData = computed(() =>
    (this.stats()?.pbfSixMonthTrend ?? []).some((p) => p.avgBodyFatPct !== null),
  );
  protected readonly hasBmiData = computed(() =>
    (this.stats()?.bmiDistribution ?? []).some((b) => b.count > 0),
  );
  protected readonly hasScatterData = computed(
    () => (this.stats()?.bodyCompositionScatter ?? []).length > 0,
  );

  protected readonly pbfChartData = computed<ChartData<'line'>>(() => {
    const trend = this.stats()?.pbfSixMonthTrend ?? [];
    return {
      labels: trend.map((p) => p.month),
      datasets: [
        {
          label: 'Avg. body fat %',
          data: trend.map((p) => p.avgBodyFatPct),
          borderColor: cssVar('--chart-1'),
          backgroundColor: cssVar('--chart-1'),
          tension: 0.3,
          spanGaps: true,
        },
      ],
    };
  });

  protected readonly bmiChartData = computed<ChartData<'bar'>>(() => {
    const buckets = this.stats()?.bmiDistribution ?? [];
    const colors: Record<string, string> = {
      Underweight: cssVar('--badge-blue-fg'),
      Normal: cssVar('--badge-green-fg'),
      Overweight: cssVar('--badge-amber-fg'),
      Obese: cssVar('--badge-red-fg'),
    };
    return {
      labels: buckets.map((b) => b.label),
      datasets: [
        {
          label: 'Patients',
          data: buckets.map((b) => b.count),
          backgroundColor: buckets.map((b) => colors[b.label]),
        },
      ],
    };
  });

  protected readonly scatterChartData = computed<ChartData<'scatter'>>(() => {
    const points = this.stats()?.bodyCompositionScatter ?? [];
    return {
      datasets: [
        {
          label: 'Body fat % vs. skeletal muscle mass (kg)',
          data: points.map((p) => ({ x: p.x, y: p.y })),
          backgroundColor: cssVar('--chart-2'),
        },
      ],
    };
  });

  protected readonly lineOptions = signal<ChartConfiguration<'line'>['options']>({});
  protected readonly barOptions = signal<ChartConfiguration<'bar'>['options']>({});
  protected readonly scatterOptions = signal<ChartConfiguration<'scatter'>['options']>({});

  constructor() {
    this.load();
    effect(
      () => {
        this.themeService.theme();
        this.recomputeChartOptions();
      },
      { allowSignalWrites: true },
    );
  }

  protected activityIcon(item: DashboardActivity): string {
    return ACTIVITY_ICONS[item.type];
  }

  protected openCreateLead(): void {
    openSidePanel(this.dialog, CreateLeadDialogComponent)
      .afterClosed()
      .subscribe((created) => created && this.load());
  }

  protected attentionLabel(patient: AttentionPatient): string {
    return patient.reason === 'never_recorded'
      ? 'No health record yet'
      : 'No record in 30+ days';
  }

  private load(): void {
    this.loading.set(true);
    this.dashboardService.stats().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.loading.set(false);
        this.recomputeChartOptions();
      },
      error: (err) => {
        this.loading.set(false);
        this.toast.error(err.error ?? 'Could not load the dashboard.');
      },
    });
  }

  private recomputeChartOptions(): void {
    const muted = cssVar('--muted-foreground');
    const gridColor = cssVar('--border');
    const common = {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
    };

    this.lineOptions.set({
      ...common,
      scales: {
        x: { ticks: { color: muted }, grid: { color: gridColor } },
        y: { ticks: { color: muted }, grid: { color: gridColor } },
      },
    });
    this.barOptions.set({
      ...common,
      scales: {
        x: { ticks: { color: muted }, grid: { display: false } },
        y: { ticks: { color: muted }, grid: { color: gridColor }, beginAtZero: true },
      },
    });
    this.scatterOptions.set({
      ...common,
      scales: {
        x: {
          title: { display: true, text: 'Body fat %', color: muted },
          ticks: { color: muted },
          grid: { color: gridColor },
        },
        y: {
          title: { display: true, text: 'Skeletal muscle mass (kg)', color: muted },
          ticks: { color: muted },
          grid: { color: gridColor },
        },
      },
    });
  }
}
