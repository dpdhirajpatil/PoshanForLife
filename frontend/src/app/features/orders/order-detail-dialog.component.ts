import { CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiError } from '../../core/models/api-response.model';
import { OrderDetail } from '../../core/models/order.model';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { OrdersService } from './orders.service';

/**
 * Full order context: bill-to patient, the originating assignment (service +
 * duration + who assigned it), ledger entries, and the mark-as-paid action.
 * Closes with the updated order when a change was made so the list refreshes.
 */
@Component({
  selector: 'app-order-detail-dialog',
  standalone: true,
  imports: [
    CurrencyPipe,
    DatePipe,
    TitleCasePipe,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Order detail</h2>

    <mat-dialog-content>
      @if (order(); as o) {
        <div class="detail">
          <div class="row">
            <span class="label">Patient</span>
            <span>
              {{ o.patient.name }}
              <span class="muted">· {{ o.patient.email }}</span>
              @if (o.patient.phone) {
                <span class="muted">· {{ o.patient.phone }}</span>
              }
            </span>
          </div>

          @if (o.patientProgramme; as pp) {
            <div class="row">
              <span class="label">Service</span>
              <span>
                {{ pp.catalogueItem?.name ?? 'Deleted item' }}
                <span class="muted">
                  ({{ pp.serviceType | titlecase }}
                  @if (pp.catalogueItem) {
                    · {{ pp.catalogueItem.serviceCode }}
                  }
                  @if (duration(pp.catalogueItem)) {
                    · {{ duration(pp.catalogueItem) }}
                  })
                </span>
              </span>
            </div>
            <div class="row">
              <span class="label">Period</span>
              <span>
                {{ pp.startDate | date: 'mediumDate' }}
                @if (pp.endDate && pp.endDate !== pp.startDate) {
                  → {{ pp.endDate | date: 'mediumDate' }}
                }
                <span class="muted">· assignment {{ pp.status }}</span>
              </span>
            </div>
            @if (pp.assignedBy || pp.assignedDoctor) {
              <div class="row">
                <span class="label">Assigned</span>
                <span>
                  @if (pp.assignedBy) {
                    by {{ pp.assignedBy.name }}
                  }
                  @if (pp.assignedDoctor) {
                    <span class="muted">· doctor {{ pp.assignedDoctor.name }}</span>
                  }
                </span>
              </div>
            }
          } @else {
            <div class="row">
              <span class="label">Service</span>
              <span class="muted">Assignment no longer exists</span>
            </div>
          }

          <div class="row">
            <span class="label">Amount</span>
            <span class="amount">{{ o.amountInr | currency: 'INR' : 'symbol' : '1.0-2' }}</span>
          </div>
          <div class="row">
            <span class="label">Status</span>
            <span>
              <span class="badge" [class]="'status-' + o.status">{{ o.status | titlecase }}</span>
              <span class="badge" [class]="'payment-' + o.paymentStatus">
                {{ o.paymentStatus | titlecase }}
              </span>
            </span>
          </div>

          @for (tx of o.transactions; track tx.id) {
            <div class="tx">
              <mat-icon>receipt_long</mat-icon>
              <div>
                <div>
                  <strong>{{ tx.invoiceNumber }}</strong>
                  <span class="muted"> · {{ tx.transactionType }} · {{ tx.paymentType }}</span>
                </div>
                <div class="muted small">{{ tx.transactionId }}</div>
                <div class="small">
                  {{ tx.amountInr | currency: 'INR' : 'symbol' : '1.0-2' }} ·
                  {{ tx.createdAt | date: 'medium' }}
                </div>
              </div>
            </div>
          } @empty {
            <p class="muted small">No transactions recorded for this order yet.</p>
          }

          @if (o.notes) {
            <div class="row">
              <span class="label">Notes</span>
              <span>{{ o.notes }}</span>
            </div>
          }
          <div class="row">
            <span class="label">Created</span>
            <span>
              {{ o.createdAt | date: 'medium' }}
              @if (o.createdBy) {
                <span class="muted">by {{ o.createdBy.name }}</span>
              }
            </span>
          </div>
        </div>
      } @else if (loading()) {
        <div class="center"><mat-spinner diameter="32" /></div>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close(changed() ? order() : undefined)">Close</button>
      @if (order()?.paymentStatus !== 'paid' && !loading()) {
        <button mat-flat-button color="primary" (click)="markPaid()" [disabled]="saving()">
          @if (saving()) {
            <mat-spinner diameter="18" />
          } @else {
            Mark as paid
          }
        </button>
      }
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
      max-width: 480px;
    }
    .row {
      display: flex;
      gap: 12px;
    }
    .label {
      width: 90px;
      flex-shrink: 0;
      font-size: 0.82rem;
      color: var(--muted-foreground);
      padding-top: 1px;
    }
    .amount {
      font-weight: 600;
    }
    .muted {
      color: var(--muted-foreground);
    }
    .small {
      font-size: 0.82rem;
    }
    .badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      margin-right: 6px;
    }
    .status-active { background: var(--badge-green-bg); color: var(--badge-green-fg); }
    .status-completed { background: var(--badge-blue-bg); color: var(--badge-blue-fg); }
    .status-deactivated { background: var(--badge-grey-bg); color: var(--badge-grey-fg); }
    .payment-paid { background: var(--badge-green-bg); color: var(--badge-green-fg); }
    .payment-pending { background: var(--badge-amber-bg); color: var(--badge-amber-fg); }
    .payment-unpaid { background: var(--badge-red-bg); color: var(--badge-red-fg); }
    .tx {
      display: flex;
      gap: 10px;
      align-items: flex-start;
      background: var(--muted);
      border-radius: 8px;
      padding: 10px 12px;
    }
    .tx mat-icon {
      color: var(--muted-foreground);
    }
  `,
})
export class OrderDetailDialogComponent {
  protected readonly ref = inject(MatDialogRef<OrderDetailDialogComponent>);
  private readonly orderId = inject<string>(MAT_DIALOG_DATA);
  private readonly ordersService = inject(OrdersService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly changed = signal(false);

  constructor() {
    this.ordersService.get(this.orderId).subscribe({
      next: (order) => {
        this.order.set(order);
        this.loading.set(false);
      },
      error: (err: ApiError) => {
        this.loading.set(false);
        this.toast.error(err.error);
        this.ref.close();
      },
    });
  }

  protected duration(item?: { durationWeeks?: number; durationMinutes?: number; durationDays?: number }): string {
    if (!item) return '';
    if (item.durationWeeks != null) return `${item.durationWeeks} wk`;
    if (item.durationMinutes != null) return `${item.durationMinutes} min`;
    if (item.durationDays != null) return `${item.durationDays} days`;
    return '';
  }

  protected markPaid(): void {
    const current = this.order();
    if (!current || this.saving()) return;
    const willInvoice = current.transactions.length === 0 && current.amountInr > 0;
    const data: ConfirmDialogData = {
      title: 'Mark order as paid',
      message:
        `${current.patient.name}'s order of ` +
        `₹${current.amountInr} will be marked paid.` +
        (willInvoice
          ? ' An activation transaction with a new invoice number will be generated.'
          : ''),
      confirmLabel: 'Mark as paid',
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.saving.set(true);
        this.ordersService.update(current.id, { paymentStatus: 'paid' }).subscribe({
          next: (updated) => {
            this.saving.set(false);
            this.changed.set(true);
            this.order.set(updated);
            const invoice = updated.transactions[0]?.invoiceNumber;
            this.toast.success(
              invoice ? `Order marked paid — invoice ${invoice}` : 'Order marked paid',
            );
          },
          error: (err: ApiError) => {
            this.saving.set(false);
            this.toast.error(err.error);
          },
        });
      });
  }
}
