import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiError } from '../../core/models/api-response.model';
import { UserDetail } from '../../core/models/user.model';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { UsersService } from '../users/users.service';
import { Assignment, AssignmentsService } from './assignments.service';

export interface ManagePatientsDialogData {
  doctor: UserDetail;
}

/**
 * Individual add/remove of a doctor's patients via the assignments API —
 * complements the bulk "replace patient list" dialog, which is better for
 * setting the whole list at once.
 */
@Component({
  selector: 'app-manage-patients-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Patients of {{ data.doctor.name }}</h2>

    <mat-dialog-content>
      @if (loading()) {
        <div class="center"><mat-spinner diameter="32" /></div>
      } @else {
        @if (assignments().length === 0) {
          <p class="empty">No patients assigned yet.</p>
        }
        <mat-list>
          @for (a of assignments(); track a.id) {
            <mat-list-item>
              <mat-icon matListItemIcon>personal_injury</mat-icon>
              <span matListItemTitle>{{ a.patient.name }}</span>
              <button
                mat-icon-button
                matListItemMeta
                aria-label="Remove patient"
                (click)="remove(a)"
              >
                <mat-icon>close</mat-icon>
              </button>
            </mat-list-item>
          }
        </mat-list>

        <mat-form-field appearance="outline" class="add-field" subscriptSizing="dynamic">
          <mat-label>Add patient</mat-label>
          <input
            matInput
            [formControl]="patientSearch"
            [matAutocomplete]="auto"
            placeholder="Search patients"
          />
          <mat-icon matPrefix>person_add</mat-icon>
          <mat-autocomplete #auto (optionSelected)="add($event.option.value)">
            @for (p of selectablePatients(); track p.id) {
              <mat-option [value]="p">{{ p.name }} — {{ p.email }}</mat-option>
            }
          </mat-autocomplete>
        </mat-form-field>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()">Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      width: 100%;
      max-width: 440px;
      max-height: 60vh;
    }
    .center {
      display: grid;
      place-content: center;
      padding: 32px;
    }
    .empty {
      color: var(--muted-foreground);
      margin: 8px 0;
    }
    .add-field {
      width: 100%;
      margin-top: 8px;
    }
  `,
})
export class ManagePatientsDialogComponent {
  protected readonly ref = inject(MatDialogRef<ManagePatientsDialogComponent>);
  protected readonly data = inject<ManagePatientsDialogData>(MAT_DIALOG_DATA);
  private readonly assignmentsService = inject(AssignmentsService);
  private readonly usersService = inject(UsersService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(MatDialog);

  protected readonly loading = signal(true);
  protected readonly assignments = signal<Assignment[]>([]);
  private readonly patients = signal<UserDetail[]>([]);

  protected readonly patientSearch = new FormControl<string | UserDetail>('', {
    nonNullable: true,
  });
  private readonly searchValue = toSignal(this.patientSearch.valueChanges, { initialValue: '' });

  protected readonly selectablePatients = computed(() => {
    const assignedIds = new Set(this.assignments().map((a) => a.patient.id));
    const raw = this.searchValue();
    const term = (typeof raw === 'string' ? raw : raw.name).trim().toLowerCase();
    return this.patients()
      .filter((p) => p.isActive && !assignedIds.has(p.id))
      .filter(
        (p) =>
          !term ||
          p.name.toLowerCase().includes(term) ||
          p.email.toLowerCase().includes(term),
      );
  });

  constructor() {
    this.usersService
      .list({ role: 'PATIENT', page: 1, limit: 500 })
      .subscribe((res) => this.patients.set(res.data));
    this.reload();
  }

  protected add(patient: UserDetail): void {
    this.patientSearch.setValue('');
    this.assignmentsService.create(this.data.doctor.id, patient.id).subscribe({
      next: () => {
        this.toast.success(`${patient.name} assigned — ${this.data.doctor.name} has been notified`);
        this.reload();
      },
      error: (err: ApiError) => this.toast.error(err.error),
    });
  }

  protected remove(assignment: Assignment): void {
    const data: ConfirmDialogData = {
      title: 'Remove patient',
      message: `Remove ${assignment.patient.name} from ${this.data.doctor.name}? The patient and their history are kept.`,
      confirmLabel: 'Remove',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.assignmentsService.remove(assignment.id).subscribe({
          next: () => {
            this.toast.success(`${assignment.patient.name} removed`);
            this.reload();
          },
          error: (err: ApiError) => this.toast.error(err.error),
        });
      });
  }

  private reload(): void {
    this.assignmentsService.list({ doctorId: this.data.doctor.id }).subscribe((assignments) => {
      this.assignments.set(assignments);
      this.loading.set(false);
    });
  }
}
