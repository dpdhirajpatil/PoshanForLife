import { CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
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
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged, merge } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import { OrderListItem, OrderStatus } from '../../core/models/order.model';
import { OrderPaymentStatus } from '../../core/models/patient-programme.model';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { OrderDetailDialogComponent } from './order-detail-dialog.component';
import { OrdersService } from './orders.service';

const ORDER_STATUSES: OrderStatus[] = ['active', 'completed', 'deactivated'];
const PAYMENT_STATUSES: OrderPaymentStatus[] = ['paid', 'unpaid', 'pending'];

/**
 * Orders list. DOCTOR users are scoped to their own patients server-side —
 * the page is identical for both roles. Orders are never created here; they
 * come from the patient "Assign a service" flow.
 */
@Component({
  selector: 'app-orders-page',
  standalone: true,
  imports: [
    CurrencyPipe,
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
    MatTooltipModule,
  ],
  template: `
    <div class="page-header">
      <h1>Orders</h1>
    </div>

    <mat-card appearance="outlined">
      <div class="filters">
        <mat-form-field appearance="outline" class="search" subscriptSizing="dynamic">
          <mat-label>Search</mat-label>
          <input matInput [formControl]="search" placeholder="Patient name or email" />
          <mat-icon matPrefix>search</mat-icon>
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

        <mat-form-field appearance="outline" class="select" subscriptSizing="dynamic">
          <mat-label>Payment</mat-label>
          <mat-select [formControl]="paymentStatus">
            <mat-option value="">All</mat-option>
            @for (s of paymentStatuses; track s) {
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

        @if (dateFrom.value || dateTo.value) {
          <button mat-icon-button (click)="clearDates()" matTooltip="Clear date range">
            <mat-icon>filter_alt_off</mat-icon>
          </button>
        }
      </div>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <div class="table-scroll">
      <table mat-table [dataSource]="orders()">
        <ng-container matColumnDef="patient">
          <th mat-header-cell *matHeaderCellDef>Patient</th>
          <td mat-cell *matCellDef="let o">{{ o.patient.name }}</td>
        </ng-container>

        <ng-container matColumnDef="service">
          <th mat-header-cell *matHeaderCellDef>Service</th>
          <td mat-cell *matCellDef="let o">
            @if (o.serviceName) {
              <div class="service-cell">
                <span>{{ o.serviceName }}</span>
                <span class="service-meta">{{ o.serviceType | titlecase }}</span>
              </div>
            } @else {
              <span class="muted">—</span>
            }
          </td>
        </ng-container>

        <ng-container matColumnDef="amount">
          <th mat-header-cell *matHeaderCellDef>Amount</th>
          <td mat-cell *matCellDef="let o">
            {{ o.amountInr | currency: 'INR' : 'symbol' : '1.0-2' }}
          </td>
        </ng-container>

        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let o">
            <span class="badge" [class]="'status-' + o.status">{{ o.status | titlecase }}</span>
          </td>
        </ng-container>

        <ng-container matColumnDef="payment">
          <th mat-header-cell *matHeaderCellDef>Payment</th>
          <td mat-cell *matCellDef="let o">
            <span class="badge" [class]="'payment-' + o.paymentStatus">
              {{ o.paymentStatus | titlecase }}
            </span>
          </td>
        </ng-container>

        <ng-container matColumnDef="date">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let o">{{ o.createdAt | date: 'mediumDate' }}</td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let o">
            @if (o.paymentStatus !== 'paid') {
              <button
                mat-icon-button
                matTooltip="Mark as paid"
                (click)="markPaid(o); $event.stopPropagation()"
                aria-label="Mark as paid"
              >
                <mat-icon>price_check</mat-icon>
              </button>
            }
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr
          mat-row
          *matRowDef="let row; columns: columns"
          class="clickable"
          (click)="openDetail(row)"
        ></tr>

        <tr class="mat-mdc-row" *matNoDataRow>
          <td class="mat-mdc-cell no-data" [attr.colspan]="columns.length">
            @if (!loading()) {
              No orders match the current filters.
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
    .filters {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
      flex-wrap: wrap;
    }
    .search {
      width: 100%;
      max-width: 300px;
    }
    .select {
      width: 150px;
    }
    .range {
      width: 240px;
    }
    table {
      width: 100%;
    }
    .clickable {
      cursor: pointer;
    }
    .clickable:hover {
      background: var(--muted);
    }
    .service-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .service-meta {
      font-size: 0.8rem;
      color: var(--muted-foreground);
    }
    .muted {
      color: var(--muted-foreground);
    }
    .badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      white-space: nowrap;
    }
    .status-active { background: var(--badge-green-bg); color: var(--badge-green-fg); }
    .status-completed { background: var(--badge-blue-bg); color: var(--badge-blue-fg); }
    .status-deactivated { background: var(--badge-grey-bg); color: var(--badge-grey-fg); }
    .payment-paid { background: var(--badge-green-bg); color: var(--badge-green-fg); }
    .payment-pending { background: var(--badge-amber-bg); color: var(--badge-amber-fg); }
    .payment-unpaid { background: var(--badge-red-bg); color: var(--badge-red-fg); }
    .no-data {
      padding: 24px;
      text-align: center;
      color: var(--muted-foreground);
    }
  `,
})
export class OrdersPageComponent {
  private readonly ordersService = inject(OrdersService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  protected readonly statuses = ORDER_STATUSES;
  protected readonly paymentStatuses = PAYMENT_STATUSES;
  protected readonly columns = ['patient', 'service', 'amount', 'status', 'payment', 'date', 'actions'];

  protected readonly orders = signal<OrderListItem[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(1);
  protected readonly limit = signal(10);
  protected readonly loading = signal(false);

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly status = new FormControl<'' | OrderStatus>('', { nonNullable: true });
  protected readonly paymentStatus = new FormControl<'' | OrderPaymentStatus>('', {
    nonNullable: true,
  });
  protected readonly dateFrom = new FormControl<Date | null>(null);
  protected readonly dateTo = new FormControl<Date | null>(null);

  constructor() {
    this.load();
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.resetAndLoad());
    // selects apply instantly; the date-range inputs fire twice per pick, so
    // the short debounce collapses them into one reload
    merge(this.status.valueChanges, this.paymentStatus.valueChanges,
        this.dateFrom.valueChanges, this.dateTo.valueChanges)
      .pipe(debounceTime(100), takeUntilDestroyed())
      .subscribe(() => this.resetAndLoad());
  }

  protected load(): void {
    this.loading.set(true);
    this.ordersService
      .list({
        search: this.search.value || undefined,
        status: this.status.value || undefined,
        paymentStatus: this.paymentStatus.value || undefined,
        dateFrom: this.dateFrom.value ? toIsoDate(this.dateFrom.value) : undefined,
        dateTo: this.dateTo.value ? toIsoDate(this.dateTo.value) : undefined,
        page: this.page(),
        limit: this.limit(),
      })
      .subscribe({
        next: (result) => {
          this.orders.set(result.data);
          this.total.set(result.meta?.total ?? result.data.length);
          this.loading.set(false);
        },
        error: (err: ApiError) => {
          this.loading.set(false);
          this.toast.error(err.error);
        },
      });
  }

  protected resetAndLoad(): void {
    this.page.set(1);
    this.load();
  }

  protected clearDates(): void {
    this.dateFrom.setValue(null);
    this.dateTo.setValue(null);
  }

  protected onPage(event: PageEvent): void {
    this.page.set(event.pageIndex + 1);
    this.limit.set(event.pageSize);
    this.load();
  }

  protected openDetail(order: OrderListItem): void {
    this.dialog
      .open(OrderDetailDialogComponent, { data: order.id })
      .afterClosed()
      .subscribe((updated) => updated && this.load());
  }

  protected markPaid(order: OrderListItem): void {
    const data: ConfirmDialogData = {
      title: 'Mark order as paid',
      message:
        `${order.patient.name}'s order of ₹${order.amountInr} will be marked paid. ` +
        `If no transaction exists yet, an activation transaction with a new invoice ` +
        `number will be generated.`,
      confirmLabel: 'Mark as paid',
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.ordersService.update(order.id, { paymentStatus: 'paid' }).subscribe({
          next: (updated) => {
            const invoice = updated.transactions[0]?.invoiceNumber;
            this.toast.success(
              invoice ? `Order marked paid — invoice ${invoice}` : 'Order marked paid',
            );
            this.load();
          },
          error: (err: ApiError) => this.toast.error(err.error),
        });
      });
  }
}

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
