import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import { CatalogueItem, CatalogueType } from '../../core/models/catalogue.model';
import { PatientProgramme, ServiceType } from '../../core/models/patient-programme.model';
import { ToastService } from '../../core/services/toast.service';
import { CatalogueService } from '../catalogue/catalogue.service';
import { AssignmentReceiptComponent } from './assignment-receipt.component';
import { AssignServicePayload, PatientProgrammesService } from './patient-programmes.service';

export interface AssignServiceDialogData {
  patientId: string;
  patientName: string;
}

const TYPE_TO_ROUTE: Record<ServiceType, CatalogueType> = {
  programme: 'programmes',
  session: 'sessions',
  challenge: 'challenges',
};

/**
 * Three-step assignment flow: pick the service type and a published catalogue
 * item → confirm start date / price override / notes → receipt of the created
 * assignment with its order and invoice. Closes with the created assignment
 * (set on entering the receipt step) so the panel refreshes.
 */
@Component({
  selector: 'app-assign-service-dialog',
  standalone: true,
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatStepperModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    AssignmentReceiptComponent,
  ],
  template: `
    <h2 mat-dialog-title>Assign a service to {{ data.patientName }}</h2>

    <mat-dialog-content>
      <mat-stepper #stepper [linear]="true" orientation="horizontal">
        <mat-step label="Service" [completed]="!!selected()">
          <div class="type-row">
            <mat-button-toggle-group
              [value]="serviceType()"
              (change)="switchType($event.value)"
              aria-label="Service type"
            >
              <mat-button-toggle value="programme">Programmes</mat-button-toggle>
              <mat-button-toggle value="session">Sessions</mat-button-toggle>
              <mat-button-toggle value="challenge">Challenges</mat-button-toggle>
            </mat-button-toggle-group>
          </div>

          <mat-form-field appearance="outline" class="full" subscriptSizing="dynamic">
            <mat-label>Search published items</mat-label>
            <input matInput [formControl]="search" placeholder="Name, code or category" />
            <mat-icon matPrefix>search</mat-icon>
          </mat-form-field>

          @if (loadingItems()) {
            <mat-progress-bar mode="indeterminate" />
          }

          <div class="item-list" role="listbox" aria-label="Published catalogue items">
            @for (item of items(); track item.id) {
              <button
                type="button"
                class="item-row"
                [class.selected]="selected()?.id === item.id"
                (click)="select(item)"
                role="option"
                [attr.aria-selected]="selected()?.id === item.id"
              >
                <div class="item-main">
                  <span class="item-name">{{ item.name }}</span>
                  <span class="item-meta">{{ item.serviceCode }} · {{ item.type }} · {{ durationOf(item) }}</span>
                </div>
                <span class="item-price">{{ item.priceInr | currency: 'INR' : 'symbol' : '1.0-2' }}</span>
              </button>
            } @empty {
              @if (!loadingItems()) {
                <p class="muted">No published {{ serviceType() }}s match.</p>
              }
            }
          </div>

          <div class="step-actions">
            <button mat-flat-button color="primary" matStepperNext [disabled]="!selected()">
              Next
            </button>
          </div>
        </mat-step>

        <mat-step label="Details" [completed]="form.valid">
          @if (selected(); as item) {
            <p class="summary-line">
              <strong>{{ item.name }}</strong>
              <span class="muted"> · {{ item.serviceCode }} · catalogue price
                {{ item.priceInr | currency: 'INR' : 'symbol' : '1.0-2' }}</span>
            </p>
          }
          <form [formGroup]="form" class="details-grid">
            <mat-form-field appearance="outline">
              <mat-label>Start date</mat-label>
              <input matInput formControlName="startDate" [matDatepicker]="startPicker" />
              <mat-datepicker-toggle matIconSuffix [for]="startPicker" />
              <mat-datepicker #startPicker />
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Price (INR)</mat-label>
              <input matInput formControlName="priceInr" type="number" min="0" step="0.01" />
              <span matTextPrefix>₹&nbsp;</span>
              <mat-hint>Prefilled from the catalogue — change to override</mat-hint>
              @if (form.controls.priceInr.hasError('server')) {
                <mat-error>{{ form.controls.priceInr.getError('server') }}</mat-error>
              } @else if (form.controls.priceInr.invalid) {
                <mat-error>Price must be 0 or more</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="span-2">
              <mat-label>Notes (optional)</mat-label>
              <textarea matInput formControlName="notes" rows="2"></textarea>
            </mat-form-field>
          </form>

          <p class="muted small">
            End date is derived from the {{ serviceType() }}'s duration
            {{ serviceType() === 'session' ? '— sessions are single-day appointments' : '' }};
            an order is created with it{{ pricePreview() > 0
              ? ', plus an activation transaction with an invoice number'
              : ' (free service — no transaction)' }}.
          </p>

          <div class="step-actions">
            <button mat-button matStepperPrevious [disabled]="saving()">Back</button>
            <button mat-flat-button color="primary" (click)="confirm()" [disabled]="saving()">
              @if (saving()) {
                <mat-spinner diameter="18" />
              } @else {
                Assign service
              }
            </button>
          </div>
        </mat-step>

        <mat-step label="Receipt" [completed]="!!created()">
          <app-assignment-receipt [assignment]="created()" />
          <div class="step-actions">
            <button mat-flat-button color="primary" (click)="ref.close(created())">Done</button>
          </div>
        </mat-step>
      </mat-stepper>
    </mat-dialog-content>
  `,
  styles: `
    mat-stepper {
      min-width: 620px;
    }
    .type-row {
      margin-bottom: 12px;
    }
    .full {
      width: 100%;
    }
    .item-list {
      display: flex;
      flex-direction: column;
      gap: 6px;
      max-height: 260px;
      overflow-y: auto;
      margin-top: 10px;
    }
    .item-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 10px 12px;
      border: 1px solid rgba(0, 0, 0, 0.15);
      border-radius: 8px;
      background: none;
      cursor: pointer;
      text-align: left;
      font: inherit;
    }
    .item-row:hover {
      background: rgba(0, 0, 0, 0.03);
    }
    .item-row.selected {
      border-color: var(--primary);
      background: rgba(45, 138, 104, 0.07);
    }
    .item-main {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .item-name {
      font-weight: 500;
    }
    .item-meta {
      font-size: 0.8rem;
      color: rgba(0, 0, 0, 0.6);
    }
    .item-price {
      font-weight: 600;
      white-space: nowrap;
    }
    .details-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px 16px;
      margin-top: 8px;
    }
    .span-2 {
      grid-column: span 2;
    }
    .summary-line {
      margin: 4px 0 8px;
    }
    .muted {
      color: rgba(0, 0, 0, 0.55);
    }
    .small {
      font-size: 0.82rem;
    }
    .step-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 16px;
    }
  `,
})
export class AssignServiceDialogComponent {
  protected readonly ref = inject(MatDialogRef<AssignServiceDialogComponent>);
  protected readonly data = inject<AssignServiceDialogData>(MAT_DIALOG_DATA);
  private readonly catalogueService = inject(CatalogueService);
  private readonly programmesService = inject(PatientProgrammesService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  private readonly stepper = viewChild.required(MatStepper);

  protected readonly serviceType = signal<ServiceType>('programme');
  protected readonly items = signal<CatalogueItem[]>([]);
  protected readonly selected = signal<CatalogueItem | null>(null);
  protected readonly loadingItems = signal(false);
  protected readonly saving = signal(false);
  protected readonly created = signal<PatientProgramme | null>(null);

  protected readonly search = new FormControl('', { nonNullable: true });

  protected readonly form = this.fb.nonNullable.group({
    startDate: [new Date(), Validators.required],
    priceInr: [null as number | null, [Validators.min(0)]],
    notes: [''],
  });

  constructor() {
    this.loadItems();
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.loadItems());
    // dialog must not close silently once the assignment exists
    this.ref.disableClose = true;
    this.ref.backdropClick().pipe(takeUntilDestroyed()).subscribe(() => {
      this.ref.close(this.created());
    });
  }

