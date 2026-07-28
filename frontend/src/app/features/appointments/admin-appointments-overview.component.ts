import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, merge } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import {
  APPOINTMENT_STATUSES,
  APPOINTMENT_STATUS_LABELS,
  Appointment,
  AppointmentStatus,
} from '../../core/models/appointment.model';
import { UserDetail } from '../../core/models/user.model';
import { ToastService } from '../../core/services/toast.service';
import { UsersService } from '../users/users.service';
import { AppointmentDetailDialogComponent } from './appointment-detail-dialog.component';
import { AppointmentsService } from './appointments.service';

/** Org-wide appointments, filterable by practitioner/date/status — ADMIN only (DOCTOR gets the schedule view instead). */
@Component({
  selector: 'app-admin-appointments-overview',
  standalone: true,
  imports: [
    DatePipe,
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
    <mat-card appearance="outlined">
      <div class="filters">
        <mat-form-field appearance="outline" class="select" subscriptSizing="dynamic">
          <mat-label>Practitioner</mat-label>
          <mat-select [formControl]="practitionerId">
            <mat-option value="">All</mat-option>
            @for (d of practitioners(); track d.id) {
              <mat-option [value]="d.id">{{ d.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="select" subscriptSizing="dynamic">
          <mat-label>Status</mat-label>
          <mat-select [formControl]="status">
            <mat-option value="">All</mat-option>
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ statusLabel(s) }}</mat-option>
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
        <table mat-table [dataSource]="appointments()">
          <ng-container matColumnDef="scheduledAt">
            <th mat-header-cell *matHeaderCellDef>When</th>
            <td mat-cell *matCellDef="let a">
              {{ a.scheduledAt | date: 'medium' }}
              @if (a.isVideo) {
                <mat-icon inline class="video-icon" matTooltip="Video appointment">videocam</mat-icon>
              }
            </td>
          </ng-container>

          <ng-container matColumnDef="patient">
            <th mat-header-cell *matHeaderCellDef>Patient</th>
            <td mat-cell *matCellDef="let a">{{ a.patient.name }}</td>
          </ng-container>

          <ng-container matColumnDef="practitioner">
            <th mat-header-cell *matHeaderCellDef>Practitioner</th>
            <td mat-cell *matCellDef="let a">{{ a.practitioner.name }}</td>
          </ng-container>

          <ng-container matColumnDef="duration">
            <th mat-header-cell *matHeaderCellDef>Duration</th>
            <td mat-cell *matCellDef="let a">{{ a.durationMinutes }} min</td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let a">
              <span class="badge" [class]="'status-' + a.status">{{ statusLabel(a.status) }}</span>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="openDetail(row)"></tr>

          <tr class="mat-mdc-row" *matNoDataRow>
            <td class="mat-mdc-cell no-data" [attr.colspan]="columns.length">
              @if (!loading()) {
                No appointments match the current filters.
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
    .filters {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 24px 16px 16px;
      flex-wrap: wrap;
    }
    .select {
      width: 180px;
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
    .video-icon {
      font-size: 16px;
      vertical-align: middle;
      margin-left: 4px;
      color: var(--muted-foreground);
    }
    .badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      white-space: nowrap;
    }
    .status-scheduled { background: var(--badge-blue-bg); color: var(--badge-blue-fg); }
    .status-completed { background: var(--badge-green-bg); color: var(--badge-green-fg); }
    .status-cancelled { background: var(--badge-grey-bg); color: var(--badge-grey-fg); }
    .status-no_show { background: var(--badge-red-bg); color: var(--badge-red-fg); }
    .no-data {
      padding: 24px;
      text-align: center;
      color: var(--muted-foreground);
    }
  `,
})
export class AdminAppointmentsOverviewComponent {
  private readonly appointmentsService = inject(AppointmentsService);
  private readonly usersService = inject(UsersService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  protected readonly statuses = APPOINTMENT_STATUSES;
  protected readonly columns = ['scheduledAt', 'patient', 'practitioner', 'duration', 'status'];

  protected readonly practitioners = signal<UserDetail[]>([]);
  protected readonly appointments = signal<Appointment[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(1);
  protected readonly limit = signal(10);
  protected readonly loading = signal(false);

  protected readonly practitionerId = new FormControl('', { nonNullable: true });
  protected readonly status = new FormControl<'' | AppointmentStatus>('', { nonNullable: true });
  protected readonly dateFrom = new FormControl<Date | null>(null);
  protected readonly dateTo = new FormControl<Date | null>(null);

  constructor() {
    this.usersService.listDoctors().subscribe((doctors) => this.practitioners.set(doctors));
    this.load();
    merge(
      this.practitionerId.valueChanges,
      this.status.valueChanges,
      this.dateFrom.valueChanges,
      this.dateTo.valueChanges,
    )
      .pipe(debounceTime(100), takeUntilDestroyed())
      .subscribe(() => this.resetAndLoad());
  }

  protected statusLabel(status: AppointmentStatus): string {
    return APPOINTMENT_STATUS_LABELS[status];
  }

  protected load(): void {
    this.loading.set(true);
    this.appointmentsService
      .list({
        practitionerId: this.practitionerId.value || undefined,
        status: this.status.value || undefined,
        dateFrom: this.dateFrom.value ? toIsoDate(this.dateFrom.value) : undefined,
        dateTo: this.dateTo.value ? toIsoDate(this.dateTo.value) : undefined,
        page: this.page(),
        limit: this.limit(),
      })
      .subscribe({
        next: (result) => {
          this.appointments.set(result.data);
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

  protected openDetail(appointment: Appointment): void {
    this.dialog
      .open(AppointmentDetailDialogComponent, { data: appointment })
      .afterClosed()
      .subscribe((changed) => changed && this.load());
  }
}

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
