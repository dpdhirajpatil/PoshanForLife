import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { Product } from '../../core/models/product.model';

/** Detail-only dialog (centered, not a side panel, per convention). No purchase CTA — list-only pass. */
@Component({
  selector: 'app-product-detail-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, CurrencyPipe],
  template: `
    <h2 mat-dialog-title>{{ data.name }}</h2>
    <mat-dialog-content>
      <div class="hero-wrap">
        @if (data.images.length) {
          <img class="hero" [src]="data.images[activeImage()]" alt="" />
        } @else {
          <div class="hero placeholder">
            <mat-icon>shopping_bag</mat-icon>
          </div>
        }
      </div>
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

      <span class="segment-chip">{{ data.segmentName }}</span>
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
    .hero-wrap {
      background: linear-gradient(180deg, var(--brand-green-lightest) 0%, var(--muted) 100%);
      border-radius: var(--radius);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .hero {
      width: 100%;
      max-height: 320px;
      object-fit: contain;
      padding: 24px;
      box-sizing: border-box;
    }
    .hero.placeholder {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 240px;
      color: var(--brand-green-dark);
    }
    .hero.placeholder mat-icon {
      font-size: 56px;
      width: 56px;
      height: 56px;
    }
    .thumbs {
      display: flex;
      gap: 8px;
      margin-top: 10px;
    }
    .thumb {
      width: 56px;
      height: 56px;
      object-fit: cover;
      border-radius: 6px;
      cursor: pointer;
      opacity: 0.6;
      border: 2px solid transparent;
      background: var(--muted);
    }
    .thumb.active {
      opacity: 1;
      border-color: var(--brand-green);
    }
    .segment-chip {
      display: inline-block;
      margin: 18px 0 8px;
      padding: 3px 12px;
      border-radius: 12px;
      font-size: 0.78rem;
      font-weight: 600;
      background: var(--badge-green-bg);
      color: var(--badge-green-fg);
    }
    .price {
      font-size: 1.4rem;
      font-weight: 700;
      color: var(--brand-navy);
      margin: 6px 0;
    }
    .description {
      margin-top: 8px;
      white-space: pre-wrap;
      color: var(--foreground);
    }
  `,
})
export class ProductDetailDialogComponent {
  protected readonly data = inject<Product>(MAT_DIALOG_DATA);
  protected readonly activeImage = signal(0);
}
