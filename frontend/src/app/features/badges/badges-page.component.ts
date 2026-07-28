import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiError } from '../../core/models/api-response.model';
import { BADGE_CRITERIA_TYPE_LABELS, Badge, BadgeCriteriaType } from '../../core/models/badge.model';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { openSidePanel } from '../../shared/side-panel';
import { BadgeFormDialogComponent } from './badge-form-dialog.component';
import { BadgesService } from './badges.service';

/** ADMIN-only badge catalog management — simple CRUD, no patient-facing gamification UI here (that's mobile-only). */
@Component({
  selector: 'app-badges-page',
  standalone: true,
  imports: [
    DatePipe,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  template: `
    <div class="page-header">
      <h1>Badges</h1>
      <button mat-flat-button color="primary" (click)="openCreate()">
        <mat-icon>add</mat-icon>
        Add badge
      </button>
    </div>

    <mat-card appearance="outlined">
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <div class="table-scroll">
        <table mat-table [dataSource]="badges()">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>Name</th>
            <td mat-cell *matCellDef="let b">
              <div class="name-cell">
                <span>{{ b.name }}</span>
                @if (b.description) {
                  <span class="muted small">{{ b.description }}</span>
                }
              </div>
            </td>
          </ng-container>

          <ng-container matColumnDef="iconKey">
            <th mat-header-cell *matHeaderCellDef>Icon key</th>
            <td mat-cell *matCellDef="let b"><code>{{ b.iconKey }}</code></td>
          </ng-container>

          <ng-container matColumnDef="criteria">
            <th mat-header-cell *matHeaderCellDef>Criteria</th>
            <td mat-cell *matCellDef="let b">
              <span class="badge" [class]="'criteria-' + b.criteriaType">
                {{ criteriaLabel(b.criteriaType) }}
              </span>
              @if (b.criteriaType !== 'custom' && b.criteriaType !== 'challenge_completed') {
                <span class="muted"> · {{ b.criteriaValue }}</span>
              }
            </td>
          </ng-container>

          <ng-container matColumnDef="createdAt">
            <th mat-header-cell *matHeaderCellDef>Created</th>
            <td mat-cell *matCellDef="let b">{{ b.createdAt | date: 'mediumDate' }}</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let b">
              <button mat-icon-button matTooltip="Edit" (click)="openEdit(b)" aria-label="Edit badge">
                <mat-icon>edit</mat-icon>
              </button>
              <button mat-icon-button matTooltip="Delete" (click)="confirmDelete(b)" aria-label="Delete badge">
                <mat-icon>delete_outline</mat-icon>
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>

          <tr class="mat-mdc-row" *matNoDataRow>
            <td class="mat-mdc-cell no-data" [attr.colspan]="columns.length">
              @if (!loading()) {
                No badges yet — add one above.
              }
            </td>
          </tr>
        </table>
      </div>
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
    table {
      width: 100%;
    }
    .name-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .muted {
      color: var(--muted-foreground);
    }
    .small {
      font-size: 0.8rem;
    }
    code {
      background: var(--muted);
      border-radius: 4px;
      padding: 2px 6px;
      font-size: 0.82rem;
    }
    .badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      white-space: nowrap;
    }
    .criteria-challenge_completed { background: var(--badge-green-bg); color: var(--badge-green-fg); }
    .criteria-programme_count { background: var(--badge-blue-bg); color: var(--badge-blue-fg); }
    .criteria-streak_days { background: var(--badge-amber-bg); color: var(--badge-amber-fg); }
    .criteria-custom { background: var(--badge-grey-bg); color: var(--badge-grey-fg); }
    .no-data {
      padding: 24px;
      text-align: center;
      color: var(--muted-foreground);
    }
  `,
})
export class BadgesPageComponent {
  private readonly badgesService = inject(BadgesService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  protected readonly columns = ['name', 'iconKey', 'criteria', 'createdAt', 'actions'];
  protected readonly badges = signal<Badge[]>([]);
  protected readonly loading = signal(false);

  constructor() {
    this.load();
  }

  protected criteriaLabel(type: BadgeCriteriaType): string {
    return BADGE_CRITERIA_TYPE_LABELS[type];
  }

  private load(): void {
    this.loading.set(true);
    this.badgesService.list().subscribe({
      next: (badges) => {
        this.badges.set(badges);
        this.loading.set(false);
      },
      error: (err: ApiError) => {
        this.loading.set(false);
        this.toast.error(err.error);
      },
    });
  }

  protected openCreate(): void {
    openSidePanel(this.dialog, BadgeFormDialogComponent, { data: {} })
      .afterClosed()
      .subscribe((created) => created && this.load());
  }

  protected openEdit(badge: Badge): void {
    openSidePanel(this.dialog, BadgeFormDialogComponent, { data: { badge } })
      .afterClosed()
      .subscribe((updated) => updated && this.load());
  }

  protected confirmDelete(badge: Badge): void {
    const data: ConfirmDialogData = {
      title: 'Delete badge',
      message: `"${badge.name}" will be permanently removed, along with any patients' earned records for it.`,
      confirmLabel: 'Delete',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.badgesService.delete(badge.id).subscribe({
          next: () => {
            this.toast.success('Badge deleted');
            this.load();
          },
          error: (err: ApiError) => this.toast.error(err.error),
        });
      });
  }
}
