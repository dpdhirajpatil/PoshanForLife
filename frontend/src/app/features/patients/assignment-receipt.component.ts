import { CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { PatientProgramme } from '../../core/models/patient-programme.model';

/**
 * Receipt-style summary of an assignment's commercial record: the order plus
 * any ledger entries (invoice number, TRNID). Shown right after assigning a
 * service and from the "View order" action on the programmes tab.
 */
@Component({
  selector: 'app-assignment-receipt',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, TitleCasePipe, MatIconModule],
  template: `
    @if (assignment(); as a) {
      <div class="receipt">
        <div class="receipt-row">
          <span class="label">Service</span>
          <span>
            {{ a.catalogueItem?.name ?? 'Deleted item' }}
            <span class="muted">({{ a.serviceType | titlecase }}</span>
            @if (a.catalogueItem?.serviceCode) {
              <span class="muted"> · {{ a.catalogueItem?.serviceCode }}</span>
            }
            <span class="muted">)</span>
          </span>
        </div>
        <div class="receipt-row">
          <span class="label">Period</span>
          <span>
            {{ a.startDate | date: 'mediumDate' }}
            @if (a.endDate && a.endDate !== a.startDate) {
              → {{ a.endDate | date: 'mediumDate' }}
            } @else {
              <span class="muted">(single day)</span>
            }
          </span>
        </div>
        <div class="receipt-row">
          <span class="label">Amount</span>
          <span class="amount">{{ a.priceInr | currency: 'INR' : 'symbol' : '1.0-2' }}</span>
        </div>
        @if (a.order; as order) {
          <div class="receipt-row">
            <span class="label">Order</span>
            <span>
              {{ order.status | titlecase }} ·
              payment {{ order.paymentStatus }}
            </span>
          </div>
          @for (tx of order.transactions; track tx.id) {
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
            <p class="muted small">No transactions yet — a free service creates no ledger entry.</p>
          }
        }
        @if (a.notes) {
          <div class="receipt-row">
            <span class="label">Notes</span>
            <span>{{ a.notes }}</span>
          </div>
        }
      </div>
    }
  `,
  styles: `
    .receipt {
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 12px 0;
    }
    .receipt-row {
      display: flex;
      gap: 12px;
    }
    .label {
      width: 90px;
      flex-shrink: 0;
      font-size: 0.82rem;
      color: rgba(0, 0, 0, 0.6);
      padding-top: 1px;
    }
    .amount {
      font-weight: 600;
    }
    .muted {
      color: rgba(0, 0, 0, 0.55);
    }
    .small {
      font-size: 0.82rem;
    }
    .tx {
      display: flex;
      gap: 10px;
      align-items: flex-start;
      background: rgba(0, 0, 0, 0.03);
      border-radius: 8px;
      padding: 10px 12px;
    }
    .tx mat-icon {
      color: rgba(0, 0, 0, 0.45);
    }
  `,
})
export class AssignmentReceiptComponent {
  readonly assignment = input.required<PatientProgramme | null>();
}
