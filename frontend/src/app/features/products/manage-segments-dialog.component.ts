import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiError } from '../../core/models/api-response.model';
import { ProductSegment } from '../../core/models/product.model';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { SidePanelHandleComponent } from '../../shared/side-panel-handle.component';
import { ProductsService } from './products.service';

/**
 * ADMIN-only segment management: add, rename, reorder (up/down), archive/
 * reactivate, delete (blocked by the backend while the segment still has
 * products). Closes with `true` if anything changed, so the caller knows
 * to reload its own segment/product list.
 */
@Component({
  selector: 'app-manage-segments-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    SidePanelHandleComponent,
  ],
  template: `
    <app-side-panel-handle />
    <h2 mat-dialog-title>Manage segments</h2>

    <mat-dialog-content>
      <form class="add-form" [formGroup]="addForm" (ngSubmit)="addSegment()">
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>New segment name</mat-label>
          <input matInput formControlName="name" placeholder="e.g. Snacks" />
          @if (addForm.controls.name.hasError('server')) {
            <mat-error>{{ addForm.controls.name.getError('server') }}</mat-error>
          }
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="adding()">
          @if (adding()) {
            <mat-spinner diameter="18" />
          } @else {
            Add
          }
        </button>
      </form>

      <div class="segment-list">
        @for (segment of segments(); track segment.id; let i = $index; let count = $count) {
          <div class="segment-row" [class.archived]="segment.status === 'archived'">
            @if (editingId() === segment.id) {
              <input
                class="rename-input"
                [formControl]="renameControl"
                (keydown.enter)="commitRename(segment)"
                (blur)="commitRename(segment)"
              />
            } @else {
              <span class="name" (dblclick)="startRename(segment)">{{ segment.name }}</span>
            }
            <span class="count">{{ segment.publishedProductCount }} published</span>

            <button mat-icon-button [disabled]="i === 0" (click)="move(segment, -1)" aria-label="Move up">
              <mat-icon>arrow_upward</mat-icon>
            </button>
            <button mat-icon-button [disabled]="i === count - 1" (click)="move(segment, 1)" aria-label="Move down">
              <mat-icon>arrow_downward</mat-icon>
            </button>
            <button mat-icon-button (click)="startRename(segment)" aria-label="Rename">
              <mat-icon>edit</mat-icon>
            </button>
            <button
              mat-icon-button
              (click)="toggleArchive(segment)"
              [matTooltip]="segment.status === 'archived' ? 'Reactivate' : 'Archive'"
            >
              <mat-icon>{{ segment.status === 'archived' ? 'unarchive' : 'archive' }}</mat-icon>
            </button>
            <span [matTooltip]="'Move or delete its products first'" [matTooltipDisabled]="segment.publishedProductCount === 0">
              <button
                mat-icon-button
                [disabled]="segment.publishedProductCount > 0"
                (click)="confirmDelete(segment)"
                aria-label="Delete"
              >
                <mat-icon>delete_outline</mat-icon>
              </button>
            </span>
          </div>
        } @empty {
          <p class="hint">No segments yet — add one above.</p>
        }
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-flat-button color="primary" (click)="ref.close(changed())">Done</button>
    </mat-dialog-actions>
  `,
  styles: `
    .add-form {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      margin-bottom: 16px;
    }
    .add-form mat-form-field {
      flex: 1;
    }
    .segment-list {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .segment-row {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 6px 4px;
      border-radius: 8px;
    }
    .segment-row.archived {
      opacity: 0.6;
    }
    .name {
      flex: 1;
      font-weight: 500;
      cursor: text;
    }
    .rename-input {
      flex: 1;
      font: inherit;
      border: 1px solid var(--border);
      border-radius: 4px;
      padding: 4px 8px;
      background: var(--background);
      color: var(--foreground);
    }
    .count {
      font-size: 0.78rem;
      color: var(--muted-foreground);
      white-space: nowrap;
    }
    .hint {
      color: var(--muted-foreground);
      font-size: 0.88rem;
    }
  `,
})
export class ManageSegmentsDialogComponent {
  protected readonly ref = inject(MatDialogRef<ManageSegmentsDialogComponent>);
  private readonly productsService = inject(ProductsService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(MatDialog);
  private readonly fb = inject(FormBuilder);

  protected readonly segments = signal<ProductSegment[]>([]);
  protected readonly adding = signal(false);
  protected readonly changed = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly renameControl = this.fb.nonNullable.control('');

  protected readonly addForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
  });

  constructor() {
    this.load();
  }

  private load(): void {
    this.productsService.listSegments(true).subscribe({
      next: (segments) => this.segments.set(segments),
      error: (err: ApiError) => this.toast.error(err.error),
    });
  }

  protected addSegment(): void {
    if (this.addForm.invalid || this.adding()) return;
    this.adding.set(true);
    this.productsService.createSegment({ name: this.addForm.controls.name.value }).subscribe({
      next: () => {
        this.adding.set(false);
        this.addForm.reset({ name: '' });
        this.changed.set(true);
        this.load();
      },
      error: (err: ApiError) => {
        this.adding.set(false);
        this.addForm.controls.name.setErrors({ server: err.error });
      },
    });
  }

  protected startRename(segment: ProductSegment): void {
    this.editingId.set(segment.id);
    this.renameControl.setValue(segment.name);
  }

  protected commitRename(segment: ProductSegment): void {
    if (this.editingId() !== segment.id) return;
    this.editingId.set(null);
    const name = this.renameControl.value.trim();
    if (!name || name === segment.name) return;
    this.productsService.updateSegment(segment.id, { name }).subscribe({
      next: () => {
        this.changed.set(true);
        this.load();
      },
      error: (err: ApiError) => this.toast.error(err.error),
    });
  }

  protected toggleArchive(segment: ProductSegment): void {
    const status = segment.status === 'archived' ? 'active' : 'archived';
    this.productsService.updateSegment(segment.id, { status }).subscribe({
      next: () => {
        this.changed.set(true);
        this.load();
      },
      error: (err: ApiError) => this.toast.error(err.error),
    });
  }

  /** Swaps displayOrder with the adjacent segment in the given direction. */
  protected move(segment: ProductSegment, direction: -1 | 1): void {
    const list = this.segments();
    const index = list.findIndex((s) => s.id === segment.id);
    const neighbor = list[index + direction];
    if (!neighbor) return;
    this.productsService.updateSegment(segment.id, { displayOrder: neighbor.displayOrder }).subscribe();
    this.productsService.updateSegment(neighbor.id, { displayOrder: segment.displayOrder }).subscribe({
      next: () => {
        this.changed.set(true);
        this.load();
      },
      error: (err: ApiError) => this.toast.error(err.error),
    });
  }

  protected confirmDelete(segment: ProductSegment): void {
    const data: ConfirmDialogData = {
      title: 'Delete segment',
      message: `"${segment.name}" will be permanently removed.`,
      confirmLabel: 'Delete',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.productsService.removeSegment(segment.id).subscribe({
          next: () => {
            this.changed.set(true);
            this.load();
          },
          error: (err: ApiError) => this.toast.error(err.error),
        });
      });
  }
}
