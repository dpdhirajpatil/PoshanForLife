import { DatePipe, TitleCasePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, merge } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import { ReportListItem, ReportStats, ReportStatus, ReportType } from '../../core/models/report.model';
import { ToastService } from '../../core/services/toast.service';
import { CreateReportDialogComponent } from './create-report-dialog.component';
import { ReportDetailDialogComponent } from './report-detail-dialog.component';
import { ReportsService } from './reports.service';
import { UploadReportDialogComponent } from './upload-report-dialog.component';

const TYPES: ReportType[] = ['inbody', 'lab', 'prescription', 'other'];
const STATUSES: ReportStatus[] = ['pending', 'processing', 'done', 'error'];

/**
 * Reports: summary cards over the current filter set, a filterable table,
 * an AI-powered InBody PDF upload flow, and a manual "Create report" form
 * for LAB/PRESCRIPTION/OTHER types. DOCTOR callers are scoped server-side to
 * their own patients' reports.
 */
@Component({
  selector: 'app-reports-page',
  standalone: true,
  imports: [
    DatePipe,
    TitleCasePipe,
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatDialogModule,
    MatProgressBarModule,
  ],
  template: `
    <div class="page-header">
      <h1>Reports</h1>
      <div class="header-actions">
        <button mat-stroked-button (click)="openCreate()">
          <mat-icon>note_add</mat-icon>
          Create report
        </button>
        <button mat-flat-button color="primary" (click)="openUpload()">
          <mat-icon>upload_file</mat-icon>
          Upload InBody report
        </button>
      </div>
    </div>

    <div class="summary-row">
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">{{ stats()?.total ?? 0 }}</span>
        <span class="stat-label">Total reports</span>
      </mat-card>
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">{{ stats()?.thisMonth ?? 0 }}</span>
        <span class="stat-label">This month</span>
      </mat-card>
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">{{ stats()?.processing ?? 0 }}</span>
        <span class="stat-label">Processing</span>
      </mat-card>
      <mat-card appearance="outlined" class="stat-card error-stat">
        <span class="stat-value">{{ stats()?.error ?? 0 }}</span>
        <span class="stat-label">Failed</span>
      </mat-card>
    </div>

    <mat-card appearance="outlined">
      <div class="filters">
        <mat-form-field appearance="outline" class="search" subscriptSizing="dynamic">
          <mat-label>Search</mat-label>
          <input matInput [formControl]="search" placeholder="Patient name, email or title" />
          <mat-icon matPrefix>search</mat-icon>
        </mat-form-field>

        <mat-form-field appearance="outline" class="select" subscriptSizing="dynamic">
          <mat-label>Type</mat-label>
          <mat-select [formControl]="type">
            <mat-option value="">All</mat-option>
            @for (t of types; track t) {
              <mat-option [value]="t">{{ t | titlecase }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="select" subscriptSizing="dynamic">
          <mat-label>Status</mat-label>
          <mat-select [formControl]="status">
            <mat-option value="">All</mat-option>
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ s | titlecase }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="range" subscriptSizing="dynamic">
          <mat-label>Date range</mat-label>
          <mat-date-range-input [rangePicker]="rangePicker">
            <input matStartDate [formControl]="dateFrom" placeholder="From" />
            <input matEndDate [formControl]="dateTo" placeholder="To" />
          </mat-date-range-input>
          <mat-datepicker-toggle matIconSuffix [for]="rangePicker" />
          <mat-date-range-picker #rangePicker />
        </mat-form-field>
      </div>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <div class="table-scroll">
      <table mat-table [dataSource]="reports()">
        <ng-container matColumnDef="patient">
          <th mat-header-cell *matHeaderCellDef>Patient</th>
          <td mat-cell *matCellDef="let r">
            <a class="row-link" (click)="openDetail(r)">{{ r.patient.name }}</a>
          </td>
        </ng-container>

        <ng-container matColumnDef="title">
          <th mat-header-cell *matHeaderCellDef>Title</th>
          <td mat-cell *matCellDef="let r">{{ r.title }}</td>
        </ng-container>

        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">
            <span class="badge" [class]="'type-' + r.type">{{ r.type | titlecase }}</span>
          </td>
        </ng-container>

        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">
            <span class="badge" [class]="'status-' + r.status">{{ r.status | titlecase }}</span>
          </td>
        </ng-container>

        <ng-container matColumnDef="date">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let r">{{ r.createdAt | date: 'mediumDate' }}</td>
        </ng-container>

        <ng-container matColumnDef="uploadedBy">
          <th mat-header-cell *matHeaderCellDef>Uploaded by</th>
          <td mat-cell *matCellDef="let r">{{ r.createdBy.name }}</td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>

        <tr class="mat-mdc-row" *matNoDataRow>
          <td class="mat-mdc-cell no-data" [attr.colspan]="columns.length">
            @if (!loading()) {
              No reports match the current filters.
            }
          </td>
        </tr>
      </table>
      </div>

      <mat-paginator
        [length]="total()"
        [pageIndex]="page() - 1"
        [pageSize]="limit()"
        [pageSizeOptions]="[10, 25, 50]"
        (page)="onPage($event)"
        showFirstLastButtons
      />
    </mat-card>
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
    .header-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .summary-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 16px;
      margin-bottom: 16px;
    }
    .stat-card {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .error-stat .stat-value {
      color: var(--badge-red-fg);
    }
    .stat-value {
      font-size: 1.7rem;
      font-weight: 600;
    }
    .stat-label {
      color: var(--muted-foreground);
      font-size: 0.85rem;
    }
    .filters {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      padding: 24px 16px 4px;
    }
    .search {
      flex: 1 1 260px;
    }
    .select {
      flex: 0 1 160px;
    }
    .range {
      flex: 0 1 240px;
    }
    table {
      width: 100%;
    }
    .row-link {
      color: var(--primary);
      cursor: pointer;
      font-weight: 500;
    }
    .row-link:hover {
      text-decoration: underline;
    }
    .badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
    }
    .type-inbody { background: var(--badge-blue-bg); color: var(--badge-blue-fg); }
    .type-lab { background: var(--badge-purple-bg); color: var(--badge-purple-fg); }
    .type-prescription { background: var(--badge-amber-bg); color: var(--badge-amber-fg); }
    .type-other { background: var(--badge-grey-bg); color: var(--badge-grey-fg); }
    .status-pending { background: var(--badge-grey-bg); color: var(--badge-grey-fg); }
    .status-processing { background: var(--badge-amber-bg); color: var(--badge-amber-fg); }
    .status-done { background: var(--badge-green-bg); color: var(--badge-green-fg); }
    .status-error { background: var(--badge-red-bg); color: var(--badge-red-fg); }
    .no-data {
      text-align: center;
      padding: 24px;
      color: var(--muted-foreground);
    }
  `,
})
export class ReportsPageComponent {
  private readonly reportsService = inject(ReportsService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  protected readonly types = TYPES;
  protected readonly statuses = STATUSES;
  protected readonly columns = ['patient', 'title', 'type', 'status', 'date', 'uploadedBy'];

  protected readonly reports = signal<ReportListItem[]>([]);
  protected readonly stats = signal<ReportStats | null>(null);
  protected readonly total = signal(0);
  protected readonly page = signal(1);
  protected readonly limit = signal(10);
  protected readonly loading = signal(false);

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly type = new FormControl<'' | ReportType>('', { nonNullable: true });
  protected readonly status = new FormControl<'' | ReportStatus>('', { nonNullable: true });
  protected readonly dateFrom = new FormControl<Date | null>(null);
  protected readonly dateTo = new FormControl<Date | null>(null);

  constructor() {
    this.load();
    merge(
      this.search.valueChanges.pipe(debounceTime(300)),
      this.type.valueChanges,
      this.status.valueChanges,
      this.dateFrom.valueChanges,
      this.dateTo.valueChanges,
    )
      .pipe(debounceTime(50), takeUntilDestroyed())
      .subscribe(() => {
        this.page.set(1);
        this.load();
      });
  }

  protected load(): void {
    this.loading.set(true);
    this.reportsService
      .list({
        search: this.search.value || undefined,
        type: (this.type.value || undefined) as ReportType | undefined,
        status: (this.status.value || undefined) as ReportStatus | undefined,
        dateFrom: this.dateFrom.value ? toIsoDate(this.dateFrom.value) : undefined,
        dateTo: this.dateTo.value ? toIsoDate(this.dateTo.value) : undefined,
        page: this.page(),
        limit: this.limit(),
      })
      .subscribe({
        next: (result) => {
          this.reports.set(result.data.reports);
          this.stats.set(result.data.stats);
          this.total.set(result.meta?.total ?? result.data.reports.length);
          this.loading.set(false);
        },
        error: (err: ApiError) => {
          this.loading.set(false);
          this.toast.error(err.error);
        },
      });
  }

  protected onPage(event: PageEvent): void {
    this.page.set(event.pageIndex + 1);
    this.limit.set(event.pageSize);
    this.load();
  }

  protected openDetail(report: ReportListItem): void {
    this.dialog
      .open(ReportDetailDialogComponent, { data: report.id })
      .afterClosed()
      .subscribe((changed) => changed && this.load());
  }

  protected openUpload(): void {
    this.dialog
      .open(UploadReportDialogComponent)
      .afterClosed()
      .subscribe((created) => created && this.load());
  }

  protected openCreate(): void {
    this.dialog
      .open(CreateReportDialogComponent)
      .afterClosed()
      .subscribe((created) => created && this.load());
  }
}

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
