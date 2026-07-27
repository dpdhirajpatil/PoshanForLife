import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ApiError } from '../../core/models/api-response.model';
import { PRODUCT_STATUSES, Product, ProductSegment, ProductStatus } from '../../core/models/product.model';
import { ToastService } from '../../core/services/toast.service';
import { applyServerFieldErrors } from '../../core/utils/form-errors';
import { SidePanelHandleComponent } from '../../shared/side-panel-handle.component';
import { ProductImagesUploadComponent } from './product-images-upload.component';
import { ProductsService, SaveProductPayload } from './products.service';

export interface ProductFormDialogData {
  segments: ProductSegment[];
  item?: Product; // absent → create mode
  defaultSegmentId?: string;
}

/**
 * Create/edit dialog. Images are managed live against the backend (each
 * upload/remove is immediate) rather than staged locally, since the
 * upload-image endpoint needs an existing product id — so on first create
 * the dialog saves the core fields, then flips in place into "edit" mode
 * (same instance, now with an id) to reveal the image gallery instead of
 * closing.
 */
@Component({
  selector: 'app-product-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    ProductImagesUploadComponent,
    SidePanelHandleComponent,
  ],
  template: `
    <app-side-panel-handle />
    <h2 mat-dialog-title>{{ isEdit() ? 'Edit product' : 'Add product' }}</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="product-form">
        <div class="grid">
          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Segment</mat-label>
            <mat-select formControlName="segmentId" required>
              @for (segment of data.segments; track segment.id) {
                <mat-option [value]="segment.id">{{ segment.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

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
            <mat-label>Price (INR)</mat-label>
            <input matInput formControlName="priceInr" type="number" min="0" step="0.01" />
            <span matTextPrefix>₹&nbsp;</span>
            <mat-hint>Informational only — no purchase flow yet</mat-hint>
            @if (form.controls.priceInr.hasError('server')) {
              <mat-error>{{ form.controls.priceInr.getError('server') }}</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>SKU / variant code</mat-label>
            <input matInput formControlName="sku" />
            @if (form.controls.sku.hasError('server')) {
              <mat-error>{{ form.controls.sku.getError('server') }}</mat-error>
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

          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Description</mat-label>
            <textarea matInput formControlName="description" rows="3"></textarea>
          </mat-form-field>
        </div>

        <h3>Images</h3>
        @if (createdProduct(); as product) {
          <app-product-images-upload [productId]="product.id" [images]="product.images" (imagesChange)="onImagesChange($event)" />
        } @else {
          <p class="hint">Save the product first, then add its images here.</p>
        }
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close(createdProduct() ?? undefined)" [disabled]="saving()">
        {{ createdProduct() ? 'Done' : 'Cancel' }}
      </button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          {{ createdProduct() ? 'Save changes' : 'Create product' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .product-form {
      padding-top: 8px;
      width: 100%;
    }
    .product-form h3 {
      margin: 16px 0 8px;
      font-size: 0.95rem;
      color: var(--muted-foreground);
    }
    .hint {
      font-size: 0.85rem;
      color: var(--muted-foreground);
      margin: 0;
    }
    .grid {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .span-2 {
      width: 100%;
    }
    mat-select {
      text-transform: capitalize;
    }
    mat-option {
      text-transform: capitalize;
    }
  `,
})
export class ProductFormDialogComponent {
  protected readonly ref = inject(MatDialogRef<ProductFormDialogComponent>);
  protected readonly data = inject<ProductFormDialogData>(MAT_DIALOG_DATA);
  private readonly productsService = inject(ProductsService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly saving = signal(false);
  protected readonly statuses = PRODUCT_STATUSES;
  /** Set once a create-mode save succeeds, flipping the dialog into edit mode in place. */
  protected readonly createdProduct = signal<Product | undefined>(this.data.item);

  protected isEdit(): boolean {
    return !!this.data.item;
  }

  protected readonly form = this.fb.nonNullable.group({
    segmentId: [
      this.data.item?.segmentId ?? this.data.defaultSegmentId ?? '',
      [Validators.required],
    ],
    name: [this.data.item?.name ?? '', [Validators.required, Validators.minLength(2)]],
    description: [this.data.item?.description ?? ''],
    priceInr: [this.data.item?.priceInr ?? (null as number | null), [Validators.min(0)]],
    sku: [this.data.item?.sku ?? ''],
    status: [this.data.item?.status ?? ('draft' as ProductStatus)],
  });

  protected onImagesChange(images: string[]): void {
    const current = this.createdProduct();
    if (current) this.createdProduct.set({ ...current, images });
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const payload: SaveProductPayload = {
      segmentId: v.segmentId,
      name: v.name,
      description: v.description || undefined,
      priceInr: v.priceInr,
      sku: v.sku || undefined,
      status: v.status,
    };

    const existing = this.createdProduct();
    const request = existing
      ? this.productsService.update(existing.id, payload)
      : this.productsService.create(payload as SaveProductPayload & { segmentId: string; name: string });

    this.saving.set(true);
    request.subscribe({
      next: (product) => {
        this.saving.set(false);
        this.toast.success(`${product.name} ${existing ? 'updated' : 'created'}`);
        this.createdProduct.set(product);
        if (existing) this.ref.close(product);
        // create mode: stay open so the admin can add images next.
      },
      error: (err: ApiError) => {
        this.saving.set(false);
        if (!applyServerFieldErrors(this.form, err)) {
          this.toast.error(err.error);
        }
      },
    });
  }
}
