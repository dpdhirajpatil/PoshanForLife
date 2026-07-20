import { CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../core/models/api-response.model';
import { TransactionDetail, TransactionOrderProgramme } from '../../core/models/transaction.model';
import { amountInWords } from '../../core/utils/amount-in-words';
import { ToastService } from '../../core/services/toast.service';
import { TransactionsService } from './transactions.service';

/**
 * Printable invoice for one transaction — a standalone, unchromed route (no
 * sidenav/topbar) so `window.print()` output is just the invoice sheet.
 * Trade-off: client-side print CSS instead of a server-rendered PDF — zero
 * extra infra/dependencies and works offline, at the cost of pixel-perfect
 * consistency across browsers/printers. Good enough for an internal admin
 * tool; revisit with a PDF library if customers print these unattended.
 */
@Component({
  selector: 'app-invoice-page',
  standalone: true,
  imports: [
    CurrencyPipe,
    DatePipe,
    TitleCasePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-chrome">
      <a routerLink="/transactions">
        <mat-icon>arrow_back</mat-icon>
        Transactions
      </a>
      <button mat-flat-button color="primary" (click)="print()">
        <mat-icon>print</mat-icon>
        Print / Download PDF
      </button>
    </div>

    @if (transaction(); as tx) {
      <div class="invoice-sheet">
        <header class="invoice-header">
          <div class="company">
            <h1>Poshan for Life</h1>
            <p class="muted">Nutrition &amp; wellness services</p>
          </div>
          <div class="invoice-meta">
            <h2>Invoice</h2>
            <p><strong>{{ tx.invoiceNumber }}</strong></p>
            <p class="muted">{{ tx.createdAt | date: 'mediumDate' }}</p>
          </div>
        </header>

        <section class="bill-to">
          <h3>Bill to</h3>
          <p>{{ tx.patient.name }}</p>
          <p class="muted">{{ tx.patient.email }}</p>
          @if (tx.patient.phone) {
            <p class="muted">{{ tx.patient.phone }}</p>
          }
        </section>

        <table class="line-items">
          <thead>
            <tr>
              <th>Service</th>
              <th>Duration</th>
              <th>Type</th>
              <th class="num">Price</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>
                {{ pp?.catalogueItem?.name ?? '—' }}
                @if (pp?.catalogueItem?.serviceCode) {
                  <div class="muted small">{{ pp!.catalogueItem!.serviceCode }}</div>
                }
              </td>
              <td>{{ duration(pp) }}</td>
              <td>{{ tx.transactionType | titlecase }}</td>
              <td class="num">{{ tx.priceInr | currency: 'INR' : 'symbol' : '1.0-2' }}</td>
            </tr>
          </tbody>
        </table>

        <section class="breakdown">
          <div class="row">
            <span>Price</span>
            <span>{{ tx.priceInr | currency: 'INR' : 'symbol' : '1.0-2' }}</span>
          </div>
          <div class="row">
            <span>Discount</span>
            <span>− {{ tx.discountInr | currency: 'INR' : 'symbol' : '1.0-2' }}</span>
          </div>
          @if (tx.creditCharged < 0) {
            <div class="row">
              <span>Credit applied</span>
              <span>{{ tx.creditCharged | currency: 'INR' : 'symbol' : '1.0-2' }}</span>
            </div>
          }
          <div class="row total">
            <span>Amount payable</span>
            <span>{{ tx.amountInr | currency: 'INR' : 'symbol' : '1.0-2' }}</span>
          </div>
        </section>

        <p class="amount-words">{{ words(tx.amountInr) }}</p>

        <section class="payment-info">
          <span class="badge" [class]="'type-' + tx.transactionType">
            {{ tx.transactionType | titlecase }}
          </span>
          <span class="badge" [class]="'pay-' + tx.paymentType">
            {{ tx.paymentType | titlecase }} payment
          </span>
          @if (pp?.assignedBy) {
            <span class="muted">Assigned by {{ pp!.assignedBy!.name }}</span>
          }
        </section>

        @if (tx.notes) {
          <section class="notes">
            <h3>Notes</h3>
            <p>{{ tx.notes }}</p>
          </section>
        }

        <footer class="invoice-footer">
          <p>Transaction ID: {{ tx.transactionId }}</p>
          <p>This is a system-generated invoice for Poshan for Life services.</p>
        </footer>
      </div>
    } @else if (loading()) {
      <div class="center"><mat-spinner diameter="40" /></div>
    }
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
      background: #f5f5f7;
      padding: 24px;
    }
    .page-chrome {
      display: flex;
      align-items: center;
      justify-content: space-between;
      max-width: 800px;
      margin: 0 auto 16px;
    }
    .page-chrome a {
      display: flex;
      align-items: center;
      gap: 4px;
      color: inherit;
      text-decoration: none;
    }
    .center {
      display: grid;
      place-content: center;
      padding: 64px;
    }
    .invoice-sheet {
      max-width: 800px;
      margin: 0 auto;
      background: white;
      padding: 48px;
      border-radius: 8px;
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
    }
    .invoice-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      border-bottom: 2px solid #222;
      padding-bottom: 16px;
      margin-bottom: 24px;
    }
    .company h1 {
      margin: 0;
      font-size: 1.4rem;
    }
    .invoice-meta {
      text-align: right;
    }
    .invoice-meta h2 {
      margin: 0 0 4px;
      font-size: 1.1rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .invoice-meta p {
      margin: 2px 0;
    }
    .muted {
      color: rgba(0, 0, 0, 0.6);
    }
    .small {
      font-size: 0.8rem;
    }
    .bill-to {
      margin-bottom: 24px;
    }
    .bill-to h3 {
      margin: 0 0 6px;
      font-size: 0.85rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: rgba(0, 0, 0, 0.6);
    }
    .bill-to p {
      margin: 2px 0;
    }
    .line-items {
      width: 100%;
      border-collapse: collapse;
      margin-bottom: 24px;
    }
    .line-items th {
      text-align: left;
      font-size: 0.8rem;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      color: rgba(0, 0, 0, 0.6);
      border-bottom: 1px solid rgba(0, 0, 0, 0.15);
      padding: 8px 4px;
    }
    .line-items td {
      padding: 12px 4px;
      border-bottom: 1px solid rgba(0, 0, 0, 0.08);
    }
    .num {
      text-align: right;
    }
    .breakdown {
      max-width: 320px;
      margin-left: auto;
      margin-bottom: 16px;
    }
    .breakdown .row {
      display: flex;
      justify-content: space-between;
      padding: 4px 0;
    }
    .breakdown .total {
      border-top: 1px solid rgba(0, 0, 0, 0.2);
      margin-top: 6px;
      padding-top: 10px;
      font-weight: 700;
      font-size: 1.1rem;
    }
    .amount-words {
      font-style: italic;
      color: rgba(0, 0, 0, 0.75);
      margin: 16px 0 24px;
    }
    .payment-info {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 16px;
    }
    .badge {
      padding: 3px 12px;
      border-radius: 12px;
      font-size: 0.8rem;
      white-space: nowrap;
    }
    .type-activation { background: #e8f5e9; color: #2e7d32; }
    .type-deactivation { background: #eceff1; color: #455a64; }
    .type-refund { background: #fbe9e7; color: #c62828; }
    .pay-offline { background: #e3f2fd; color: #1565c0; }
    .pay-online { background: #ede7f6; color: #5e35b1; }
    .pay-credit { background: #fff8e1; color: #b26a00; }
    .notes {
      margin-bottom: 16px;
    }
    .notes h3 {
      margin: 0 0 4px;
      font-size: 0.85rem;
      text-transform: uppercase;
      color: rgba(0, 0, 0, 0.6);
    }
    .invoice-footer {
      border-top: 1px solid rgba(0, 0, 0, 0.1);
      padding-top: 12px;
      margin-top: 24px;
      font-size: 0.8rem;
      color: rgba(0, 0, 0, 0.55);
    }
    .invoice-footer p {
      margin: 2px 0;
    }

    @media print {
      .page-chrome {
        display: none;
      }
      :host {
        background: white;
        padding: 0;
      }
      .invoice-sheet {
        box-shadow: none;
        border-radius: 0;
        max-width: none;
        padding: 0;
      }
    }
  `,
})
export class InvoicePageComponent implements OnInit {
  /** Bound from the :transactionId route param via withComponentInputBinding. */
  readonly transactionId = input.required<string>();

  private readonly transactionsService = inject(TransactionsService);
  private readonly toast = inject(ToastService);

  protected readonly transaction = signal<TransactionDetail | null>(null);
  protected readonly loading = signal(true);

  protected get pp(): TransactionOrderProgramme | undefined {
    return this.transaction()?.order?.patientProgramme;
  }

  ngOnInit(): void {
    this.transactionsService.get(this.transactionId()).subscribe({
      next: (tx) => {
        this.transaction.set(tx);
        this.loading.set(false);
      },
      error: (err: ApiError) => {
        this.loading.set(false);
        this.toast.error(err.error);
      },
    });
  }

  protected duration(pp?: TransactionOrderProgramme): string {
    const item = pp?.catalogueItem;
    if (!item) return '—';
    if (item.durationWeeks != null) return `${item.durationWeeks} week(s)`;
    if (item.durationMinutes != null) return `${item.durationMinutes} minute(s)`;
    if (item.durationDays != null) return `${item.durationDays} day(s)`;
    return '—';
  }

  protected words(amount: number): string {
    return amountInWords(amount);
  }

  protected print(): void {
    window.print();
  }
}
