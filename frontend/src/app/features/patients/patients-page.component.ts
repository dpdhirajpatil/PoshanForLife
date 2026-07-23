import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import { PatientStats, PatientSummary, ageFrom } from '../../core/models/patient.model';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { PatientFormDialogComponent } from './patient-form-dialog.component';
import { PatientsService } from './patients.service';

@Component({
  selector: 'app-patients-page',
  standalone: true,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatDialogModule,
    MatProgressBarModule,
  ],
  template: `
    <div class="page-header">
      <h1>Patients</h1>
      <button mat-flat-button color="primary" (click)="openCreate()">
        <mat-icon>person_add</mat-icon>
        Add patient
      </button>
    </div>

    <div class="stats-row">
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">{{ stats()?.totalPatients ?? '—' }}</span>
        <span class="stat-label">Total patients</span>
      </mat-card>
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">{{ stats()?.activeThisMonth ?? '—' }}</span>
        <span class="stat-label">Active this month</span>
      </mat-card>
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">
          {{ stats()?.averageBmi != null ? (stats()!.averageBmi | number: '1.1-1') : '—' }}
        </span>
        <span class="stat-label">Average BMI</span>
      </mat-card>
      <mat-card appearance="outlined" class="stat-card">
        <span class="stat-value">
          {{
            stats()?.averageBodyFatPct != null
              ? (stats()!.averageBodyFatPct | number: '1.1-1') + '%'
              : '—'
          }}
        </span>
        <span class="stat-label">Average body fat</span>
      </mat-card>
    </div>

    <mat-card appearance="outlined">
      <div class="filters">
        <mat-form-field appearance="outline" class="search" subscriptSizing="dynamic">
          <mat-label>Search</mat-label>
          <input matInput [formControl]="search" placeholder="Name or email" />
          <mat-icon matPrefix>search</mat-icon>
        </mat-form-field>
      </div>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <div class="table-scroll">
      <table mat-table [dataSource]="patients()">
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let p">
            <a class="patient-link" [routerLink]="[p.id]">{{ p.name }}</a>
          </td>
        </ng-container>

        <ng-container matColumnDef="email">
          <th mat-header-cell *matHeaderCellDef>Email</th>
          <td mat-cell *matCellDef="let p">{{ p.email }}</td>
        </ng-container>

        <ng-container matColumnDef="phone">
          <th mat-header-cell *matHeaderCellDef>Phone</th>
          <td mat-cell *matCellDef="let p">{{ p.phone || '—' }}</td>
        </ng-container>

        <ng-container matColumnDef="doctor">
          <th mat-header-cell *matHeaderCellDef>Assigned doctor</th>
          <td mat-cell *matCellDef="let p">
            {{ doctorNames(p) || '—' }}
          </td>
        </ng-container>

        <ng-container matColumnDef="age">
          <th mat-header-cell *matHeaderCellDef>Age</th>
          <td mat-cell *matCellDef="let p">{{ age(p) ?? '—' }}</td>
        </ng-container>

        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let p">
            <span class="status-badge" [class.inactive]="!p.isActive">
              {{ p.isActive ? 'Active' : 'Inactive' }}
            </span>
          </td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let p">
            <button
              mat-icon-button
              [matMenuTriggerFor]="rowMenu"
              [matMenuTriggerData]="{ patient: p }"
              aria-label="Patient actions"
            >
              <mat-icon>more_vert</mat-icon>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns()"></tr>
        <tr mat-row *matRowDef="let row; columns: columns()" [class.row-inactive]="!row.isActive"></tr>

        <tr class="mat-mdc-row" *matNoDataRow>
          <td class="mat-mdc-cell no-data" [attr.colspan]="columns().length">
            @if (!loading()) {
              No patients match the current filters.
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

    <mat-menu #rowMenu="matMenu">
      <ng-template matMenuContent let-patient="patient">
        <a mat-menu-item [routerLink]="[patient.id]">
          <mat-icon>visibility</mat-icon>
          View details
        </a>
        <button mat-menu-item (click)="openEdit(patient)">
          <mat-icon>edit</mat-icon>
          Edit
        </button>
        @if (isAdmin && patient.isActive) {
          <button mat-menu-item (click)="deactivate(patient)">
            <mat-icon>person_off</mat-icon>
            Deactivate
          </button>
        }
      </ng-template>
    </mat-menu>
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
    .stats-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
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
      padding: 24px 16px 16px;
    }
    .search {
      width: 100%;
      max-width: 420px;
    }
    table {
      width: 100%;
    }
    .patient-link {
      color: inherit;
      font-weight: 500;
      text-decoration: none;
    }
    .patient-link:hover {
      text-decoration: underline;
    }
    .status-badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      background: var(--badge-green-bg);
      color: var(--badge-green-fg);
    }
    .status-badge.inactive {
      background: var(--badge-red-bg);
      color: var(--badge-red-fg);
    }
    .row-inactive td {
      opacity: 0.55;
    }
    .no-data {
      padding: 24px;
      text-align: center;
      color: var(--muted-foreground);
    }
  `,
})
export class PatientsPageComponent {
  private readonly patientsService = inject(PatientsService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  protected readonly isAdmin = inject(AuthService).isAdmin();

  protected readonly patients = signal<PatientSummary[]>([]);
  protected readonly stats = signal<PatientStats | null>(null);
  protected readonly total = signal(0);
  protected readonly page = signal(1);
  protected readonly limit = signal(10);
  protected readonly loading = signal(false);

  protected readonly search = new FormControl('', { nonNullable: true });

  /** Doctors see only their own patients, so the doctor column is admin-only. */
  protected readonly columns = signal(
    this.isAdmin
      ? ['name', 'email', 'phone', 'doctor', 'age', 'status', 'actions']
      : ['name', 'email', 'phone', 'age', 'status', 'actions'],
  );

  constructor() {
    this.load();
    this.loadStats();
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.page.set(1);
        this.load();
      });
  }

  protected age = (p: PatientSummary) => ageFrom(p.dateOfBirth);

  protected doctorNames(p: PatientSummary): string {
    return p.assignedDoctors.map((d) => d.name).join(', ');
  }

  protected load(): void {
    this.loading.set(true);
    this.patientsService
      .list({ search: this.search.value || undefined, page: this.page(), limit: this.limit() })
      .subscribe({
        next: (result) => {
          this.patients.set(result.data);
          this.total.set(result.meta?.total ?? result.data.length);
          this.loading.set(false);
        },
        error: (err: ApiError) => {
          this.loading.set(false);
          this.toast.error(err.error);
        },
      });
  }

  protected loadStats(): void {
    this.patientsService.stats().subscribe({
      next: (stats) => this.stats.set(stats),
      error: () => this.stats.set(null),
    });
  }

  protected onPage(event: PageEvent): void {
    this.page.set(event.pageIndex + 1);
    this.limit.set(event.pageSize);
    this.load();
  }

  protected openCreate(): void {
    this.dialog
      .open(PatientFormDialogComponent, { data: {} })
      .afterClosed()
      .subscribe((created) => {
        if (!created) return;
        this.load();
        this.loadStats();
        if (created.tempPassword) {
          this.dialog.open(ConfirmDialogComponent, {
            data: {
              title: 'Temporary password',
              message:
                `Share this one-time temporary password with ${created.name}: ` +
                `${created.tempPassword} — it will not be shown again. ` +
                `(Automatic email delivery arrives with the notifications feature.)`,
              confirmLabel: 'Done',
            } satisfies ConfirmDialogData,
          });
        }
      });
  }

  protected openEdit(summary: PatientSummary): void {
    // fetch the full record so the medical section is pre-filled
    this.patientsService.get(summary.id).subscribe({
      next: (patient) => {
        this.dialog
          .open(PatientFormDialogComponent, { data: { patient } })
          .afterClosed()
          .subscribe((updated) => updated && this.load());
      },
      error: (err: ApiError) => this.toast.error(err.error),
    });
  }

  protected deactivate(patient: PatientSummary): void {
    const data: ConfirmDialogData = {
      title: 'Deactivate patient',
      message: `${patient.name} (${patient.email}) will no longer be able to sign in. Medical history and records are kept.`,
      confirmLabel: 'Deactivate',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.patientsService.deactivate(patient.id).subscribe({
          next: () => {
            this.toast.success(`${patient.name} deactivated`);
            this.load();
            this.loadStats();
          },
          error: (err: ApiError) => this.toast.error(err.error),
        });
      });
  }
}
