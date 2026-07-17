import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import { UserDetail } from '../../core/models/user.model';
import { ToastService } from '../../core/services/toast.service';
import { UsersService } from './users.service';

export interface AssignPatientsDialogData {
  doctor: UserDetail;
}

/**
 * Manages a doctor's patient assignments. Saving REPLACES the full set
 * (backend contract), so the list is pre-selected with current assignments.
 */
@Component({
  selector: 'app-assign-patients-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatListModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Assigned patients — {{ data.doctor.name }}</h2>

    <mat-dialog-content>
      @if (loading()) {
        <div class="center"><mat-spinner diameter="32" /></div>
      } @else {
        <mat-form-field appearance="outline" class="filter">
          <mat-label>Filter patients</mat-label>
          <input matInput [formControl]="filter" placeholder="Name or email" />
        </mat-form-field>

        @if (filteredPatients().length === 0) {
          <p class="empty">No patients found.</p>
        }
        <mat-selection-list>
          @for (patient of filteredPatients(); track patient.id) {
            <mat-list-option
              [selected]="selectedIds().has(patient.id)"
              (selectedChange)="toggle(patient.id, $event)"
            >
              {{ patient.name }} — {{ patient.email }}
            </mat-list-option>
          }
        </mat-selection-list>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <span class="count">{{ selectedIds().size }} selected</span>
      <button mat-button (click)="ref.close()" [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving() || loading()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Save assignments
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .filter {
      width: 100%;
      margin-top: 8px;
    }
    .center {
      display: grid;
      place-content: center;
      padding: 32px;
    }
    .empty {
      color: rgba(0, 0, 0, 0.6);
      padding: 8px;
    }
    .count {
      margin-right: auto;
      color: rgba(0, 0, 0, 0.6);
      font-size: 0.85rem;
    }
    mat-dialog-content {
      min-width: 440px;
      max-height: 60vh;
    }
  `,
})
export class AssignPatientsDialogComponent {
  protected readonly ref = inject(MatDialogRef<AssignPatientsDialogComponent>);
  protected readonly data = inject<AssignPatientsDialogData>(MAT_DIALOG_DATA);
  private readonly usersService = inject(UsersService);
  private readonly toast = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly patients = signal<UserDetail[]>([]);
  protected readonly selectedIds = signal<Set<string>>(new Set());

  protected readonly filter = new FormControl('', { nonNullable: true });
  private readonly filterValue = toSignal(this.filter.valueChanges, { initialValue: '' });

  protected readonly filteredPatients = computed(() => {
    const term = this.filterValue().trim().toLowerCase();
    if (!term) return this.patients();
    return this.patients().filter(
      (p) => p.name.toLowerCase().includes(term) || p.email.toLowerCase().includes(term),
    );
  });

  constructor() {
    forkJoin({
      patients: this.usersService.listPatients(),
      assigned: this.usersService.assignedPatients(this.data.doctor.id),
    }).subscribe({
      next: ({ patients, assigned }) => {
        this.patients.set(patients);
        this.selectedIds.set(new Set(assigned.map((p) => p.id)));
        this.loading.set(false);
      },
      error: (err: ApiError) => {
        this.toast.error(err.error);
        this.ref.close();
      },
    });
  }

  toggle(id: string, selected: boolean): void {
    const next = new Set(this.selectedIds());
    if (selected) {
      next.add(id);
    } else {
      next.delete(id);
    }
    this.selectedIds.set(next);
  }

  save(): void {
    this.saving.set(true);
    this.usersService.assignPatients(this.data.doctor.id, [...this.selectedIds()]).subscribe({
      next: (assigned) => {
        this.toast.success(`${assigned.length} patient(s) assigned to ${this.data.doctor.name}`);
        this.ref.close(true);
      },
      error: (err: ApiError) => {
        this.saving.set(false);
        this.toast.error(err.error);
      },
    });
  }
}
