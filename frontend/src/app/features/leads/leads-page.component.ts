import { DatePipe, TitleCasePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { debounceTime, merge } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import {
  ACTIVITY_ICONS,
  LEAD_SOURCES,
  LEAD_STAGES,
  LEAD_STAGE_LABELS,
  LeadListItem,
  LeadSource,
  LeadStage,
  LeadSummary,
} from '../../core/models/lead.model';
import { ToastService } from '../../core/services/toast.service';
import { openSidePanel } from '../../shared/side-panel';
import { CreateLeadDialogComponent } from './create-lead-dialog.component';
import { LeadsService } from './leads.service';

type ViewMode = 'board' | 'table';

/**
 * Leads/CRM: header stats over the current filter set (minus stage), a
 * Kanban board grouped by stage, and a filterable/paginated table — toggle
 * between the two. DOCTOR callers see only their own assigned leads
 * (enforced server-side).
 */
@Component({
  selector: 'app-leads-page',
  standalone: true,
  imports: [
    DatePipe,
    TitleCasePipe,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatTableModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatDialogModule,
    MatTooltipModule,
  ],
  template: `
    <div class="page-header">
      <h1>Contacts</h1>
      <button mat-flat-button color="primary" (click)="openCreate()">
        <mat-icon>person_add</mat-icon>
        Add lead
      </button>
    </div>

    <div class="summary-row">
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">{{ total() }}</span>
        <span class="stat-label">Total leads</span>
      </mat-card>
      <mat-card appearance="outlined" class="stat-card" [class.highlight]="(summary()?.followupToday ?? 0) > 0">
        <span class="stat-value">{{ summary()?.followupToday ?? 0 }}</span>
        <span class="stat-label">Follow-ups today</span>
      </mat-card>
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">{{ summary()?.newThisWeek ?? 0 }}</span>
        <span class="stat-label">New this week</span>
      </mat-card>
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">{{ summary()?.converted ?? 0 }}</span>
        <span class="stat-label">Converted</span>
      </mat-card>
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">{{ summary()?.conversionRate ?? 0 }}%</span>
        <span class="stat-label">Conversion rate</span>
      </mat-card>
    </div>

    <mat-card appearance="outlined" class="filter-card">
      <div class="filters">
        <mat-form-field appearance="outline" class="search" subscriptSizing="dynamic">
          <mat-label>Search</mat-label>
          <input matInput [formControl]="search" placeholder="Name, phone or email" />
          <mat-icon matPrefix>search</mat-icon>
        </mat-form-field>

        <mat-form-field appearance="outline" class="select" subscriptSizing="dynamic">
          <mat-label>Source</mat-label>
          <mat-select [formControl]="source">
            <mat-option value="">All</mat-option>
            @for (s of sources; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        @if (view() === 'table') {
          <mat-form-field appearance="outline" class="select" subscriptSizing="dynamic">
            <mat-label>Stage</mat-label>
            <mat-select [formControl]="stage">
              <mat-option value="">All</mat-option>
              @for (s of stages; track s) {
                <mat-option [value]="s">{{ stageLabel(s) }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }

        <mat-checkbox [formControl]="followupToday">Follow-up today</mat-checkbox>

        <span class="spacer"></span>

        <div class="view-toggle">
          <button
            type="button"
            mat-icon-button
            [class.active]="view() === 'board'"
            matTooltip="Board view"
            (click)="setView('board')"
          >
            <mat-icon>view_kanban</mat-icon>
          </button>
          <button
            type="button"
            mat-icon-button
            [class.active]="view() === 'table'"
            matTooltip="Table view"
            (click)="setView('table')"
          >
            <mat-icon>table_rows</mat-icon>
          </button>
        </div>
      </div>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      @if (view() === 'board') {
        <div class="board">
          @for (s of stages; track s) {
            <div class="column">
              <div class="column-header">
                <span>{{ stageLabel(s) }}</span>
                <span class="column-count">{{ boardColumns()[s].length }}</span>
              </div>
              <div class="column-body">
                @for (lead of boardColumns()[s]; track lead.id) {
                  <button type="button" class="lead-card" (click)="openDetail(lead)">
                    <div class="lead-card-name">{{ lead.name }}</div>
                    @if (lead.phone || lead.email) {
                      <div class="lead-card-meta">{{ lead.phone || lead.email }}</div>
                    }
                    <div class="lead-card-footer">
                      <span class="badge source-badge">{{ lead.source }}</span>
                      @if (lead.assignedPractitioner) {
                        <span class="muted">{{ lead.assignedPractitioner.name }}</span>
                      }
                    </div>
                    @if (lead.nextFollowupAt) {
                      <div class="lead-card-followup" [class.due]="isDueToday(lead.nextFollowupAt)">
                        <mat-icon inline>event</mat-icon>
                        {{ lead.nextFollowupAt | date: 'MMM d, h:mm a' }}
                      </div>
                    }
                  </button>
                } @empty {
                  <p class="empty-column">No leads</p>
                }
              </div>
            </div>
          }
        </div>
      } @else {
        <div class="table-scroll">
        <table mat-table [dataSource]="leads()">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>Name</th>
            <td mat-cell *matCellDef="let l">
              <a class="row-link" (click)="openDetail(l)">{{ l.name }}</a>
            </td>
          </ng-container>

          <ng-container matColumnDef="contact">
            <th mat-header-cell *matHeaderCellDef>Contact</th>
            <td mat-cell *matCellDef="let l">
              {{ l.phone || '—' }}
              @if (l.email) {
                <div class="muted small">{{ l.email }}</div>
              }
            </td>
          </ng-container>

          <ng-container matColumnDef="source">
            <th mat-header-cell *matHeaderCellDef>Source</th>
            <td mat-cell *matCellDef="let l">{{ l.source }}</td>
          </ng-container>

          <ng-container matColumnDef="stage">
            <th mat-header-cell *matHeaderCellDef>Stage</th>
            <td mat-cell *matCellDef="let l">
              <span class="badge" [class]="'stage-' + l.stage">{{ stageLabel(l.stage) }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="practitioner">
            <th mat-header-cell *matHeaderCellDef>Practitioner</th>
            <td mat-cell *matCellDef="let l">{{ l.assignedPractitioner?.name || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="followup">
            <th mat-header-cell *matHeaderCellDef>Next follow-up</th>
            <td mat-cell *matCellDef="let l">
              {{ l.nextFollowupAt ? (l.nextFollowupAt | date: 'mediumDate') : '—' }}
            </td>
          </ng-container>

          <ng-container matColumnDef="createdAt">
            <th mat-header-cell *matHeaderCellDef>Created</th>
            <td mat-cell *matCellDef="let l">{{ l.createdAt | date: 'mediumDate' }}</td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>

          <tr class="mat-mdc-row" *matNoDataRow>
            <td class="mat-mdc-cell no-data" [attr.colspan]="columns.length">
              @if (!loading()) {
                No leads match the current filters.
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
      }
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
    .summary-row {
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
    }
    .stat-card.highlight {
      border-color: var(--primary);
    }
    .stat-value {
      font-size: 1.6rem;
      font-weight: 600;
    }
    .stat-label {
      color: var(--muted-foreground);
      font-size: 0.85rem;
    }
    .filter-card {
      padding-bottom: 8px;
    }
    .filters {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
      padding: 24px 16px 4px;
    }
    .search {
      flex: 1 1 220px;
    }
    .select {
      flex: 0 1 160px;
    }
    .spacer {
      flex: 1;
    }
    .view-toggle {
      display: flex;
      gap: 2px;
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 2px;
    }
    .view-toggle button.active {
      background: var(--primary-tint);
      color: var(--primary);
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
    .muted {
      color: var(--muted-foreground);
    }
    .small {
      font-size: 0.78rem;
    }
    .badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
    }
    .source-badge {
      background: var(--badge-grey-bg);
      color: var(--badge-grey-fg);
    }
    .stage-new { background: var(--badge-grey-bg); color: var(--badge-grey-fg); }
    .stage-contacted { background: var(--badge-blue-bg); color: var(--badge-blue-fg); }
    .stage-qualified { background: var(--badge-purple-bg); color: var(--badge-purple-fg); }
    .stage-proposed { background: var(--badge-amber-bg); color: var(--badge-amber-fg); }
    .stage-converted { background: var(--badge-green-bg); color: var(--badge-green-fg); }
    .stage-lost { background: var(--badge-red-bg); color: var(--badge-red-fg); }
    .no-data {
      padding: 24px;
      text-align: center;
      color: var(--muted-foreground);
    }
    .board {
      display: flex;
      gap: 12px;
      padding: 16px;
      overflow-x: auto;
    }
    .column {
      flex: 0 0 240px;
      background: var(--muted);
      border-radius: 10px;
      display: flex;
      flex-direction: column;
      max-height: 640px;
    }
    .column-header {
      display: flex;
      justify-content: space-between;
      padding: 10px 12px;
      font-weight: 600;
      font-size: 0.85rem;
      border-bottom: 1px solid var(--border);
    }
    .column-count {
      color: var(--muted-foreground);
      font-weight: 400;
    }
    .column-body {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 10px;
      overflow-y: auto;
    }
    .lead-card {
      display: flex;
      flex-direction: column;
      gap: 4px;
      text-align: left;
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 10px 12px;
      cursor: pointer;
      font: inherit;
      color: inherit;
    }
    .lead-card:hover {
      border-color: var(--primary);
    }
    .lead-card-name {
      font-weight: 500;
    }
    .lead-card-meta {
      font-size: 0.8rem;
      color: var(--muted-foreground);
    }
    .lead-card-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
      font-size: 0.78rem;
      margin-top: 4px;
    }
    .lead-card-followup {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 0.75rem;
      color: var(--muted-foreground);
      margin-top: 2px;
    }
    .lead-card-followup.due {
      color: var(--badge-amber-fg);
      font-weight: 500;
    }
    .empty-column {
      color: var(--muted-foreground);
      font-size: 0.8rem;
      text-align: center;
      padding: 12px 0;
    }
  `,
})
export class LeadsPageComponent {
  private readonly leadsService = inject(LeadsService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly sources = LEAD_SOURCES;
  protected readonly stages = LEAD_STAGES;
  protected readonly columns = ['name', 'contact', 'source', 'stage', 'practitioner', 'followup', 'createdAt'];
  protected readonly activityIcons = ACTIVITY_ICONS;

  protected readonly view = signal<ViewMode>('board');
  protected readonly leads = signal<LeadListItem[]>([]);
  protected readonly boardLeads = signal<LeadListItem[]>([]);
  protected readonly summary = signal<LeadSummary | null>(null);
  protected readonly total = signal(0);
  protected readonly page = signal(1);
  protected readonly limit = signal(10);
  protected readonly loading = signal(false);

  protected readonly boardColumns = computed(() => {
    const grouped: Record<LeadStage, LeadListItem[]> = {
      new: [], contacted: [], qualified: [], proposed: [], converted: [], lost: [],
    };
    for (const lead of this.boardLeads()) {
      grouped[lead.stage].push(lead);
    }
    return grouped;
  });

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly source = new FormControl<'' | LeadSource>('', { nonNullable: true });
  protected readonly stage = new FormControl<'' | LeadStage>('', { nonNullable: true });
  protected readonly followupToday = new FormControl(false, { nonNullable: true });

  constructor() {
    this.load();
    merge(
      this.search.valueChanges.pipe(debounceTime(300)),
      this.source.valueChanges,
      this.stage.valueChanges,
      this.followupToday.valueChanges,
    )
      .pipe(debounceTime(50), takeUntilDestroyed())
      .subscribe(() => {
        this.page.set(1);
        this.load();
      });
  }

  protected stageLabel(stage: LeadStage): string {
    return LEAD_STAGE_LABELS[stage];
  }

  protected setView(view: ViewMode): void {
    this.view.set(view);
    this.load();
  }

  protected isDueToday(iso: string): boolean {
    const date = new Date(iso);
    const now = new Date();
    return date.toDateString() === now.toDateString() || date < now;
  }

  protected load(): void {
    this.loading.set(true);
    if (this.view() === 'board') {
      this.leadsService
        .list({
          search: this.search.value || undefined,
          source: (this.source.value || undefined) as LeadSource | undefined,
          followupToday: this.followupToday.value || undefined,
          page: 1,
          limit: 500,
        })
        .subscribe({
          next: (result) => {
            this.boardLeads.set(result.data.leads);
            this.summary.set(result.data.summary);
            this.total.set(result.meta?.total ?? result.data.leads.length);
            this.loading.set(false);
          },
          error: (err: ApiError) => {
            this.loading.set(false);
            this.toast.error(err.error);
          },
        });
      return;
    }

    this.leadsService
      .list({
        search: this.search.value || undefined,
        source: (this.source.value || undefined) as LeadSource | undefined,
        stage: (this.stage.value || undefined) as LeadStage | undefined,
        followupToday: this.followupToday.value || undefined,
        page: this.page(),
        limit: this.limit(),
      })
      .subscribe({
        next: (result) => {
          this.leads.set(result.data.leads);
          this.summary.set(result.data.summary);
          this.total.set(result.meta?.total ?? result.data.leads.length);
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

  protected openDetail(lead: LeadListItem): void {
    this.router.navigate(['/leads', lead.id]);
  }

  protected openCreate(): void {
    openSidePanel(this.dialog, CreateLeadDialogComponent)
      .afterClosed()
      .subscribe((created) => created && this.load());
  }
}
