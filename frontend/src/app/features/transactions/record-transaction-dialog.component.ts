import { CurrencyPipe, TitleCasePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import { OrderListItem } from '../../core/models/order.model';
import { PaymentType, TransactionType } from '../../core/models/patient-programme.model';
import { ToastService } from '../../core/services/toast.service';
import { SidePanelHandleComponent } from '../../shared/side-panel-handle.component';
import { OrdersService } from '../orders/orders.service';
import { CreateTransactionPayload, TransactionsService } from './transactions.service';

const TRANSACTION_TYPES: TransactionType[] = ['activation', 'deactivation', 'refund'];
const PAYMENT_TYPES: PaymentType[] = ['offline', 'online', 'credit'];

/**
 * ADMIN-only manual ledger entry against an existing order: search-and-pick
 * the order, then transaction type / payment type / amount / discount /
 * gateway ref / notes. This is also how a blocked assignment deletion gets
 * unblocked in spirit — recording a refund here is "the Transactions refund
 * flow" the delete-guard error message points to.
 */
@Component({
  selector: 'app-record-transaction-dialog',
  standalone: true,
  imports: [
    CurrencyPipe,
    TitleCasePipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    SidePanelHandleComponent,
  ],
  template: `
    <app-side-panel-handle />
    <h2 mat-dialog-title>Record manual transaction</h2>

    <mat-dialog-content>
      @if (!selectedOrder(); as _) {
        <mat-form-field appearance="outline" class="full" subscriptSizing="dynamic">
          <mat-label>Search orders by patient</mat-label>
          <input matInput [formControl]="search" placeholder="Patient name or email" />
          <mat-icon matPrefix>search</mat-icon>
        </mat-form-field>

        @if (loadingOrders()) {
          <mat-progress-bar mode="indeterminate" />
        }

        <div class="order-list" role="listbox" aria-label="Orders">
          @for (order of orders(); track order.id) {
            <button type="button" class="order-row" (click)="selectOrder(order)">
              <div class="order-main">
                <span class="order-patient">{{ order.patient.name }}</span>
                <span class="order-meta">
                  {{ order.serviceName ?? 'No linked service' }}
                  @if (order.serviceType) {
                    · {{ order.serviceType | titlecase }}
                  }
                  · payment {{ order.paymentStatus }}
                </span>
              </div>
              <span class="order-amount">{{ order.amountInr | currency: 'INR' : 'symbol' : '1.0-2' }}</span>
            </button>
          } @empty {
            @if (!loadingOrders() && search.value) {
              <p class="muted">No orders match "{{ search.value }}".</p>
            }
          }
        </div>
      } @else {
        <div class="selected-order">
          <div>
            <strong>{{ selectedOrder()!.patient.name }}</strong>
            <span class="muted">
              — {{ selectedOrder()!.serviceName ?? 'No linked service' }} ·
              {{ selectedOrder()!.amountInr | currency: 'INR' : 'symbol' : '1.0-2' }}
            </span>
          </div>
          <button mat-button type="button" (click)="selectedOrder.set(null)">Change</button>
        </div>

        <form [formGroup]="form" class="grid">
          <mat-form-field appearance="outline">
            <mat-label>Transaction type</mat-label>
            <mat-select formControlName="transactionType">
              @for (t of transactionTypes; track t) {
                <mat-option [value]="t">{{ t | titlecase }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Payment type</mat-label>
            <mat-select formControlName="paymentType">
              @for (t of paymentTypes; track t) {
                <mat-option [value]="t">{{ t | titlecase }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Amount (INR)</mat-label>
            <input matInput formControlName="amountInr" type="number" min="0" step="0.01" />
            <span matTextPrefix>₹&nbsp;</span>
            @if (form.controls.amountInr.hasError('server')) {
              <mat-error>{{ form.controls.amountInr.getError('server') }}</mat-error>
            } @else if (form.controls.amountInr.invalid) {
              <mat-error>Amount must be 0 or more</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Discount (INR, optional)</mat-label>
            <input matInput formControlName="discountInr" type="number" min="0" step="0.01" />
            <span matTextPrefix>₹&nbsp;</span>
          </mat-form-field>

          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Payment gateway reference (optional)</mat-label>
            <input matInput formControlName="paymentGatewayRef" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Notes (optional)</mat-label>
            <textarea matInput formControlName="notes" rows="2"></textarea>
          </mat-form-field>
        </form>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()" [disabled]="saving()">Cancel</button>
      @if (selectedOrder()) {
        <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
          @if (saving()) {
            <mat-spinner diameter="18" />
          } @else {
            Record transaction
          }
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      width: 100%;
    }
    .full {
      width: 100%;
    }
    .order-list {
      display: flex;
      flex-direction: column;
      gap: 6px;
      max-height: 280px;
      overflow-y: auto;
      margin-top: 10px;
    }
    .order-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 10px 12px;
      border: 1px solid var(--border);
      border-radius: 8px;
      background: none;
      cursor: pointer;
      text-align: left;
      font: inherit;
    }
    .order-row:hover {
      background: var(--muted);
    }
    .order-main {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .order-patient {
      font-weight: 500;
    }
    .order-meta {
      font-size: 0.8rem;
      color: var(--muted-foreground);
    }
    .order-amount {
      font-weight: 600;
      white-space: nowrap;
    }
    .selected-order {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 12px;
      border-radius: 8px;
      background: var(--primary-tint);
      margin-bottom: 12px;
    }
    .muted {
      color: var(--muted-foreground);
    }
    .grid {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .span-2 {
      width: 100%;
    }
  `,
})
export class RecordTransactionDialogComponent {
  protected readonly ref = inject(MatDialogRef<RecordTransactionDialogComponent>);
  private readonly ordersService = inject(OrdersService);
  private readonly transactionsService = inject(TransactionsService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly orders = signal<OrderListItem[]>([]);
  protected readonly loadingOrders = signal(false);
  protected readonly selectedOrder = signal<OrderListItem | null>(null);
  protected readonly saving = signal(false);

  protected readonly transactionTypes = TRANSACTION_TYPES;
  protected readonly paymentTypes = PAYMENT_TYPES;

  protected readonly form = this.fb.nonNullable.group({
    transactionType: ['activation' as TransactionType, Validators.required],
    paymentType: ['offline' as PaymentType, Validators.required],
    amountInr: [null as number | null, [Validators.required, Validators.min(0)]],
    discountInr: [null as number | null, Validators.min(0)],
    paymentGatewayRef: [''],
    notes: [''],
  });

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((term) => this.loadOrders(term));
  }

  protected selectOrder(order: OrderListItem): void {
    this.selectedOrder.set(order);
    this.form.controls.amountInr.setValue(order.amountInr);
  }

  protected save(): void {
    const order = this.selectedOrder();
    if (!order || this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const payload: CreateTransactionPayload = {
      orderId: order.id,
      transactionType: v.transactionType,
      amountInr: v.amountInr!,
      discountInr: v.discountInr ?? undefined,
      paymentType: v.paymentType,
      paymentGatewayRef: v.paymentGatewayRef || undefined,
      notes: v.notes || undefined,
    };

    this.saving.set(true);
    this.transactionsService.create(payload).subscribe({
      next: (tx) => {
        this.toast.success(`Transaction recorded — invoice ${tx.invoiceNumber}`);
        this.ref.close(tx);
      },
      error: (err: ApiError) => {
        this.saving.set(false);
        if (err.code === 'VALIDATION_ERROR' && typeof err.details === 'object' && err.details) {
          const message = (err.details as Record<string, unknown>)['amountInr'];
          if (message) {
            this.form.controls.amountInr.setErrors({ server: String(message) });
          }
        }
        this.toast.error(err.error);
      },
    });
  }

  private loadOrders(search: string): void {
    if (!search) {
      this.orders.set([]);
      return;
    }
    this.loadingOrders.set(true);
    this.ordersService.list({ search, page: 1, limit: 20 }).subscribe({
      next: (result) => {
        this.orders.set(result.data);
        this.loadingOrders.set(false);
      },
      error: (err: ApiError) => {
        this.loadingOrders.set(false);
        this.toast.error(err.error);
      },
    });
  }
}
