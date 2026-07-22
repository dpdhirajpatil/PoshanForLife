import { DatePipe, TitleCasePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiError } from '../../core/models/api-response.model';
import { AuthService } from '../../core/services/auth.service';
import { ReportDetail } from '../../core/models/report.model';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { INBODY_FIELD_GROUPS } from './inbody-field-groups';
import { ReportsService } from './reports.service';

/**
 * Read-only report detail: patient, status, notes, a link to the original
 * PDF (when one exists), and — for InBody reports — the parsed fields
 * grouped the same way the upload review screen groups them.
 */
@Component({
  selector: 'app-report-detail-dialog',
  standalone: true,
  imports: [DatePipe, TitleCasePipe, MatDialogModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Report detail</h2>

    <mat-dialog-content>
      @if (report(); as r) {
        <div class="detail">
          <div class="row">
            <span class="label">Patient</span>
            <span>
              {{ r.patient.name }}
              <span class="muted">· {{ r.patient.email }}</span>
            </span>
          </div>
          <div class="row">
            <span class="label">Title</span>
            <span>{{ r.title }}</span>
          </div>
          <div class="row">
            <span class="label">Type</span>
            <span class="badge" [class]="'type-' + r.type">{{ r.type | titlecase }}</span>
            <span class="badge" [class]="'status-' + r.status">{{ r.status | titlecase }}</span>
          </div>
          @if (r.notes) {
            <div class="row">
              <span class="label">Notes</span>
              <span>{{ r.notes }}</span>
            </div>
          }
          <div class="row">
            <span class="label">Created</span>
            <span>{{ r.createdAt | date: 'medium' }} <span class="muted">by {{ r.createdBy.name }}</span></span>
          </div>

          @if (r.fileUrl) {
            <a class="pdf-link" [href]="r.fileUrl" target="_blank" rel="noopener">
              <mat-icon>picture_as_pdf</mat-icon>
              View original PDF
            </a>
          }

          @if (r.status === 'error') {
            <div class="error-banner">
              <mat-icon>error_outline</mat-icon>
              <span>This report could not be processed. Try re-uploading a clearer scan.</span>
            </div>
          }

          @if (r.parsedData) {
            <p class="muted extraction-meta">
              Extraction: {{ r.extractionMethod }} · confidence
              <strong [class.low-confidence]="r.confidence === 'low'">{{ r.confidence }}</strong>
              · {{ r.extractedFieldCount }} of 20 fields found
            </p>
            @for (group of fieldGroups; track group.label) {
              <h3 class="group-label">{{ group.label }}</h3>
              <div class="field-grid">
                @for (f of group.fields; track f.key) {
                  <div class="field">
                    <span class="field-label">{{ f.label }}</span>
                    <span class="field-value">
                      {{ r.parsedData[f.key] ?? '—' }}
                      @if (r.parsedData[f.key] != null && f.unit) {
                        {{ f.unit }}
                      }
                    </span>
                  </div>
                }
              </div>
            }
          }
        </div>
      } @else if (loading()) {
        <div class="center"><mat-spinner diameter="32" /></div>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      @if (isAdmin() && !loading()) {
        <button mat-button color="warn" (click)="delete()" [disabled]="deleting()">
          @if (deleting()) {
            <mat-spinner diameter="18" />
          } @else {
            Delete
          }
        </button>
      }
      <span class="spacer"></span>
      <button mat-button (click)="ref.close(deleted())">Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .center {
      display: grid;
      place-content: center;
      padding: 32px;
      width: 100%;
      max-width: 480px;
    }
    .detail {
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 8px 0;
      width: 100%;
      max-width: 620px;
    }
    .row {
      display: flex;
      gap: 12px;
      align-items: center;
    }
    .label {
      width: 90px;
      flex-shrink: 0;
      font-size: 0.82rem;
      color: var(--muted-foreground);
    }
    .muted {
      color: var(--muted-foreground);
    }
    .badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      margin-right: 6px;
    }
    .type-inbody { background: var(--badge-blue-bg); color: var(--badge-blue-fg); }
    .type-lab { background: var(--badge-purple-bg); color: var(--badge-purple-fg); }
    .type-prescription { background: var(--badge-amber-bg); color: var(--badge-amber-fg); }
    .type-other { background: var(--badge-grey-bg); color: var(--badge-grey-fg); }
    .status-pending { background: var(--badge-grey-bg); color: var(--badge-grey-fg); }
    .status-processing { background: var(--badge-amber-bg); color: var(--badge-amber-fg); }
    .status-done { background: var(--badge-green-bg); color: var(--badge-green-fg); }
    .status-error { background: var(--badge-red-bg); color: var(--badge-red-fg); }
    .pdf-link {
      display: flex;
      align-items: center;
      gap: 6px;
      color: var(--primary);
      text-decoration: none;
      font-weight: 500;
    }
    .pdf-link:hover {
      text-decoration: underline;
    }
    .error-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      background: var(--badge-red-bg);
      color: var(--badge-red-fg);
      border-radius: 8px;
      padding: 10px 12px;
      font-size: 0.85rem;
    }
    .extraction-meta {
      font-size: 0.8rem;
      margin: 4px 0 0;
    }
    .low-confidence {
      color: var(--badge-amber-fg);
    }
    .group-label {
      margin: 10px 0 4px;
      font-size: 0.8rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--muted-foreground);
    }
    .field-grid {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .field {
      display: flex;
      justify-content: space-between;
      padding: 3px 0;
      border-bottom: 1px solid var(--border);
      font-size: 0.88rem;
    }
    .field-label {
      color: var(--muted-foreground);
    }
    .field-value {
      font-weight: 500;
    }
    .spacer {
      flex: 1;
    }
  `,
})
export class ReportDetailDialogComponent {
  protected readonly ref = inject(MatDialogRef<ReportDetailDialogComponent>);
  private readonly reportId = inject<string>(MAT_DIALOG_DATA);
  private readonly reportsService = inject(ReportsService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);

  protected readonly fieldGroups = INBODY_FIELD_GROUPS;
  protected readonly report = signal<ReportDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly deleting = signal(false);
  protected readonly deleted = signal(false);
  protected readonly isAdmin = this.auth.isAdmin;

  constructor() {
    this.reportsService.get(this.reportId).subscribe({
      next: (report) => {
        this.report.set(report);
        this.loading.set(false);
      },
      error: (err: ApiError) => {
        this.loading.set(false);
        this.toast.error(err.error);
        this.ref.close();
      },
    });
  }

  protected delete(): void {
    const current = this.report();
    if (!current || this.deleting()) return;
    const data: ConfirmDialogData = {
      title: 'Delete report',
      message: `Delete "${current.title}" for ${current.patient.name}? This cannot be undone.`,
      confirmLabel: 'Delete',
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.deleting.set(true);
        this.reportsService.remove(current.id).subscribe({
          next: () => {
            this.deleting.set(false);
            this.deleted.set(true);
            this.toast.success('Report deleted');
            this.ref.close(true);
          },
          error: (err: ApiError) => {
            this.deleting.set(false);
            this.toast.error(err.error);
          },
        });
      });
  }
}
