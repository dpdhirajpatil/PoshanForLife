import { Component, inject, input, model, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiError } from '../../core/models/api-response.model';
import { CatalogueType } from '../../core/models/catalogue.model';
import { ToastService } from '../../core/services/toast.service';
import { CatalogueService } from './catalogue.service';

const MAX_BYTES = 5 * 1024 * 1024;
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];

/**
 * Cover-image widget: drag-drop or file picker, immediate upload to the
 * catalogue /upload endpoint, preview of the resulting public URL.
 * Two-way bound via the `url` model so the form dialog just reads it on save.
 */
@Component({
  selector: 'app-cover-image-upload',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div
      class="dropzone"
      [class.dragover]="dragOver()"
      [class.has-image]="!!url()"
      (dragover)="onDragOver($event)"
      (dragleave)="dragOver.set(false)"
      (drop)="onDrop($event)"
      (click)="fileInput.click()"
      role="button"
      tabindex="0"
      (keydown.enter)="fileInput.click()"
      aria-label="Upload cover image"
    >
      @if (uploading()) {
        <mat-spinner diameter="32" />
        <span class="hint">Uploading…</span>
      } @else if (url()) {
        <img [src]="url()" alt="Cover image preview" />
      } @else {
        <mat-icon>add_photo_alternate</mat-icon>
        <span class="hint">Drag an image here or click to browse</span>
        <span class="sub-hint">JPEG, PNG, WebP or GIF · max 5 MB</span>
      }
    </div>

    @if (url() && !uploading()) {
      <div class="actions">
        <button mat-button type="button" (click)="fileInput.click()">
          <mat-icon>swap_horiz</mat-icon>
          Replace
        </button>
        <button mat-button type="button" (click)="clear()">
          <mat-icon>delete_outline</mat-icon>
          Remove
        </button>
      </div>
    }

    <input
      #fileInput
      type="file"
      hidden
      [accept]="accept"
      (change)="onFileSelected($event)"
    />
  `,
  styles: `
    .dropzone {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 6px;
      min-height: 140px;
      border: 2px dashed rgba(0, 0, 0, 0.25);
      border-radius: 8px;
      cursor: pointer;
      padding: 12px;
      text-align: center;
      transition: border-color 0.15s, background 0.15s;
    }
    .dropzone.dragover {
      border-color: var(--primary);
      background: rgba(45, 138, 104, 0.06);
    }
    .dropzone.has-image {
      padding: 0;
      overflow: hidden;
    }
    .dropzone img {
      max-width: 100%;
      max-height: 180px;
      object-fit: cover;
      border-radius: 6px;
    }
    .hint {
      font-size: 0.85rem;
      color: rgba(0, 0, 0, 0.6);
    }
    .sub-hint {
      font-size: 0.75rem;
      color: rgba(0, 0, 0, 0.45);
    }
    .actions {
      display: flex;
      justify-content: center;
      gap: 8px;
      margin-top: 4px;
    }
  `,
})
export class CoverImageUploadComponent {
  private readonly catalogueService = inject(CatalogueService);
  private readonly toast = inject(ToastService);

  readonly catalogueType = input.required<CatalogueType>();
  /** Public URL of the uploaded image; '' when none. */
  readonly url = model<string>('');

  protected readonly uploading = signal(false);
  protected readonly dragOver = signal(false);
  protected readonly accept = ACCEPTED.join(',');

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) this.upload(file);
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file
    if (file) this.upload(file);
  }

  protected clear(): void {
    this.url.set('');
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
    this.catalogueService.uploadCover(this.catalogueType(), file).subscribe({
      next: ({ url }) => {
        this.uploading.set(false);
        this.url.set(url);
      },
      error: (err: ApiError) => {
        this.uploading.set(false);
        this.toast.error(err.error);
      },
    });
  }
}
