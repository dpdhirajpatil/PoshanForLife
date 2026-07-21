import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { computed } from '@angular/core';
import { ApiError } from '../../core/models/api-response.model';
import { DoctorRef } from '../../core/models/patient.model';
import { UserDetail } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { UsersService } from '../users/users.service';
import { Assignment, AssignmentsService } from './assignments.service';

/**
 * "Assigned doctors" panel on the patient detail page. Admins can add/remove
 * individual doctors (assignments API is admin-only); doctors get a read-only
 * list fed from the patient detail payload.
 */
@Component({
  selector: 'app-assigned-doctors-panel',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatDialogModule,
  ],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>Assigned doctors</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        @if (isAdmin) {
          @if (assignments().length === 0) {
            <p class="empty">No doctors assigned yet.</p>
          }
          <mat-list>
            @for (a of assignments(); track a.id) {
              <mat-list-item>
                <mat-icon matListItemIcon>stethoscope</mat-icon>
                <span matListItemTitle>{{ a.doctor.name }}</span>
                <button
                  mat-icon-button
                  matListItemMeta
                  aria-label="Remove doctor"
                  (click)="remove(a)"
                >
                  <mat-icon>close</mat-icon>
                </button>
              </mat-list-item>
            }
          </mat-list>

          <mat-form-field appearance="outline" class="add-field" subscriptSizing="dynamic">
            <mat-label>Add doctor</mat-label>
            <input
              matInput
              [formControl]="doctorSearch"
              [matAutocomplete]="auto"
              placeholder="Search doctors"
            />
            <mat-icon matPrefix>person_add</mat-icon>
            <mat-autocomplete #auto (optionSelected)="add($event.option.value)">
              @for (doc of selectableDoctors(); track doc.id) {
                <mat-option [value]="doc">{{ doc.name }} — {{ doc.email }}</mat-option>
              }
            </mat-autocomplete>
          </mat-form-field>
        } @else {
          @if (fallbackDoctors().length === 0) {
            <p class="empty">No doctors assigned yet.</p>
          }
          <mat-list>
            @for (doc of fallbackDoctors(); track doc.id) {
              <mat-list-item>
                <mat-icon matListItemIcon>stethoscope</mat-icon>
                <span matListItemTitle>{{ doc.name }}</span>
              </mat-list-item>
            }
          </mat-list>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: `
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
export class AssignedDoctorsPanelComponent implements OnInit {
  readonly patientId = input.required<string>();
  /** Shown to non-admin users, who cannot call the assignments API. */
  readonly fallbackDoctors = input<DoctorRef[]>([]);
  /** Emitted after any successful add/remove so the parent can refresh. */
  readonly changed = output<void>();

  private readonly assignmentsService = inject(AssignmentsService);
  private readonly usersService = inject(UsersService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(MatDialog);

  protected readonly isAdmin = inject(AuthService).isAdmin();
  protected readonly assignments = signal<Assignment[]>([]);
  private readonly doctors = signal<UserDetail[]>([]);

  protected readonly doctorSearch = new FormControl<string | UserDetail>('', { nonNullable: true });
  private readonly searchValue = toSignal(this.doctorSearch.valueChanges, { initialValue: '' });

  /** Active doctors not yet assigned, filtered by the search text. */
  protected readonly selectableDoctors = computed(() => {
    const assignedIds = new Set(this.assignments().map((a) => a.doctor.id));
    const raw = this.searchValue();
    const term = (typeof raw === 'string' ? raw : raw.name).trim().toLowerCase();
    return this.doctors()
      .filter((d) => d.isActive && !assignedIds.has(d.id))
      .filter(
        (d) =>
          !term ||
          d.name.toLowerCase().includes(term) ||
          d.email.toLowerCase().includes(term),
      );
  });

  ngOnInit(): void {
    if (!this.isAdmin) return;
    this.reload();
    this.usersService
      .list({ role: 'DOCTOR', page: 1, limit: 100 })
      .subscribe((res) => this.doctors.set(res.data));
  }

  protected add(doctor: UserDetail): void {
    this.doctorSearch.setValue('');
    this.assignmentsService.create(doctor.id, this.patientId()).subscribe({
      next: () => {
        this.toast.success(`Assigned to ${doctor.name} — the doctor has been notified`);
        this.reload();
        this.changed.emit();
      },
      error: (err: ApiError) => this.toast.error(err.error),
    });
  }

  protected remove(assignment: Assignment): void {
    const data: ConfirmDialogData = {
      title: 'Remove doctor',
      message: `Remove ${assignment.doctor.name} from this patient? The patient and their history are kept.`,
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
            this.toast.success(`${assignment.doctor.name} removed`);
            this.reload();
            this.changed.emit();
          },
          error: (err: ApiError) => this.toast.error(err.error),
        });
      });
  }

  private reload(): void {
    this.assignmentsService
      .list({ patientId: this.patientId() })
      .subscribe((assignments) => this.assignments.set(assignments));
  }
}
