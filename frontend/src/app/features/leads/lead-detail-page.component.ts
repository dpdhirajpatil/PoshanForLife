import { DatePipe, TitleCasePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { ApiError } from '../../core/models/api-response.model';
import {
  ACTIVITY_ICONS,
  LEAD_STAGES,
  LEAD_STAGE_LABELS,
  LOGGABLE_ACTIVITY_TYPES,
  LeadActivityType,
  LeadDetail,
  LeadStage,
} from '../../core/models/lead.model';
import { ToastService } from '../../core/services/toast.service';
import { openSidePanel } from '../../shared/side-panel';
import { ConvertLeadDialogComponent } from './convert-lead-dialog.component';
import { LeadsService } from './leads.service';
import { ScheduleFollowupDialogComponent } from './schedule-followup-dialog.component';

/**
 * Lead detail: contact info, an editable stage dropdown (fires PATCH, which
 * auto-logs a stage_change activity server-side), the chronological activity
 * timeline with a quick "Log activity" add, "Schedule follow-up", and the
 * guided "Convert to patient" flow (deep-links to the new patient on success).
 */
@Component({
  selector: 'app-lead-detail-page',
  standalone: true,
  imports: [
    DatePipe,
    TitleCasePipe,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatDialogModule,
    MatProgressSpinnerModule,
  ],
  template: `
    @if (loading()) {
      <div class="center"><mat-spinner diameter="40" /></div>
    }
    @if (lead(); as l) {
      <a mat-button routerLink="/leads">
        <mat-icon>arrow_back</mat-icon>
        Contacts
      </a>

      <mat-card appearance="outlined" class="header-card">
        <div class="header-main">
          <h1>{{ l.name }}</h1>
          <div class="header-meta">
            @if (l.phone) { <span>{{ l.phone }}</span> }
            @if (l.email) { <span>· {{ l.email }}</span> }
            @if (l.city) { <span>· {{ l.city }}</span> }
            @if (l.age) { <span>· {{ l.age }} yrs</span> }
            <span>· Source: {{ l.source }}</span>
          </div>
          @if (l.assignedPractitioner) {
            <div class="header-meta">Practitioner: {{ l.assignedPractitioner.name }}</div>
          }
        </div>

        <div class="header-actions">
          <mat-form-field appearance="outline" subscriptSizing="dynamic" class="stage-select">
            <mat-label>Stage</mat-label>
            <mat-select [formControl]="stageControl" (selectionChange)="changeStage($event.value)">
              @for (s of stages; track s) {
                <mat-option [value]="s">{{ stageLabel(s) }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          @if (l.stage !== 'converted') {
            <button mat-stroked-button (click)="openScheduleFollowup()">
              <mat-icon>event</mat-icon>
              Schedule follow-up
            </button>
            <button mat-flat-button color="primary" (click)="openConvert()">
              <mat-icon>person_add</mat-icon>
              Convert to patient
            </button>
          } @else if (l.convertedPatientId) {
            <a mat-flat-button color="primary" [routerLink]="['/patients', l.convertedPatientId]">
              <mat-icon>account_circle</mat-icon>
              View patient
            </a>
          }
        </div>
      </mat-card>

      @if (l.nextFollowupAt) {
        <mat-card appearance="outlined" class="followup-banner">
          <mat-icon>notifications_active</mat-icon>
          Next follow-up: {{ l.nextFollowupAt | date: 'medium' }}
        </mat-card>
      }

      <div class="content-grid">
        <mat-card appearance="outlined" class="info-card">
          <mat-card-header>
            <mat-card-title>Details</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <dl class="info-grid">
              <div><dt>Gender</dt><dd>{{ l.gender ?? '—' }}</dd></div>
              <div><dt>Interested in</dt><dd>{{ l.interestedProgramme?.name ?? '—' }}</dd></div>
              <div class="span-2"><dt>Health goal</dt><dd>{{ l.healthGoal || '—' }}</dd></div>
              <div class="span-2"><dt>Notes</dt><dd>{{ l.notes || '—' }}</dd></div>
              <div><dt>Created by</dt><dd>{{ l.createdBy.name }}</dd></div>
              <div><dt>Created</dt><dd>{{ l.createdAt | date: 'mediumDate' }}</dd></div>
            </dl>
          </mat-card-content>
        </mat-card>

        <mat-card appearance="outlined" class="activity-card">
          <mat-card-header>
            <mat-card-title>Activity timeline</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <form [formGroup]="activityForm" class="log-activity">
              <mat-form-field appearance="outline" subscriptSizing="dynamic" class="type-select">
                <mat-label>Type</mat-label>
                <mat-select formControlName="activityType">
                  @for (t of loggableTypes; track t) {
                    <mat-option [value]="t">{{ t | titlecase }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline" subscriptSizing="dynamic" class="description-input">
                <mat-label>Log activity…</mat-label>
                <input matInput formControlName="description" (keydown.enter)="logActivity()" />
              </mat-form-field>
              <button mat-flat-button color="primary" (click)="logActivity()" [disabled]="logging()">
                @if (logging()) {
                  <mat-spinner diameter="18" />
                } @else {
                  Add
                }
              </button>
            </form>

            <div class="timeline">
              @for (a of reversedActivities(); track a.id) {
                <div class="timeline-entry">
                  <mat-icon class="timeline-icon">{{ activityIcons[a.activityType] }}</mat-icon>
                  <div class="timeline-body">
                    <p class="timeline-desc">{{ a.description }}</p>
                    <p class="timeline-meta">{{ a.createdBy.name }} · {{ a.createdAt | date: 'medium' }}</p>
                  </div>
                </div>
              } @empty {
                <p class="muted">No activity logged yet.</p>
              }
            </div>
          </mat-card-content>
        </mat-card>
      </div>
    }
  `,
  styles: `
    .center {
      display: grid;
      place-content: center;
      padding: 64px;
    }
    .header-card {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      padding: 16px;
      margin: 8px 0 16px;
      flex-wrap: wrap;
    }
    .header-main h1 {
      margin: 0 0 4px;
      font-size: 1.35rem;
    }
    .header-meta {
      color: var(--muted-foreground);
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
      font-size: 0.9rem;
    }
    .header-actions {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    .stage-select {
      width: 160px;
    }
    .followup-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 16px;
      margin-bottom: 16px;
      color: var(--badge-amber-fg);
      background: var(--badge-amber-bg);
    }
    .content-grid {
      display: grid;
      grid-template-columns: 1fr 1.4fr;
      gap: 16px;
      align-items: start;
    }
    .info-grid {
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin: 0;
    }
    .info-grid dt {
      font-size: 0.8rem;
      color: var(--muted-foreground);
    }
    .info-grid dd {
      margin: 2px 0 0;
    }
    .span-2 {
      grid-column: span 2;
    }
    .log-activity {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 16px;
      align-items: flex-start;
    }
    .type-select {
      flex: 1 1 140px;
    }
    .description-input {
      flex: 3 1 200px;
    }
    .timeline {
      display: flex;
      flex-direction: column;
      gap: 12px;
      max-height: 480px;
      overflow-y: auto;
    }
    .timeline-entry {
      display: flex;
      gap: 10px;
    }
    .timeline-icon {
      color: var(--muted-foreground);
      margin-top: 2px;
    }
    .timeline-desc {
      margin: 0;
    }
    .timeline-meta {
      margin: 2px 0 0;
      font-size: 0.78rem;
      color: var(--muted-foreground);
    }
    .muted {
      color: var(--muted-foreground);
    }
    @media (max-width: 900px) {
      .content-grid {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class LeadDetailPageComponent implements OnInit {
  /** Bound from the :id route param via withComponentInputBinding. */
  readonly id = input.required<string>();

  private readonly leadsService = inject(LeadsService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly stages = LEAD_STAGES;
  protected readonly loggableTypes = LOGGABLE_ACTIVITY_TYPES;
  protected readonly activityIcons = ACTIVITY_ICONS;

  protected readonly lead = signal<LeadDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly logging = signal(false);

  protected readonly stageControl = new FormControl<LeadStage>('new', { nonNullable: true });

  protected readonly activityForm = this.fb.nonNullable.group({
    activityType: ['note' as LeadActivityType, Validators.required],
    description: ['', [Validators.required, Validators.minLength(1)]],
  });

  ngOnInit(): void {
    this.load(this.id());
  }

  protected stageLabel(stage: LeadStage): string {
    return LEAD_STAGE_LABELS[stage];
  }

  protected reversedActivities() {
    return [...(this.lead()?.activities ?? [])].reverse();
  }

  protected changeStage(stage: LeadStage): void {
    const current = this.lead();
    if (!current || stage === current.stage) return;
    this.leadsService.update(current.id, { stage }).subscribe({
      next: (updated) => {
        this.lead.set(updated);
        this.stageControl.setValue(updated.stage, { emitEvent: false });
        this.toast.success(`Stage changed to ${this.stageLabel(updated.stage)}`);
      },
      error: (err: ApiError) => {
        this.stageControl.setValue(current.stage, { emitEvent: false });
        this.toast.error(err.error);
      },
    });
  }

  protected logActivity(): void {
    const current = this.lead();
    if (!current || this.activityForm.invalid || this.logging()) {
      this.activityForm.markAllAsTouched();
      return;
    }
    const v = this.activityForm.getRawValue();
    this.logging.set(true);
    this.leadsService.addActivity(current.id, v).subscribe({
      next: () => {
        this.logging.set(false);
        this.activityForm.reset({ activityType: v.activityType, description: '' });
        this.load(current.id);
      },
      error: (err: ApiError) => {
        this.logging.set(false);
        this.toast.error(err.error);
      },
    });
  }

  protected openScheduleFollowup(): void {
    const current = this.lead();
    if (!current) return;
    openSidePanel(this.dialog, ScheduleFollowupDialogComponent, {
      data: { leadId: current.id, leadName: current.name },
    })
      .afterClosed()
      .subscribe((updated) => updated && this.load(current.id));
  }

  protected openConvert(): void {
    const current = this.lead();
    if (!current) return;
    openSidePanel(this.dialog, ConvertLeadDialogComponent, { data: { lead: current } })
      .afterClosed()
      .subscribe((result) => {
        if (!result) return;
        this.toast.success(result.message);
        this.router.navigate(['/patients', result.patientId]);
      });
  }

  private load(id: string): void {
    this.loading.set(true);
    this.leadsService.get(id).subscribe({
      next: (lead) => {
        this.lead.set(lead);
        this.stageControl.setValue(lead.stage, { emitEvent: false });
        this.loading.set(false);
      },
      error: (err: ApiError) => {
        this.loading.set(false);
        this.toast.error(err.error);
      },
    });
  }
}
