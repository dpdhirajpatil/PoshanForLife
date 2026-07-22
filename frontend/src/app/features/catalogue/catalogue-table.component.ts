import { CurrencyPipe, TitleCasePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import {
  CATALOGUE_STATUSES,
  CATALOGUE_TYPE_META,
  CatalogueItem,
  CatalogueStatus,
  CatalogueType,
} from '../../core/models/catalogue.model';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import {
  CatalogueItemFormDialogComponent,
  CatalogueItemFormDialogData,
} from './catalogue-item-form-dialog.component';
import { CatalogueService } from './catalogue.service';

/**
 * One table for all three catalogue types (used by each tab). ADMIN gets the
 * add/edit/delete controls and the inline status dropdown; DOCTOR gets a
 * read-only view with status badges.
 */
@Component({
  selector: 'app-catalogue-table',
  standalone: true,
  imports: [
    CurrencyPipe,
    TitleCasePipe,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatDialogModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  template: `
    <div class="filters">
      <mat-form-field appearance="outline" class="search" subscriptSizing="dynamic">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="search" placeholder="Name, code or category" />
        <mat-icon matPrefix>search</mat-icon>
      </mat-form-field>

      <mat-form-field appearance="outline" class="status-filter" subscriptSizing="dynamic">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusFilter">
          <mat-option value="">All</mat-option>
          @for (s of statuses; track s) {
            <mat-option [value]="s">{{ s | titlecase }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <span class="spacer"></span>

      @if (isAdmin) {
        <button mat-flat-button color="primary" (click)="openCreate()">
          <mat-icon>add</mat-icon>
          Add {{ meta().singular }}
        </button>
      }
    </div>

    @if (deleteError(); as message) {
      <div class="inline-error" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ message }}</span>
        <button mat-icon-button (click)="deleteError.set(null)" aria-label="Dismiss">
          <mat-icon>close</mat-icon>
        </button>
      </div>
    }

    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    <div class="table-scroll">
    <table mat-table [dataSource]="items()">
      <ng-container matColumnDef="name">
        <th mat-header-cell *matHeaderCellDef>Name</th>
        <td mat-cell *matCellDef="let item">
          <div class="name-cell">
            @if (item.coverImageUrl) {
              <img class="thumb" [src]="item.coverImageUrl" alt="" />
            }
            <span>{{ item.name }}</span>
          </div>
        </td>
      </ng-container>

      <ng-container matColumnDef="serviceCode">
        <th mat-header-cell *matHeaderCellDef>Code</th>
        <td mat-cell *matCellDef="let item">
          <code>{{ item.serviceCode }}</code>
        </td>
      </ng-container>

      <ng-container matColumnDef="type">
        <th mat-header-cell *matHeaderCellDef>Category</th>
        <td mat-cell *matCellDef="let item">{{ item.type }}</td>
      </ng-container>

      <ng-container matColumnDef="price">
        <th mat-header-cell *matHeaderCellDef>Price</th>
        <td mat-cell *matCellDef="let item">
          {{ item.priceInr | currency: 'INR' : 'symbol' : '1.0-2' }}
        </td>
      </ng-container>

      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let item">
          @if (isAdmin) {
            <mat-form-field appearance="outline" class="status-select" subscriptSizing="dynamic">
              <mat-select
                [value]="item.status"
                (selectionChange)="changeStatus(item, $event.value)"
                aria-label="Change status"
              >
                @for (s of statuses; track s) {
                  <mat-option [value]="s">{{ s | titlecase }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          } @else {
            <span class="status-badge" [class]="'status-' + item.status">
              {{ item.status | titlecase }}
            </span>
          }
        </td>
      </ng-container>

      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let item">
          <button
            mat-icon-button
            [matMenuTriggerFor]="rowMenu"
            [matMenuTriggerData]="{ item }"
            aria-label="Item actions"
          >
            <mat-icon>more_vert</mat-icon>
          </button>
        </td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="columns()"></tr>
      <tr mat-row *matRowDef="let row; columns: columns()"></tr>

      <tr class="mat-mdc-row" *matNoDataRow>
        <td class="mat-mdc-cell no-data" [attr.colspan]="columns().length">
          @if (!loading()) {
            No {{ meta().singular }}s match the current filters.
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

    <mat-menu #rowMenu="matMenu">
      <ng-template matMenuContent let-item="item">
        <button mat-menu-item (click)="openEdit(item)">
          <mat-icon>edit</mat-icon>
          Edit
        </button>
        <span
          [matTooltip]="deleteBlockedReason(item)"
          [matTooltipDisabled]="!isDeleteBlocked(item)"
        >
          <button mat-menu-item [disabled]="isDeleteBlocked(item)" (click)="confirmDelete(item)">
            <mat-icon>delete_outline</mat-icon>
            Delete
          </button>
        </span>
      </ng-template>
    </mat-menu>
  `,
  styles: `
    .filters {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
      flex-wrap: wrap;
    }
    .search {
      width: 100%;
      max-width: 360px;
    }
    .status-filter {
      width: 160px;
    }
    .spacer {
      flex: 1;
    }
    .inline-error {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 16px 8px;
      padding: 8px 12px;
      border-radius: 6px;
      background: var(--badge-red-bg);
      color: var(--badge-red-fg);
      font-size: 0.88rem;
    }
    .inline-error span {
      flex: 1;
    }
    table {
      width: 100%;
    }
    .name-cell {
      display: flex;
      align-items: center;
      gap: 10px;
      font-weight: 500;
    }
    .thumb {
      width: 36px;
      height: 36px;
      border-radius: 6px;
      object-fit: cover;
    }
    code {
      font-size: 0.82rem;
      background: var(--muted);
      padding: 2px 6px;
      border-radius: 4px;
    }
    .status-select {
      width: 140px;
      font-size: 0.85rem;
    }
    .status-badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
    }
    .status-draft {
      background: var(--badge-grey-bg);
      color: var(--badge-grey-fg);
    }
    .status-published {
      background: var(--badge-green-bg);
      color: var(--badge-green-fg);
    }
    .status-archived {
      background: var(--badge-orange-bg);
      color: var(--badge-orange-fg);
    }
    .no-data {
      padding: 24px;
      text-align: center;
      color: var(--muted-foreground);
    }
  `,
})
export class CatalogueTableComponent {
  private readonly catalogueService = inject(CatalogueService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  readonly type = input.required<CatalogueType>();

  protected readonly isAdmin = inject(AuthService).isAdmin();
  protected readonly statuses = CATALOGUE_STATUSES;

  protected readonly items = signal<CatalogueItem[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(1);
  protected readonly limit = signal(10);
  protected readonly loading = signal(false);
  /** Backend delete-conflict message shown inline above the table. */
  protected readonly deleteError = signal<string | null>(null);

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly statusFilter = new FormControl<'' | CatalogueStatus>('', {
    nonNullable: true,
  });

  /** Doctors browse read-only — no actions column. */
  protected readonly columns = signal(
    this.isAdmin
      ? ['name', 'serviceCode', 'type', 'price', 'status', 'actions']
      : ['name', 'serviceCode', 'type', 'price', 'status'],
  );

  protected meta = () => CATALOGUE_TYPE_META[this.type()];

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.page.set(1);
        this.load();
      });
    this.statusFilter.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.page.set(1);
      this.load();
    });
  }

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.catalogueService
      .list(this.type(), {
        search: this.search.value || undefined,
        status: this.statusFilter.value || undefined,
        page: this.page(),
        limit: this.limit(),
      })
      .subscribe({
        next: (result) => {
          this.items.set(result.data);
          this.total.set(result.meta?.total ?? result.data.length);
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

  protected openCreate(): void {
    this.dialog
      .open(CatalogueItemFormDialogComponent, {
        data: { type: this.type() } satisfies CatalogueItemFormDialogData,
      })
      .afterClosed()
      .subscribe((created) => created && this.load());
  }

  protected openEdit(item: CatalogueItem): void {
    this.dialog
      .open(CatalogueItemFormDialogComponent, {
        data: { type: this.type(), item } satisfies CatalogueItemFormDialogData,
      })
      .afterClosed()
      .subscribe((updated) => updated && this.load());
  }

  protected changeStatus(item: CatalogueItem, status: CatalogueStatus): void {
    if (status === item.status) return;
    this.catalogueService.update(this.type(), item.id, { status }).subscribe({
      next: (updated) => {
        this.toast.success(`${updated.name} is now ${updated.status}`);
        this.load();
      },
      error: (err: ApiError) => {
        this.toast.error(err.error);
        this.load(); // reset the select to the server's state
      },
    });
  }

  protected isDeleteBlocked(item: CatalogueItem): boolean {
    return item.activeAssignmentCount > 0 && item.status !== 'archived';
  }

  protected deleteBlockedReason(item: CatalogueItem): string {
    return (
      `${item.activeAssignmentCount} active patient assignment(s) reference this ` +
      `${this.meta().singular}. Archive it instead of deleting.`
    );
  }

  protected confirmDelete(item: CatalogueItem): void {
    const data: ConfirmDialogData = {
      title: `Delete ${this.meta().singular}`,
      message: `"${item.name}" (${item.serviceCode}) will be permanently removed from the catalogue.`,
      confirmLabel: 'Delete',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.catalogueService.remove(this.type(), item.id).subscribe({
          next: () => {
            this.deleteError.set(null);
            this.toast.success(`${item.name} deleted`);
            this.load();
          },
          error: (err: ApiError) => {
            // e.g. an assignment was created after the list loaded
            this.deleteError.set(err.error);
            this.load();
          },
        });
      });
  }
}
