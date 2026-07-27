import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { Product } from '../../core/models/product.model';

/** Detail-only dialog (centered, not a side panel, per convention). No purchase CTA — list-only pass. */
@Component({
  selector: 'app-product-detail-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, CurrencyPipe],
  template: `
    <h2 mat-dialog-title>{{ data.name }}</h2>
    <mat-dialog-content>
      @if (data.images.length) {
        <img class="hero" [src]="data.images[activeImage()]" alt="" />
        @if (data.images.length > 1) {
          <div class="thumbs">
            @for (url of data.images; track url; let i = $index) {
              <img
                [src]="url"
                alt=""
                class="thumb"
                [class.active]="i === activeImage()"
                (click)="activeImage.set(i)"
              />
            }
          </div>
        }
      }

      <p class="segment">{{ data.segmentName }}</p>
      @if (data.priceInr != null) {
        <p class="price">{{ data.priceInr | currency: 'INR' : 'symbol' : '1.0-2' }}</p>
      }
      @if (data.description) {
        <p class="description">{{ data.description }}</p>
      }
      <!-- A future "Purchase" prompt's Buy button goes here, next to/below the price. -->
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .hero {
      width: 100%;
      max-height: 320px;
      object-fit: cover;
      border-radius: 8px;
    }
    .thumbs {
      display: flex;
      gap: 8px;
      margin-top: 8px;
    }
    .thumb {
      width: 56px;
      height: 56px;
      object-fit: cover;
      border-radius: 6px;
      cursor: pointer;
      opacity: 0.6;
      border: 2px solid transparent;
    }
    .thumb.active {
      opacity: 1;
      border-color: var(--primary);
    }
    .segment {
      margin: 16px 0 4px;
      font-size: 0.85rem;
      color: var(--muted-foreground);
    }
    .price {
      font-size: 1.3rem;
      font-weight: 600;
      margin: 4px 0;
    }
    .description {
      margin-top: 8px;
      white-space: pre-wrap;
    }
  `,
})
export class ProductDetailDialogComponent {
  protected readonly data = inject<Product>(MAT_DIALOG_DATA);
  protected readonly activeImage = signal(0);
}
