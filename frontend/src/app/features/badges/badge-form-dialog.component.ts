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
  BADGE_CRITERIA_TYPES,
  BADGE_CRITERIA_TYPE_LABELS,
  Badge,
  BadgeCriteriaType,
} from '../../core/models/badge.model';
import { ToastService } from '../../core/services/toast.service';
import { applyServerFieldErrors } from '../../core/utils/form-errors';
import { SidePanelHandleComponent } from '../../shared/side-panel-handle.component';
import { BadgesService } from './badges.service';

export interface BadgeFormDialogData {
  badge?: Badge; // absent → create mode
}

/** Create/edit form for one badge catalog entry. */
@Component({
  selector: 'app-badge-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    SidePanelHandleComponent,
  ],
  template: `
    <app-side-panel-handle />
    <h2 mat-dialog-title>{{ isEdit ? 'Edit badge' : 'Add badge' }}</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="badge-form" (ngSubmit)="save()">
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" required />
          @if (form.controls.name.hasError('server')) {
            <mat-error>{{ form.controls.name.getError('server') }}</mat-error>
          } @else if (form.controls.name.invalid) {
            <mat-error>Name must be at least 2 characters</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="3"></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Icon key</mat-label>
          <input matInput formControlName="iconKey" required placeholder="e.g. trophy_gold" />
          @if (form.controls.iconKey.hasError('server')) {
            <mat-error>{{ form.controls.iconKey.getError('server') }}</mat-error>
          } @else if (form.controls.iconKey.invalid) {
            <mat-error>Icon key is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Criteria type</mat-label>
          <mat-select formControlName="criteriaType" required>
            @for (type of criteriaTypes; track type) {
              <mat-option [value]="type">{{ criteriaTypeLabel(type) }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Criteria value</mat-label>
          <input matInput type="number" formControlName="criteriaValue" min="0" />
          <mat-hint>{{ criteriaHint(form.controls.criteriaType.value) }}</mat-hint>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()" [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          {{ isEdit ? 'Save changes' : 'Create badge' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .badge-form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      width: 100%;
      padding-top: 8px;
    }
  `,
})
export class BadgeFormDialogComponent {
  protected readonly ref = inject(MatDialogRef<BadgeFormDialogComponent>);
  private readonly data = inject<BadgeFormDialogData>(MAT_DIALOG_DATA);
  private readonly badgesService = inject(BadgesService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly criteriaTypes = BADGE_CRITERIA_TYPES;
  protected readonly isEdit = !!this.data.badge;
  protected readonly saving = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    name: [this.data.badge?.name ?? '', [Validators.required, Validators.minLength(2)]],
    description: [this.data.badge?.description ?? ''],
    iconKey: [this.data.badge?.iconKey ?? '', Validators.required],
    criteriaType: [this.data.badge?.criteriaType ?? ('challenge_completed' as BadgeCriteriaType), Validators.required],
    criteriaValue: [this.data.badge?.criteriaValue ?? 0, [Validators.required, Validators.min(0)]],
  });

  protected criteriaTypeLabel(type: BadgeCriteriaType): string {
    return BADGE_CRITERIA_TYPE_LABELS[type];
  }

  protected criteriaHint(type: BadgeCriteriaType): string {
    switch (type) {
      case 'challenge_completed':
        return 'Unused for this type — awarded on completing any challenge.';
      case 'programme_count':
        return 'Number of completed programmes required.';
      case 'streak_days':
        return 'Streak length (days) required.';
      case 'custom':
        return 'Unused — custom badges have no automatic evaluation.';
    }
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request = this.isEdit
      ? this.badgesService.update(this.data.badge!.id, {
          name: value.name,
          description: value.description || undefined,
          iconKey: value.iconKey,
          criteriaType: value.criteriaType,
          criteriaValue: value.criteriaValue,
        })
      : this.badgesService.create({
          name: value.name,
          description: value.description || undefined,
          iconKey: value.iconKey,
          criteriaType: value.criteriaType,
          criteriaValue: value.criteriaValue,
        });

    this.saving.set(true);
    request.subscribe({
      next: (badge) => {
        this.toast.success(this.isEdit ? 'Badge updated' : 'Badge created');
        this.ref.close(badge);
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
