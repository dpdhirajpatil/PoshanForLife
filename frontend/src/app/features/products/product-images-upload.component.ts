import { Component, inject, input, model, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiError } from '../../core/models/api-response.model';
import { ToastService } from '../../core/services/toast.service';
import { ProductsService } from './products.service';

const MAX_BYTES = 5 * 1024 * 1024;
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];

/**
 * Multi-image gallery widget — extends cover-image-upload.component's
 * pattern (same validation/limits) for products, which need several
 * angles/flavor variants rather than a single cover image. Each upload/
 * remove is an immediate backend call (there's no local-only staging),
 * since the upload-image endpoint requires an existing product id.
 */
@Component({
  selector: 'app-product-images-upload',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="gallery">
      @for (url of images(); track url) {
        <div class="tile">
          <img [src]="url" alt="Product image" />
          <button
            mat-icon-button
            type="button"
            class="remove"
            (click)="removeImage(url)"
            [disabled]="busy()"
            aria-label="Remove image"
          >
            <mat-icon>close</mat-icon>
          </button>
        </div>
      }
      <div class="tile add-tile" (click)="fileInput.click()" role="button" tabindex="0" (keydown.enter)="fileInput.click()">
        @if (uploading()) {
          <mat-spinner diameter="24" />
        } @else {
          <mat-icon>add_photo_alternate</mat-icon>
          <span>Add image</span>
        }
      </div>
    </div>
    <span class="sub-hint">JPEG, PNG, WebP or GIF · max 5 MB each</span>

    <input #fileInput type="file" hidden [accept]="accept" (change)="onFileSelected($event)" />
  `,
  styles: `
    .gallery {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }
    .tile {
      position: relative;
      width: 96px;
      height: 96px;
      border-radius: 8px;
      overflow: hidden;
      border: 1px solid var(--border);
    }
    .tile img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
    .remove {
      position: absolute;
      top: 2px;
      right: 2px;
      width: 24px;
      height: 24px;
      line-height: 24px;
      background: rgb(0 0 0 / 0.5);
      color: #fff;
    }
    .remove mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }
    .add-tile {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 4px;
      cursor: pointer;
      border: 2px dashed var(--border);
      color: var(--muted-foreground);
      font-size: 0.72rem;
      text-align: center;
      padding: 4px;
    }
    .add-tile:hover {
      border-color: var(--primary);
    }
    .sub-hint {
      display: block;
      margin-top: 6px;
      font-size: 0.75rem;
      color: var(--muted-foreground);
    }
  `,
})
export class ProductImagesUploadComponent {
  private readonly productsService = inject(ProductsService);
  private readonly toast = inject(ToastService);

  readonly productId = input.required<string>();
  readonly images = model<string[]>([]);

  protected readonly uploading = signal(false);
  protected readonly busy = signal(false);
  protected readonly accept = ACCEPTED.join(',');

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file
    if (file) this.upload(file);
  }

  protected removeImage(url: string): void {
    this.busy.set(true);
    this.productsService.removeImage(this.productId(), url).subscribe({
      next: (product) => {
        this.busy.set(false);
        this.images.set(product.images);
      },
      error: (err: ApiError) => {
        this.busy.set(false);
        this.toast.error(err.error);
      },
    });
  }

  private upload(file: File): void {
    if (!ACCEPTED.includes(file.type)) {
      this.toast.error('Only JPEG, PNG, WebP or GIF images are allowed');
      return;
    }
    if (file.size > MAX_BYTES) {
      this.toast.error('Image must be 5 MB or smaller');
      return;
    }
    this.uploading.set(true);
    this.productsService.uploadImage(this.productId(), file).subscribe({
      next: (product) => {
        this.uploading.set(false);
        this.images.set(product.images);
      },
      error: (err: ApiError) => {
        this.uploading.set(false);
        this.toast.error(err.error);
      },
    });
  }
}
