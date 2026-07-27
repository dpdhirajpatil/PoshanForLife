import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiError } from '../../core/models/api-response.model';
import { Product, ProductSegment } from '../../core/models/product.model';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { openSidePanel } from '../../shared/side-panel';
import { TrapeziumBarComponent } from '../../shared/trapezium-bar.component';
import { ManageSegmentsDialogComponent } from './manage-segments-dialog.component';
import { ProductDetailDialogComponent } from './product-detail-dialog.component';
import { ProductFormDialogComponent, ProductFormDialogData } from './product-form-dialog.component';
import { ProductsService } from './products.service';

interface SegmentSection {
  segment: ProductSegment;
  products: Product[];
}

/**
 * Browse view for all roles, with ADMIN management layered on the SAME
 * page (add/edit/delete/reorder + "Manage segments") rather than a
 * separate admin-only page, per the feature's explicit scope.
 */
@Component({
  selector: 'app-products-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatMenuModule,
    MatDialogModule,
    MatProgressBarModule,
    MatTooltipModule,
    TrapeziumBarComponent,
  ],
  template: `
    <div class="page-header">
      <div>
        <h1>Products</h1>
        <p class="subtitle">Meal replacements, supplements and more</p>
      </div>
      <div class="header-actions">
        <mat-form-field appearance="outline" class="search" subscriptSizing="dynamic">
          <mat-label>Search</mat-label>
          <input matInput [formControl]="search" placeholder="Search all products" />
          <mat-icon matPrefix>search</mat-icon>
        </mat-form-field>
        @if (isAdmin) {
          <button mat-stroked-button (click)="openManageSegments()">
            <mat-icon>tune</mat-icon>
            Manage segments
          </button>
          <button mat-flat-button color="primary" (click)="openCreate()" [disabled]="segments().length === 0">
            <mat-icon>add</mat-icon>
            Add product
          </button>
        }
      </div>
    </div>

    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (!loading() && segments().length === 0) {
      <div class="empty-state global">
        <div class="icon-circle">
          <mat-icon>inventory_2</mat-icon>
        </div>
        <h3>No product segments yet</h3>
        @if (isAdmin) {
          <p>Create a segment (e.g. "Meal Replacements") to start adding products.</p>
          <button mat-flat-button color="primary" (click)="openManageSegments()">Manage segments</button>
        } @else {
          <p>Check back soon.</p>
        }
      </div>
    }

    @for (section of sections(); track section.segment.id) {
      <section class="segment-section">
        <div class="segment-heading">
          <app-trapezium-bar color="green">{{ section.segment.name }}</app-trapezium-bar>
          <span class="segment-count">{{ section.products.length }} product{{ section.products.length === 1 ? '' : 's' }}</span>
        </div>

        @if (section.products.length === 0) {
          <div class="empty-state">
            <div class="icon-circle small">
              <mat-icon>shopping_bag</mat-icon>
            </div>
            <p>No products in this category yet{{ search.value ? ' matching your search' : '' }}.</p>
          </div>
        } @else {
          <div class="card-grid">
            @for (product of section.products; track product.id; let i = $index; let count = $count) {
              <div class="product-card" (click)="openDetail(product)">
                <div class="thumb-wrap">
                  @if (product.images.length) {
                    <img class="thumb" [src]="product.images[0]" alt="" />
                  } @else {
                    <div class="thumb placeholder">
                      <mat-icon>shopping_bag</mat-icon>
                    </div>
                  }
                  @if (isAdmin && product.status === 'draft') {
                    <span class="draft-badge">Draft</span>
                  }
                </div>
                <div class="card-body">
                  <span class="name">{{ product.name }}</span>
                  @if (product.priceInr != null) {
                    <span class="price">{{ product.priceInr | currency: 'INR' : 'symbol' : '1.0-2' }}</span>
                  }
                  <!-- A future "Purchase" prompt's Buy button belongs here. -->
                </div>

                @if (isAdmin) {
                  <div class="card-admin-actions" (click)="$event.stopPropagation()">
                    <button mat-icon-button [disabled]="i === 0" (click)="move(product, section.products, -1)" aria-label="Move up">
                      <mat-icon>arrow_upward</mat-icon>
                    </button>
                    <button mat-icon-button [disabled]="i === count - 1" (click)="move(product, section.products, 1)" aria-label="Move down">
                      <mat-icon>arrow_downward</mat-icon>
                    </button>
                    <button mat-icon-button [matMenuTriggerFor]="rowMenu" [matMenuTriggerData]="{ product }" aria-label="More actions">
                      <mat-icon>more_vert</mat-icon>
                    </button>
                  </div>
                }
              </div>
            }
          </div>
        }
      </section>
    }

    <mat-menu #rowMenu="matMenu">
      <ng-template matMenuContent let-product="product">
        <button mat-menu-item (click)="openEdit(product)">
          <mat-icon>edit</mat-icon>
          Edit
        </button>
        <button mat-menu-item (click)="confirmDelete(product)">
          <mat-icon>delete_outline</mat-icon>
          Delete
        </button>
      </ng-template>
    </mat-menu>
  `,
  styles: `
    :host {
      display: block;
      background: var(--muted);
      min-height: 100%;
    }
    .page-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
      padding: 28px 24px 20px;
    }
    .subtitle {
      margin: 4px 0 0;
      color: var(--muted-foreground);
      font-size: 0.92rem;
    }
    .header-actions {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
    }
    .search {
      width: 260px;
      background: var(--card);
      border-radius: 8px;
    }
    .segment-section {
      padding: 12px 24px 32px;
    }
    .segment-heading {
      display: flex;
      align-items: center;
      gap: 14px;
      margin-bottom: 18px;
    }
    .segment-heading app-trapezium-bar {
      font-family: var(--font-display);
      font-weight: 700;
      font-size: 0.95rem;
      letter-spacing: 0.02em;
    }
    .segment-count {
      color: var(--muted-foreground);
      font-size: 0.85rem;
    }
    .card-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 20px;
    }
    .product-card {
      position: relative;
      display: flex;
      flex-direction: column;
      border: 1px solid var(--border);
      border-radius: var(--radius);
      overflow: hidden;
      cursor: pointer;
      background: var(--card);
      transition:
        box-shadow 0.18s ease,
        transform 0.18s ease,
        border-color 0.18s ease;
    }
    .product-card:hover {
      box-shadow: 0 10px 24px rgb(38 43 100 / 0.1);
      border-color: var(--brand-green);
      transform: translateY(-2px);
    }
    .thumb-wrap {
      position: relative;
      background: linear-gradient(180deg, var(--brand-green-lightest) 0%, var(--muted) 100%);
    }
    .thumb {
      display: block;
      width: 100%;
      height: 180px;
      object-fit: contain;
      padding: 18px;
      box-sizing: border-box;
    }
    .thumb.placeholder {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 180px;
      color: var(--brand-green-dark);
    }
    .thumb.placeholder mat-icon {
      font-size: 44px;
      width: 44px;
      height: 44px;
    }
    .card-body {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 14px 16px 16px;
      border-top: 1px solid var(--border);
    }
    .name {
      font-weight: 600;
      color: var(--foreground);
      line-height: 1.3;
    }
    .price {
      color: var(--brand-navy);
      font-weight: 700;
      font-size: 0.95rem;
    }
    .draft-badge {
      position: absolute;
      top: 10px;
      left: 10px;
      padding: 2px 10px;
      border-radius: 10px;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      background: var(--badge-amber-bg);
      color: var(--badge-amber-fg);
    }
    .card-admin-actions {
      position: absolute;
      top: 8px;
      right: 8px;
      display: flex;
      background: rgb(255 255 255 / 0.92);
      border-radius: 10px;
      box-shadow: 0 2px 8px rgb(0 0 0 / 0.1);
    }
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      gap: 8px;
      padding: 40px 16px;
      color: var(--muted-foreground);
      background: var(--card);
      border: 1px dashed var(--border);
      border-radius: var(--radius);
    }
    .empty-state.global {
      padding: 72px 16px;
      margin: 0 24px;
    }
    .icon-circle {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: var(--primary);
      color: var(--primary-foreground);
    }
    .icon-circle.small {
      width: 48px;
      height: 48px;
    }
    .icon-circle mat-icon {
      font-size: 32px;
      width: 32px;
      height: 32px;
    }
    .icon-circle.small mat-icon {
      font-size: 24px;
      width: 24px;
      height: 24px;
    }
    .empty-state h3 {
      margin: 4px 0 0;
    }
    .empty-state p {
      margin: 0;
      max-width: 320px;
    }
  `,
})
export class ProductsPageComponent {
  private readonly productsService = inject(ProductsService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  protected readonly isAdmin = inject(AuthService).isAdmin();

  protected readonly segments = signal<ProductSegment[]>([]);
  protected readonly products = signal<Product[]>([]);
  protected readonly loading = signal(false);
  protected readonly search = new FormControl('', { nonNullable: true });

  protected readonly sections = computed<SegmentSection[]>(() =>
    this.segments()
      .filter((s) => s.status === 'active')
      .map((segment) => ({
        segment,
        products: this.products().filter((p) => p.segmentId === segment.id),
      })),
  );

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.load());
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.productsService.listSegments().subscribe({
      next: (segments) => this.segments.set(segments),
      error: (err: ApiError) => this.toast.error(err.error),
    });
    this.productsService
      .list({ search: this.search.value || undefined, page: 1, limit: 200 })
      .subscribe({
        next: (result) => {
          this.products.set(result.data.products);
          this.loading.set(false);
        },
        error: (err: ApiError) => {
          this.loading.set(false);
          this.toast.error(err.error);
        },
      });
  }

  protected openManageSegments(): void {
    openSidePanel(this.dialog, ManageSegmentsDialogComponent)
      .afterClosed()
      .subscribe((changed) => changed && this.load());
  }

  protected openCreate(): void {
    openSidePanel(this.dialog, ProductFormDialogComponent, {
      data: { segments: this.segments().filter((s) => s.status === 'active') } satisfies ProductFormDialogData,
    })
      .afterClosed()
      .subscribe((saved) => saved && this.load());
  }

  protected openEdit(product: Product): void {
    openSidePanel(this.dialog, ProductFormDialogComponent, {
      data: {
        segments: this.segments().filter((s) => s.status === 'active'),
        item: product,
      } satisfies ProductFormDialogData,
    })
      .afterClosed()
      .subscribe((saved) => saved && this.load());
  }

  protected openDetail(product: Product): void {
    this.dialog.open(ProductDetailDialogComponent, { data: product, maxWidth: '560px' });
  }

  /** Swaps displayOrder with the adjacent product within the same segment's already-sorted list. */
  protected move(product: Product, siblings: Product[], direction: -1 | 1): void {
    const index = siblings.findIndex((p) => p.id === product.id);
    const neighbor = siblings[index + direction];
    if (!neighbor) return;
    this.productsService.update(product.id, { displayOrder: neighbor.displayOrder }).subscribe();
    this.productsService.update(neighbor.id, { displayOrder: product.displayOrder }).subscribe({
      next: () => this.load(),
      error: (err: ApiError) => this.toast.error(err.error),
    });
  }

  protected confirmDelete(product: Product): void {
    const data: ConfirmDialogData = {
      title: 'Delete product',
      message: `"${product.name}" will be permanently removed.`,
      confirmLabel: 'Delete',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.productsService.remove(product.id).subscribe({
          next: () => {
            this.toast.success(`${product.name} deleted`);
            this.load();
          },
          error: (err: ApiError) => this.toast.error(err.error),
        });
      });
  }
}
