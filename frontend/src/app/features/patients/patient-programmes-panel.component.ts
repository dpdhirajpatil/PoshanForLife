import { CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiError } from '../../core/models/api-response.model';
import {
  PatientProgramme,
  hasNonRefundTransaction,
} from '../../core/models/patient-programme.model';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import {
  AssignServiceDialogComponent,
  AssignServiceDialogData,
} from './assign-service-dialog.component';
import { AssignmentReceiptComponent } from './assignment-receipt.component';
import {
  EditAssignmentDialogComponent,
  EditAssignmentDialogData,
} from './edit-assignment-dialog.component';
import { PatientProgrammesService } from './patient-programmes.service';

/** The Programmes tab: all past/current service assignments for a patient. */
@Component({
  selector: 'app-patient-programmes-panel',
  standalone: true,
  imports: [
    CurrencyPipe,
    DatePipe,
    TitleCasePipe,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatDialogModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  template: `
    <mat-card appearance="outlined">
      <div class="panel-header">
        <h3>Service assignments</h3>
        <button mat-flat-button color="primary" (click)="openAssign()">
          <mat-icon>medical_services</mat-icon>
          Assign a service
        </button>
      </div>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <div class="table-scroll">
      <table mat-table [dataSource]="assignments()">
        <ng-container matColumnDef="service">
          <th mat-header-cell *matHeaderCellDef>Service</th>
          <td mat-cell *matCellDef="let a">
            <div class="service-cell">
              <span class="service-name">{{ a.catalogueItem?.name ?? 'Deleted item' }}</span>
              <span class="service-meta">
                {{ a.serviceType | titlecase }}
                @if (a.catalogueItem?.serviceCode) {
                  · {{ a.catalogueItem.serviceCode }}
                }
              </span>
            </div>
          </td>
        </ng-container>

        <ng-container matColumnDef="period">
          <th mat-header-cell *matHeaderCellDef>Period</th>
          <td mat-cell *matCellDef="let a">
            {{ a.startDate | date: 'mediumDate' }}
            @if (a.endDate && a.endDate !== a.startDate) {
              <span class="muted">→ {{ a.endDate | date: 'mediumDate' }}</span>
            }
          </td>
        </ng-container>

        <ng-container matColumnDef="price">
          <th mat-header-cell *matHeaderCellDef>Price</th>
          <td mat-cell *matCellDef="let a">
            {{ a.priceInr | currency: 'INR' : 'symbol' : '1.0-2' }}
          </td>
        </ng-container>

        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let a">
            <span class="badge" [class]="'assignment-' + a.status">{{ a.status | titlecase }}</span>
          </td>
        </ng-container>

        <ng-container matColumnDef="payment">
          <th mat-header-cell *matHeaderCellDef>Order</th>
          <td mat-cell *matCellDef="let a">
            @if (a.order; as order) {
              <button
                class="order-link"
                (click)="openReceipt(a)"
                matTooltip="View order & invoice"
              >
                <span class="badge" [class]="'payment-' + order.paymentStatus">
                  {{ order.paymentStatus | titlecase }}
                </span>
                @if (order.transactions.length) {
                  <span class="invoice">{{ order.transactions[0].invoiceNumber }}</span>
                }
              </button>
            } @else {
              <span class="muted">—</span>
            }
          </td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let a">
            <button
              mat-icon-button
              [matMenuTriggerFor]="rowMenu"
              [matMenuTriggerData]="{ assignment: a }"
              aria-label="Assignment actions"
            >
              <mat-icon>more_vert</mat-icon>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>

        <tr class="mat-mdc-row" *matNoDataRow>
          <td class="mat-mdc-cell no-data" [attr.colspan]="columns.length">
            @if (!loading()) {
              No services assigned yet.
            }
          </td>
        </tr>
      </table>
      </div>
    </mat-card>

    <mat-menu #rowMenu="matMenu">
      <ng-template matMenuContent let-assignment="assignment">
        <button mat-menu-item (click)="openReceipt(assignment)">
          <mat-icon>receipt_long</mat-icon>
          View order & invoice
        </button>
        <button mat-menu-item (click)="openEdit(assignment)">
          <mat-icon>edit</mat-icon>
          Edit status / dates / notes
        </button>
        @if (assignment.status === 'active') {
          <button mat-menu-item (click)="cancel(assignment)">
            <mat-icon>cancel</mat-icon>
            Cancel assignment
          </button>
        }
        <span
          [matTooltip]="deleteBlockedReason"
          [matTooltipDisabled]="!isDeleteBlocked(assignment)"
        >
          <button
            mat-menu-item
            [disabled]="isDeleteBlocked(assignment)"
            (click)="confirmDelete(assignment)"
          >
            <mat-icon>delete_outline</mat-icon>
            Delete
          </button>
        </span>
      </ng-template>
    </mat-menu>
  `,
  styles: `
    .panel-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px;
    }
    .panel-header h3 {
      margin: 0;
      font-size: 1.05rem;
    }
    table {
      width: 100%;
    }
    .service-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .service-name {
      font-weight: 500;
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
    .assignment-active {
      background: var(--badge-green-bg);
      color: var(--badge-green-fg);
    }
    .assignment-completed {
      background: var(--badge-blue-bg);
      color: var(--badge-blue-fg);
    }
    .assignment-cancelled {
      background: var(--badge-red-bg);
      color: var(--badge-red-fg);
    }
    .payment-paid {
      background: var(--badge-green-bg);
      color: var(--badge-green-fg);
    }
    .payment-pending {
      background: var(--badge-amber-bg);
      color: var(--badge-amber-fg);
    }
    .payment-unpaid {
      background: var(--badge-red-bg);
      color: var(--badge-red-fg);
    }
    .order-link {
      display: flex;
      align-items: center;
      gap: 8px;
      background: none;
      border: none;
      padding: 0;
      cursor: pointer;
      font: inherit;
    }
    .invoice {
      font-size: 0.8rem;
      color: var(--muted-foreground);
    }
    .order-link:hover .invoice {
      text-decoration: underline;
    }
    .no-data {
      padding: 24px;
      text-align: center;
      color: var(--muted-foreground);
    }
  `,
})
export class PatientProgrammesPanelComponent implements OnInit {
  readonly patientId = input.required<string>();
  readonly patientName = input.required<string>();

  private readonly programmesService = inject(PatientProgrammesService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  protected readonly assignments = signal<PatientProgramme[]>([]);
  protected readonly loading = signal(false);
  protected readonly columns = ['service', 'period', 'price', 'status', 'payment', 'actions'];

  protected readonly deleteBlockedReason =
    'A payment transaction has been recorded — record a refund via the Transactions flow instead of deleting.';

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.programmesService.list(this.patientId()).subscribe({
      next: (assignments) => {
        this.assignments.set(assignments);
        this.loading.set(false);
      },
      error: (err: ApiError) => {
        this.loading.set(false);
        this.toast.error(err.error);
      },
    });
  }

  protected isDeleteBlocked(assignment: PatientProgramme): boolean {
    return hasNonRefundTransaction(assignment);
  }

  protected openAssign(): void {
    this.dialog
      .open(AssignServiceDialogComponent, {
        data: {
          patientId: this.patientId(),
          patientName: this.patientName(),
        } satisfies AssignServiceDialogData,
      })
      .afterClosed()
      .subscribe((created) => created && this.load());
  }

  protected openReceipt(assignment: PatientProgramme): void {
    this.dialog.open(AssignmentReceiptDialogComponent, { data: assignment });
  }

  protected openEdit(assignment: PatientProgramme): void {
    this.dialog
      .open(EditAssignmentDialogComponent, {
        data: {
          patientId: this.patientId(),
          assignment,
        } satisfies EditAssignmentDialogData,
      })
      .afterClosed()
      .subscribe((updated) => updated && this.load());
  }

  protected cancel(assignment: PatientProgramme): void {
    const data: ConfirmDialogData = {
      title: 'Cancel assignment',
      message:
        `"${assignment.catalogueItem?.name ?? 'This service'}" will be marked cancelled for ` +
        `${this.patientName()}. The order and any recorded transactions are kept.`,
      confirmLabel: 'Cancel assignment',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.programmesService
          .update(this.patientId(), assignment.id, { status: 'cancelled' })
          .subscribe({
            next: () => {
              this.toast.success('Assignment cancelled');
              this.load();
            },
            error: (err: ApiError) => this.toast.error(err.error),
          });
      });
  }

  protected confirmDelete(assignment: PatientProgramme): void {
    const data: ConfirmDialogData = {
      title: 'Delete assignment',
      message:
        `"${assignment.catalogueItem?.name ?? 'This service'}" and its order will be permanently ` +
        `removed for ${this.patientName()}.`,
      confirmLabel: 'Delete',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.programmesService.remove(this.patientId(), assignment.id).subscribe({
          next: () => {
            this.toast.success('Assignment deleted');
            this.load();
          },
          error: (err: ApiError) => {
            this.toast.error(err.error);
            this.load();
          },
        });
      });
  }
}

/** Small wrapper dialog around the shared receipt component. */
@Component({
  selector: 'app-assignment-receipt-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, AssignmentReceiptComponent],
  template: `
    <h2 mat-dialog-title>Order & invoice</h2>
    <mat-dialog-content>
      <app-assignment-receipt [assignment]="assignment" />
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
})
export class AssignmentReceiptDialogComponent {
  protected readonly assignment = inject<PatientProgramme>(MAT_DIALOG_DATA);
}