  protected switchType(type: ServiceType): void {
    this.serviceType.set(type);
    this.selected.set(null);
    this.loadItems();
  }

  protected select(item: CatalogueItem): void {
    this.selected.set(item);
    this.form.controls.priceInr.setValue(item.priceInr);
  }

  protected durationOf(item: CatalogueItem): string {
    if (item.durationWeeks != null) return `${item.durationWeeks} wk`;
    if (item.durationMinutes != null) return `${item.durationMinutes} min`;
    if (item.durationDays != null) return `${item.durationDays} days`;
    return '';
  }

  protected pricePreview(): number {
    return this.form.controls.priceInr.value ?? this.selected()?.priceInr ?? 0;
  }

  protected confirm(): void {
    const item = this.selected();
    if (!item || this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const type = this.serviceType();
    const payload: AssignServicePayload = {
      serviceType: type,
      [`${type}Id`]: item.id,
      startDate: toIsoDate(v.startDate),
      priceInr: v.priceInr ?? undefined,
      notes: v.notes || undefined,
    };

    this.saving.set(true);
    this.programmesService.create(this.data.patientId, payload).subscribe({
      next: (assignment) => {
        this.saving.set(false);
        this.created.set(assignment);
        this.toast.success(`${item.name} assigned to ${this.data.patientName}`);
        this.stepper().next(); // on to the receipt step
      },
      error: (err: ApiError) => {
        this.saving.set(false);
        this.toast.error(err.error);
      },
    });
  }

  private loadItems(): void {
    this.loadingItems.set(true);
    this.catalogueService
      .list(TYPE_TO_ROUTE[this.serviceType()], {
        status: 'published',
        search: this.search.value || undefined,
        page: 1,
        limit: 50,
      })
      .subscribe({
        next: (result) => {
          this.items.set(result.data);
          this.loadingItems.set(false);
        },
        error: (err: ApiError) => {
          this.loadingItems.set(false);
          this.toast.error(err.error);
        },
      });
  }
}

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
