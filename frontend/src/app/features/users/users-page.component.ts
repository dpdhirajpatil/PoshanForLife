import { DatePipe } from '@angular/common';
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
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import { Role, UserDetail } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { ManagePatientsDialogComponent } from '../assignments/manage-patients-dialog.component';
import { AssignPatientsDialogComponent } from './assign-patients-dialog.component';
import { ChangePasswordDialogComponent } from './change-password-dialog.component';
import { UserFormDialogComponent } from './user-form-dialog.component';
import { UsersService } from './users.service';

@Component({
  selector: 'app-users-page',
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
    MatMenuModule,
    MatDialogModule,
    MatProgressBarModule,
  ],
  template: `
    <div class="page-header">
      <h1>Users</h1>
      <button mat-flat-button color="primary" (click)="openCreate()">
        <mat-icon>person_add</mat-icon>
        Add user
      </button>
    </div>

    <mat-card appearance="outlined">
      <div class="filters">
        <mat-form-field appearance="outline" class="search" subscriptSizing="dynamic">
          <mat-label>Search</mat-label>
          <input matInput [formControl]="search" placeholder="Name or email" />
          <mat-icon matPrefix>search</mat-icon>
        </mat-form-field>

        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Role</mat-label>
          <mat-select [formControl]="roleFilter">
            <mat-option [value]="null">All roles</mat-option>
            @for (role of roles; track role) {
              <mat-option [value]="role">{{ role }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <div class="table-scroll">
      <table mat-table [dataSource]="users()">
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let user">{{ user.name }}</td>
        </ng-container>

        <ng-container matColumnDef="email">
          <th mat-header-cell *matHeaderCellDef>Email</th>
          <td mat-cell *matCellDef="let user">{{ user.email }}</td>
        </ng-container>

        <ng-container matColumnDef="role">
          <th mat-header-cell *matHeaderCellDef>Role</th>
          <td mat-cell *matCellDef="let user">
            <span class="role-badge" [class]="'role-' + user.role.toLowerCase()">
              {{ user.role }}
            </span>
          </td>
        </ng-container>

        <ng-container matColumnDef="phone">
          <th mat-header-cell *matHeaderCellDef>Phone</th>
          <td mat-cell *matCellDef="let user">{{ user.phone || '—' }}</td>
        </ng-container>

        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let user">
            <span class="status-badge" [class.inactive]="!user.isActive">
              {{ user.isActive ? 'Active' : 'Inactive' }}
            </span>
          </td>
        </ng-container>

        <ng-container matColumnDef="createdAt">
          <th mat-header-cell *matHeaderCellDef>Created</th>
          <td mat-cell *matCellDef="let user">{{ user.createdAt | date: 'mediumDate' }}</td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let user">
            <button
              mat-icon-button
              [matMenuTriggerFor]="rowMenu"
              [matMenuTriggerData]="{ user }"
              aria-label="User actions"
            >
              <mat-icon>more_vert</mat-icon>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr
          mat-row
          *matRowDef="let row; columns: columns"
          [class.row-inactive]="!row.isActive"
        ></tr>

        <tr class="mat-mdc-row" *matNoDataRow>
          <td class="mat-mdc-cell no-data" [attr.colspan]="columns.length">
            @if (!loading()) {
              No users match the current filters.
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
      <ng-template matMenuContent let-user="user">
        <button mat-menu-item (click)="openEdit(user)">
          <mat-icon>edit</mat-icon>
          Edit
        </button>
        <button mat-menu-item (click)="openChangePassword(user)">
          <mat-icon>key</mat-icon>
          {{ isSelf(user) ? 'Change password' : 'Reset password' }}
        </button>
        @if (user.role === 'DOCTOR') {
          <button mat-menu-item (click)="openManagePatients(user)">
            <mat-icon>person_add</mat-icon>
            Manage patients
          </button>
          <button mat-menu-item (click)="openAssignPatients(user)">
            <mat-icon>diversity_1</mat-icon>
            Replace patient list
          </button>
        }
        @if (user.isActive) {
          <button mat-menu-item (click)="deactivate(user)" [disabled]="isSelf(user)">
            <mat-icon>person_off</mat-icon>
            Deactivate
          </button>
        } @else {
          <button mat-menu-item (click)="reactivate(user)">
            <mat-icon>how_to_reg</mat-icon>
            Reactivate
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
    .filters {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      padding: 16px;
    }
    .search {
      flex: 1 1 220px;
      max-width: 420px;
    }
    table {
      width: 100%;
    }
    .role-badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      font-weight: 600;
    }
    .role-admin {
      background: var(--badge-purple-bg);
      color: var(--badge-purple-fg);
    }
    .role-doctor {
      background: var(--badge-blue-bg);
      color: var(--badge-blue-fg);
    }
    .role-patient {
      background: var(--badge-green-bg);
      color: var(--badge-green-fg);
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
export class UsersPageComponent {
  private readonly usersService = inject(UsersService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly currentUserId = inject(AuthService).currentUser()?.id;

  protected readonly roles: Role[] = ['ADMIN', 'DOCTOR', 'PATIENT'];
  protected readonly columns = ['name', 'email', 'role', 'phone', 'status', 'createdAt', 'actions'];

  protected readonly users = signal<UserDetail[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(1);
  protected readonly limit = signal(10);
  protected readonly loading = signal(false);

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly roleFilter = new FormControl<Role | null>(null);

  constructor() {
    this.load();
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.page.set(1);
        this.load();
      });
    this.roleFilter.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.page.set(1);
      this.load();
    });
  }

  protected isSelf(user: UserDetail): boolean {
    return user.id === this.currentUserId;
  }

  protected load(): void {
    this.loading.set(true);
    this.usersService
      .list({
        role: this.roleFilter.value ?? undefined,
        search: this.search.value || undefined,
        page: this.page(),
        limit: this.limit(),
      })
      .subscribe({
        next: (result) => {
          this.users.set(result.data);
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
      .open(UserFormDialogComponent, { data: {} })
      .afterClosed()
      .subscribe((created) => created && this.load());
  }

  protected openEdit(user: UserDetail): void {
    this.dialog
      .open(UserFormDialogComponent, { data: { user } })
      .afterClosed()
      .subscribe((updated) => updated && this.load());
  }

  protected openChangePassword(user: UserDetail): void {
    this.dialog.open(ChangePasswordDialogComponent, { data: { user } });
  }

  protected openAssignPatients(user: UserDetail): void {
    this.dialog.open(AssignPatientsDialogComponent, { data: { doctor: user } });
  }

  protected openManagePatients(user: UserDetail): void {
    this.dialog.open(ManagePatientsDialogComponent, { data: { doctor: user } });
  }

  protected deactivate(user: UserDetail): void {
    const data: ConfirmDialogData = {
      title: 'Deactivate user',
      message: `${user.name} (${user.email}) will no longer be able to sign in. Their data is kept and the account can be reactivated later.`,
      confirmLabel: 'Deactivate',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.usersService.deactivate(user.id).subscribe({
          next: () => {
            this.toast.success(`${user.name} deactivated`);
            this.load();
          },
          error: (err: ApiError) => this.toast.error(err.error),
        });
      });
  }

  protected reactivate(user: UserDetail): void {
    this.usersService.update(user.id, { isActive: true }).subscribe({
      next: () => {
        this.toast.success(`${user.name} reactivated`);
        this.load();
      },
      error: (err: ApiError) => this.toast.error(err.error),
    });
  }
}
