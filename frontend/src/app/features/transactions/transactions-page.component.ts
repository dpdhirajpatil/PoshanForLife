import { CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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
import {
  PaymentType,
  ServiceType,
  TransactionType,
} from '../../core/models/patient-programme.model';
import { TransactionListItem, TransactionTotals } from '../../core/models/transaction.model';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { UserDetail } from '../../core/models/user.model';
import { UsersService } from '../users/users.service';
import { RecordTransactionDialogComponent } from './record-transaction-dialog.component';
import { TransactionsService } from './transactions.service';

const CATALOGUE_TYPES: ServiceType[] = ['programme', 'session', 'challenge'];
const PAYMENT_TYPES: PaymentType[] = ['offline', 'online', 'credit'];

/**
 * The financial ledger: summary cards over the current filter set, a
 * filterable table, and (ADMIN) manual entry. DOCTOR callers are scoped to
 * their own patients' transactions server-side — same page, no toggle.
 */
@Component({
  selector: 'app-transactions-page',
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
  ],
  template: `
    <div class="page-header">
      <h1>Transactions</h1>
      @if (isAdmin) {
        <button mat-flat-button color="primary" (click)="openRecordTransaction()">
          <mat-icon>add</mat-icon>
          Record manual transaction
        </button>
      }
    </div>

    <div class="summary-row">
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">
          {{ summary()?.totalTransactionValue ?? 0 | currency: 'INR' : 'symbol' : '1.0-2' }}
        </span>
        <span class="stat-label">Total transaction value</span>
      </mat-card>
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">
          {{ summary()?.totalCreditConsumed ?? 0 | currency: 'INR' : 'symbol' : '1.0-2' }}
        </span>
        <span class="stat-label">Total credit consumed</span>
      </mat-card>
    </div>

    <mat-card appearance="outlined">
      <div class="filters">
        <mat-form-field appearance="outline" class="search" subscriptSizing="dynamic">
          <mat-label>Search</mat-label>
          <input matInput [formControl]="search" placeholder="Patient name or email" />
          <mat-icon matPrefix>search</mat-icon>
        </mat-form-field>

        @if (isAdmin) {
          <mat-form-field appearance="outline" class="select" subscriptSizing="dynamic">
            <mat-label>Practitioner</mat-label>
            <mat-select [formControl]="practitionerId">
              <mat-option value="">All</mat-option>
              @for (p of practitioners(); track p.id) {
                <mat-option [value]="p.id">{{ p.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }

        <mat-form-field appearance="outline" class="select" subscriptSizing="dynamic">
          <mat-label>Catalogue</mat-label>
          <mat-select [formControl]="catalogue">
            <mat-option value="">All</mat-option>
            @for (c of catalogueTypes; track c) {
              <mat-option [value]="c">{{ c | titlecase }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="select" subscriptSizing="dynamic">
          <mat-label>Payment type</mat-label>
          <mat-select [formControl]="paymentType">
            <mat-option value="">All</mat-option>
            @for (p of paymentTypes; track p) {
              <mat-option [value]="p">{{ p | titlecase }}</mat-option>
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
      <table mat-table [dataSource]="transactions()">
        <ng-container matColumnDef="invoice">
          <th mat-header-cell *matHeaderCellDef>Invoice</th>
          <td mat-cell *matCellDef="let t">
            <a class="invoice-link" (click)="openInvoice(t)">{{ t.invoiceNumber }}</a>
          </td>
        </ng-container>

        <ng-container matColumnDef="patient">
          <th mat-header-cell *matHeaderCellDef>Patient</th>
          <td mat-cell *matCellDef="let t">{{ t.patient.name }}</td>
        </ng-container>

        <ng-container matColumnDef="service">
          <th mat-header-cell *matHeaderCellDef>Service</th>
          <td mat-cell *matCellDef="let t">
            @if (t.serviceName) {
              <div class="service-cell">
                <span>{{ t.serviceName }}</span>
                <span class="service-meta">{{ t.catalogueType | titlecase }}</span>
              </div>
            } @else {
              <span class="muted">—</span>
            }
          </td>
        </ng-container>

        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let t">
            <span class="badge" [class]="'type-' + t.transactionType">
              {{ t.transactionType | titlecase }}
            </span>
          </td>
        </ng-container>

        <ng-container matColumnDef="paymentType">
          <th mat-header-cell *matHeaderCellDef>Payment</th>
          <td mat-cell *matCellDef="let t">
            <span class="badge" [class]="'pay-' + t.paymentType">
              {{ t.paymentType | titlecase }}
            </span>
          </td>
        </ng-container>

        <ng-container matColumnDef="amount">
          <th mat-header-cell *matHeaderCellDef>Amount</th>
          <td mat-cell *matCellDef="let t">
            {{ t.amountInr | currency: 'INR' : 'symbol' : '1.0-2' }}
            @if (t.creditCharged < 0) {
              <div class="credit-note">{{ t.creditCharged | currency: 'INR' : 'symbol' : '1.0-2' }} credit</div>
            }
          </td>
        </ng-container>

        <ng-container matColumnDef="practitioner">
          <th mat-header-cell *matHeaderCellDef>Recorded by</th>
          <td mat-cell *matCellDef="let t">{{ t.createdBy.name }}</td>
        </ng-container>

        <ng-container matColumnDef="date">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let t">{{ t.createdAt | date: 'mediumDate' }}</td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>

        <tr class="mat-mdc-row" *matNoDataRow>
          <td class="mat-mdc-cell no-data" [attr.colspan]="columns.length">
            @if (!loading()) {
              No transactions match the current filters.
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
    .summary-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
      margin-bottom: 16px;
    }
    .stat-card {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 4px;
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
      align-items: center;
      gap: 16px;
      padding: 16px;
      flex-wrap: wrap;
    }
    .search {
      width: 100%;
      max-width: 260px;
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
    .invoice-link {
      color: var(--primary);
      cursor: pointer;
      font-weight: 500;
    }
    .invoice-link:hover {
      text-decoration: underline;
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
    .credit-note {
      font-size: 0.75rem;
      color: var(--badge-red-fg);
    }
    .badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      white-space: nowrap;
    }
    .type-activation { background: var(--badge-green-bg); color: var(--badge-green-fg); }
    .type-deactivation { background: var(--badge-grey-bg); color: var(--badge-grey-fg); }
    .type-refund { background: var(--badge-red-bg); color: var(--badge-red-fg); }
    .pay-offline { background: var(--badge-blue-bg); color: var(--badge-blue-fg); }
    .pay-online { background: var(--badge-purple-bg); color: var(--badge-purple-fg); }
    .pay-credit { background: var(--badge-amber-bg); color: var(--badge-amber-fg); }
    .no-data {
      padding: 24px;
      text-align: center;
      color: var(--muted-foreground);
    }
  `,
})
export class TransactionsPageComponent {
  private readonly transactionsService = inject(TransactionsService);
  private readonly usersService = inject(UsersService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly isAdmin = inject(AuthService).isAdmin();
  protected readonly catalogueTypes = CATALOGUE_TYPES;
  protected readonly paymentTypes = PAYMENT_TYPES;
  protected readonly columns = this.isAdmin
    ? ['invoice', 'patient', 'service', 'type', 'paymentType', 'amount', 'practitioner', 'date']
    : ['invoice', 'patient', 'service', 'type', 'paymentType', 'amount', 'date'];

  protected readonly transactions = signal<TransactionListItem[]>([]);
  protected readonly summary = signal<TransactionTotals | null>(null);
  protected readonly practitioners = signal<UserDetail[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(1);
  protected readonly limit = signal(10);
  protected readonly loading = signal(false);

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly practitionerId = new FormControl('', { nonNullable: true });
  protected readonly catalogue = new FormControl<'' | ServiceType>('', { nonNullable: true });
  protected readonly paymentType = new FormControl<'' | PaymentType>('', { nonNullable: true });
  protected readonly dateFrom = new FormControl<Date | null>(null);
  protected readonly dateTo = new FormControl<Date | null>(null);

  constructor() {
    this.load();
    if (this.isAdmin) {
      this.usersService.list({ page: 1, limit: 500 }).subscribe((res) => {
        this.practitioners.set(
          res.data.filter((u) => u.role === 'ADMIN' || u.role === 'DOCTOR'),
        );
      });
    }
    merge(
      this.search.valueChanges.pipe(debounceTime(300)),
      this.practitionerId.valueChanges,
      this.catalogue.valueChanges,
      this.paymentType.valueChanges,
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
    this.transactionsService
      .list({
        search: this.search.value || undefined,
        userId: this.practitionerId.value || undefined,
        catalogue: (this.catalogue.value || undefined) as ServiceType | undefined,
        paymentType: (this.paymentType.value || undefined) as PaymentType | undefined,
        dateFrom: this.dateFrom.value ? toIsoDate(this.dateFrom.value) : undefined,
        dateTo: this.dateTo.value ? toIsoDate(this.dateTo.value) : undefined,
        page: this.page(),
        limit: this.limit(),
      })
      .subscribe({
        next: (result) => {
          this.transactions.set(result.data.transactions);
          this.summary.set(result.data.summary);
          this.total.set(result.meta?.total ?? result.data.transactions.length);
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

  protected openInvoice(tx: TransactionListItem): void {
    this.router.navigate(['/invoices', tx.id]);
  }

  protected openRecordTransaction(): void {
    this.dialog
      .open(RecordTransactionDialogComponent)
      .afterClosed()
      .subscribe((created) => created && this.load());
  }
}

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
