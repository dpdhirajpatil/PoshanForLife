import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatStepperModule } from '@angular/material/stepper';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import { CatalogueItem, CatalogueType } from '../../core/models/catalogue.model';
import { ConvertLeadResponse, LeadDetail } from '../../core/models/lead.model';
import { ServiceType } from '../../core/models/patient-programme.model';
import { ToastService } from '../../core/services/toast.service';
import { SidePanelHandleComponent } from '../../shared/side-panel-handle.component';
import { CatalogueService } from '../catalogue/catalogue.service';
import { LeadsService } from './leads.service';

export interface ConvertLeadDialogData {
  lead: LeadDetail;
}

const TYPE_TO_ROUTE: Record<ServiceType, CatalogueType> = {
  programme: 'programmes',
  session: 'sessions',
  challenge: 'challenges',
};

/**
 * Guided convert-to-patient flow: confirm/override contact + medical details
 * → optionally assign a published catalogue item immediately → confirm.
 * Closes with the created patientId so the caller can deep-link to the new
 * patient's profile.
 */
@Component({
  selector: 'app-convert-lead-dialog',
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
    SidePanelHandleComponent,
  ],
  template: `
    <app-side-panel-handle />
    <h2 mat-dialog-title>Convert {{ data.lead.name }} to a patient</h2>

    <mat-dialog-content>
      <mat-stepper [linear]="true" orientation="horizontal">
        <mat-step label="Contact details" [completed]="contactForm.valid">
          <p class="muted intro">Confirm or override the details used to create the patient account.</p>
          <form [formGroup]="contactForm" class="grid">
            <mat-form-field appearance="outline">
              <mat-label>Name</mat-label>
              <input matInput formControlName="name" />
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Email</mat-label>
              <input matInput formControlName="email" />
              @if (contactForm.controls.email.invalid && contactForm.controls.email.touched) {
                <mat-error>A valid email is required (this lead has none on file)</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Phone (optional)</mat-label>
              <input matInput formControlName="phone" />
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Date of birth (optional)</mat-label>
              <input matInput formControlName="dateOfBirth" [matDatepicker]="dobPicker" />
              <mat-datepicker-toggle matIconSuffix [for]="dobPicker" />
              <mat-datepicker #dobPicker />
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Blood group (optional)</mat-label>
              <input matInput formControlName="bloodGroup" />
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Height, cm (optional)</mat-label>
              <input matInput type="number" formControlName="heightCm" />
            </mat-form-field>
          </form>

          <div class="step-actions">
            <button mat-flat-button color="primary" matStepperNext [disabled]="contactForm.invalid">
              Next
            </button>
          </div>
        </mat-step>

        <mat-step label="Service (optional)">
          <p class="muted intro">Optionally assign a published catalogue item immediately.</p>

          <mat-button-toggle-group [value]="serviceType()" (change)="switchType($event.value)">
            <mat-button-toggle [value]="null">None</mat-button-toggle>
            <mat-button-toggle value="programme">Programmes</mat-button-toggle>
            <mat-button-toggle value="session">Sessions</mat-button-toggle>
            <mat-button-toggle value="challenge">Challenges</mat-button-toggle>
          </mat-button-toggle-group>

          @if (serviceType()) {
            @if (loadingItems()) {
              <mat-progress-bar mode="indeterminate" />
            }
            <div class="item-list" role="listbox" aria-label="Published catalogue items">
              @for (item of items(); track item.id) {
                <button
                  type="button"
                  class="item-row"
                  [class.selected]="selectedItem()?.id === item.id"
                  (click)="selectItem(item)"
                >
                  <div class="item-main">
                    <span class="item-name">{{ item.name }}</span>
                    <span class="item-meta">{{ item.serviceCode }}</span>
                  </div>
                  <span class="item-price">{{ item.priceInr | currency: 'INR' : 'symbol' : '1.0-2' }}</span>
                </button>
              } @empty {
                @if (!loadingItems()) {
                  <p class="muted">No published items match.</p>
                }
              }
            </div>

            @if (selectedItem()) {
              <form [formGroup]="serviceForm" class="assignment-grid">
                <mat-form-field appearance="outline">
                  <mat-label>Start date</mat-label>
                  <input matInput [matDatepicker]="startPicker" formControlName="startDate" />
                  <mat-datepicker-toggle matIconSuffix [for]="startPicker" />
                  <mat-datepicker #startPicker />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Price (INR)</mat-label>
                  <input matInput type="number" formControlName="price" />
                </mat-form-field>
              </form>
            }
          }

          <div class="step-actions">
            <button mat-button matStepperPrevious>Back</button>
            <button mat-flat-button color="primary" matStepperNext>Next</button>
          </div>
        </mat-step>

        <mat-step label="Confirm">
          <div class="summary">
            <p><strong>{{ contactForm.getRawValue().name }}</strong> · {{ contactForm.getRawValue().email }}</p>
            @if (selectedItem(); as item) {
              <p class="muted">Will be assigned: {{ item.name }} ({{ item.serviceCode }})</p>
            } @else {
              <p class="muted">No immediate service assignment.</p>
            }
          </div>

          <div class="step-actions">
            <button mat-button matStepperPrevious [disabled]="converting()">Back</button>
            <button mat-flat-button color="primary" (click)="convert()" [disabled]="converting()">
              @if (converting()) {
                <mat-spinner diameter="18" />
              } @else {
                Convert to patient
              }
            </button>
          </div>
        </mat-step>
      </mat-stepper>
    </mat-dialog-content>
  `,
  styles: `
    mat-stepper {
      width: 100%;
    }
    .muted {
      color: var(--muted-foreground);
    }
    .intro {
      margin-top: 0;
    }
    .grid {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .assignment-grid {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin-top: 12px;
    }
    .item-list {
      display: flex;
      flex-direction: column;
      gap: 6px;
      max-height: 220px;
      overflow-y: auto;
      margin-top: 12px;
    }
    .item-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 10px 12px;
      border: 1px solid var(--border);
      border-radius: 8px;
      background: none;
      cursor: pointer;
      text-align: left;
      font: inherit;
    }
    .item-row:hover {
      background: var(--muted);
    }
    .item-row.selected {
      border-color: var(--primary);
      background: var(--primary-tint);
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
      color: var(--muted-foreground);
    }
    .item-price {
      font-weight: 600;
      white-space: nowrap;
    }
    .summary {
      padding: 8px 0;
    }
    .step-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 16px;
    }
  `,
})
export class ConvertLeadDialogComponent {
  protected readonly ref = inject(MatDialogRef<ConvertLeadDialogComponent>);
  protected readonly data = inject<ConvertLeadDialogData>(MAT_DIALOG_DATA);
  private readonly catalogueService = inject(CatalogueService);
  private readonly leadsService = inject(LeadsService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly serviceType = signal<ServiceType | null>(null);
  protected readonly items = signal<CatalogueItem[]>([]);
  protected readonly selectedItem = signal<CatalogueItem | null>(null);
  protected readonly loadingItems = signal(false);
  protected readonly converting = signal(false);

  protected readonly contactForm = this.fb.nonNullable.group({
    name: [this.data.lead.name, Validators.required],
    email: [this.data.lead.email ?? '', [Validators.required, Validators.email]],
    phone: [this.data.lead.phone ?? ''],
    dateOfBirth: [null as Date | null],
    bloodGroup: [''],
    heightCm: [null as number | null],
  });

  protected readonly serviceForm = this.fb.nonNullable.group({
    startDate: [new Date()],
    price: [null as number | null],
  });

  protected switchType(type: ServiceType | null): void {
    this.serviceType.set(type);
    this.selectedItem.set(null);
    this.items.set([]);
    if (type) this.loadItems(type);
  }

  protected selectItem(item: CatalogueItem): void {
    this.selectedItem.set(item);
    this.serviceForm.controls.price.setValue(item.priceInr);
  }

  protected convert(): void {
    if (this.contactForm.invalid || this.converting()) {
      this.contactForm.markAllAsTouched();
      return;
    }
    const contact = this.contactForm.getRawValue();
    const item = this.selectedItem();
    const type = this.serviceType();
    const service = this.serviceForm.getRawValue();

    this.converting.set(true);
    this.leadsService
      .convert(this.data.lead.id, {
        name: contact.name,
        email: contact.email,
        phone: contact.phone || undefined,
        dateOfBirth: contact.dateOfBirth ? toIsoDate(contact.dateOfBirth) : undefined,
        bloodGroup: contact.bloodGroup || undefined,
        heightCm: contact.heightCm ?? undefined,
        assignServiceId: item?.id,
        serviceType: item && type ? type : undefined,
        startDate: item ? toIsoDate(service.startDate) : undefined,
        price: item ? (service.price ?? undefined) : undefined,
      })
      .subscribe({
        next: (result: ConvertLeadResponse) => {
          this.toast.success(result.message);
          this.ref.close(result);
        },
        error: (err: ApiError) => {
          this.converting.set(false);
          this.toast.error(err.error);
        },
      });
  }

  private loadItems(type: ServiceType): void {
    this.loadingItems.set(true);
    this.catalogueService.list(TYPE_TO_ROUTE[type], { status: 'published', page: 1, limit: 50 }).subscribe({
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
