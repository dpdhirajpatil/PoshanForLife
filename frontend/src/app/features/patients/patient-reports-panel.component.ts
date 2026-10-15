import { DatePipe, TitleCasePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ReportListItem } from '../../core/models/report.model';
import { AuthService } from '../../core/services/auth.service';
import { openSidePanel } from '../../shared/side-panel';
import { ReportDetailDialogComponent } from '../reports/report-detail-dialog.component';
import { ReportsService } from '../reports/reports.service';
import { UploadReportDialogComponent, UploadReportDialogData } from '../reports/upload-report-dialog.component';

/**
 * The patient detail page's Reports tab: this patient's reports (newest
 * first) and a direct InBody upload (ADMIN/DOCTOR) that skips the patient
 * picker. Rows open the same detail dialog as the Reports page.
 */
@Component({
  selector: 'app-patient-reports-panel',
  standalone: true,
  imports: [
    DatePipe,
    TitleCasePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressBarModule,
    MatDialogModule,
  ],
  template: `
    <div class="panel-header">
      <h3>Reports</h3>
      @if (canManage) {
        <button mat-flat-button color="primary" (click)="openUpload()">
          <mat-icon>upload_file</mat-icon>
          Upload InBody report
        </button>
      }
    </div>

    <mat-card appearance="outlined">
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (!loading() && reports().length === 0) {
        <p class="no-data">No reports yet.</p>
      } @else {
        <div class="table-scroll">
          <table mat-table [dataSource]="reports()">
            <ng-container matColumnDef="title">
              <th mat-header-cell *matHeaderCellDef>Title</th>
              <td mat-cell *matCellDef="let r">
                <a class="row-link" (click)="openDetail(r)">{{ r.title }}</a>
              </td>
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
          </table>
        </div>
      }
    </mat-card>
  `,
  styles: `
    .panel-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 12px;
    }
    .panel-header h3 {
      margin: 0;
      font-size: 1.05rem;
    }
    .table-scroll {
      overflow-x: auto;
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
      margin: 0;
      color: var(--muted-foreground);
    }
  `,
})
export class PatientReportsPanelComponent implements OnInit {
  readonly patientId = input.required<string>();
  readonly patientName = input.required<string>();
  readonly patientEmail = input<string>('');

  private readonly reportsService = inject(ReportsService);
  private readonly dialog = inject(MatDialog);
  private readonly auth = inject(AuthService);

  protected readonly canManage = this.auth.hasAnyRole('ADMIN', 'DOCTOR');
  protected readonly columns = ['title', 'type', 'status', 'date', 'uploadedBy'];
  protected readonly reports = signal<ReportListItem[]>([]);
  protected readonly loading = signal(false);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.reportsService.list({ patientId: this.patientId(), page: 1, limit: 50 }).subscribe({
      next: (res) => {
        this.reports.set(res.data.reports);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected openUpload(): void {
    const data: UploadReportDialogData = {
      patient: { id: this.patientId(), name: this.patientName(), email: this.patientEmail() },
    };
    openSidePanel(this.dialog, UploadReportDialogComponent, { data })
      .afterClosed()
      .subscribe(() => this.load());
  }

  protected openDetail(report: ReportListItem): void {
    this.dialog
      .open(ReportDetailDialogComponent, { data: report.id })
      .afterClosed()
      .subscribe((changed) => changed && this.load());
  }
}
