import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ApiError } from '../../core/models/api-response.model';
import {
  CATALOGUE_STATUSES,
  CATALOGUE_TYPE_META,
  CatalogueItem,
  CatalogueStatus,
  CatalogueType,
} from '../../core/models/catalogue.model';
import { ToastService } from '../../core/services/toast.service';
import { applyServerFieldErrors } from '../../core/utils/form-errors';
import { CatalogueService, SaveCatalogueItemPayload } from './catalogue.service';
import { CoverImageUploadComponent } from './cover-image-upload.component';

export interface CatalogueItemFormDialogData {
  type: CatalogueType;
  item?: CatalogueItem; // absent → create mode
}

/**
 * Create/edit dialog shared by all three catalogue types: the shared fields
 * are one common form-group; only the duration (and, for challenges, the
 * goal) field is swapped in per type. ADMIN-only — the table never opens it
 * for doctors.
 */
@Component({
  selector: 'app-catalogue-item-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    CoverImageUploadComponent,
  ],
  template: `
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} {{ meta.singular }}</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="catalogue-form" (ngSubmit)="save()">
        <div class="grid">
          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Name</mat-label>
            <input matInput formControlName="name" required />
            @if (form.controls.name.hasError('server')) {
              <mat-error>{{ form.controls.name.getError('server') }}</mat-error>
            } @else if (form.controls.name.invalid) {
              <mat-error>Name must be at least 2 characters</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Service code</mat-label>
            <input matInput formControlName="serviceCode" required />
            <mat-hint>Unique across programmes, sessions and challenges</mat-hint>
            @if (form.controls.serviceCode.hasError('server')) {
              <mat-error>{{ form.controls.serviceCode.getError('server') }}</mat-error>
            } @else if (form.controls.serviceCode.invalid) {
              <mat-error>Service code is required (max 64 characters)</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Category</mat-label>
            <input matInput formControlName="type" required placeholder="e.g. Weight loss" />
            @if (form.controls.type.hasError('server')) {
              <mat-error>{{ form.controls.type.getError('server') }}</mat-error>
            } @else if (form.controls.type.invalid) {
              <mat-error>Category is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Price (INR)</mat-label>
            <input matInput formControlName="priceInr" type="number" min="0" step="0.01" />
            <span matTextPrefix>₹&nbsp;</span>
            @if (form.controls.priceInr.hasError('server')) {
              <mat-error>{{ form.controls.priceInr.getError('server') }}</mat-error>
            } @else if (form.controls.priceInr.invalid) {
              <mat-error>Price must be 0 or more</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>{{ meta.durationLabel }}</mat-label>
            <input matInput formControlName="duration" type="number" min="1" />
            @if (form.controls.duration.hasError('server')) {
              <mat-error>{{ form.controls.duration.getError('server') }}</mat-error>
            } @else if (form.controls.duration.invalid) {
              <mat-error>{{ meta.durationLabel }} must be a positive number</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Status</mat-label>
            <mat-select formControlName="status">
              @for (s of statuses; track s) {
                <mat-option [value]="s">{{ s }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          @if (data.type === 'challenges') {
            <mat-form-field appearance="outline" class="span-2">
              <mat-label>Goal description</mat-label>
              <textarea matInput formControlName="goalDescription" rows="2" required></textarea>
              @if (form.controls.goalDescription.hasError('server')) {
                <mat-error>{{ form.controls.goalDescription.getError('server') }}</mat-error>
              } @else if (form.controls.goalDescription.invalid) {
                <mat-error>Goal description is required for challenges</mat-error>
              }
            </mat-form-field>
          }

          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Description</mat-label>
            <textarea matInput formControlName="description" rows="3"></textarea>
          </mat-form-field>
        </div>

        <h3>Cover image</h3>
        <app-cover-image-upload [catalogueType]="data.type" [(url)]="coverUrl" />
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()" [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          {{ isEdit ? 'Save changes' : 'Create ' + meta.singular }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .catalogue-form {
      padding-top: 8px;
      min-width: 560px;
    }
    .catalogue-form h3 {
      margin: 16px 0 8px;
      font-size: 0.95rem;
      color: var(--muted-foreground);
    }
    .grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px 16px;
    }
    .span-2 {
      grid-column: span 2;
    }
    mat-select {
      text-transform: capitalize;
    }
    mat-option {
      text-transform: capitalize;
    }
  `,
})
export class CatalogueItemFormDialogComponent {
  protected readonly ref = inject(MatDialogRef<CatalogueItemFormDialogComponent>);
  protected readonly data = inject<CatalogueItemFormDialogData>(MAT_DIALOG_DATA);
  private readonly catalogueService = inject(CatalogueService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly meta = CATALOGUE_TYPE_META[this.data.type];
  protected readonly isEdit = !!this.data.item;
  protected readonly saving = signal(false);
  protected readonly statuses = CATALOGUE_STATUSES;
  protected readonly coverUrl = signal(this.data.item?.coverImageUrl ?? '');

  protected readonly form = this.fb.nonNullable.group({
    name: [this.data.item?.name ?? '', [Validators.required, Validators.minLength(2)]],
    serviceCode: [
      this.data.item?.serviceCode ?? '',
      [Validators.required, Validators.maxLength(64)],
    ],
    type: [this.data.item?.type ?? '', [Validators.required, Validators.maxLength(100)]],
    priceInr: [
      this.data.item?.priceInr ?? (null as number | null),
      [Validators.required, Validators.min(0)],
    ],
    duration: [
      (this.data.item?.[this.meta.durationField] ?? null) as number | null,
      [Validators.required, Validators.min(1)],
    ],
    status: [this.data.item?.status ?? ('draft' as CatalogueStatus)],
    goalDescription: [
      this.data.item?.goalDescription ?? '',
      this.data.type === 'challenges' ? [Validators.required] : [],
    ],
    description: [this.data.item?.description ?? ''],
  });

  save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const payload: SaveCatalogueItemPayload = {
      name: v.name,
      serviceCode: v.serviceCode.trim(),
      type: v.type,
      priceInr: v.priceInr!,
      description: v.description || undefined,
      // "" clears a previously stored image on update
      coverImageUrl: this.coverUrl() || (this.isEdit ? '' : undefined),
      status: v.status,
      [this.meta.durationField]: v.duration!,
      ...(this.data.type === 'challenges' ? { goalDescription: v.goalDescription } : {}),
    };

    const request = this.isEdit
      ? this.catalogueService.update(this.data.type, this.data.item!.id, payload)
      : this.catalogueService.create(this.data.type, payload);

    this.saving.set(true);
    request.subscribe({
      next: (item) => {
        this.toast.success(
          `${item.name} ${this.isEdit ? 'updated' : 'created'}`,
        );
        this.ref.close(item);
      },
      error: (err: ApiError) => {
        this.saving.set(false);
        if (!this.applyDurationServerError(err) && !applyServerFieldErrors(this.form, err)) {
          this.toast.error(err.error);
        }
      },
    });
  }

  /** The backend reports duration errors by their per-type field name. */
  private applyDurationServerError(err: ApiError): boolean {
    if (err.code !== 'VALIDATION_ERROR' || typeof err.details !== 'object' || !err.details) {
      return false;
    }
    const message = (err.details as Record<string, unknown>)[this.meta.durationField];
    if (message === undefined) {
      return false;
    }
    this.form.controls.duration.setErrors({ server: String(message) });
    this.form.controls.duration.markAsTouched();
    return true;
  }
}
