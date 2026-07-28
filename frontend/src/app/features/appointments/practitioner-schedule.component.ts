import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiError } from '../../core/models/api-response.model';
import { Appointment } from '../../core/models/appointment.model';
import { ToastService } from '../../core/services/toast.service';
import { AppointmentDetailDialogComponent } from './appointment-detail-dialog.component';
import { AppointmentsService } from './appointments.service';

type ScheduleView = 'day' | 'week';

/**
 * A practitioner's own day/week schedule — DOCTOR is hard-scoped server-side
 * to their own appointments, so no practitioner filter is needed here (that's
 * the admin overview's job). Calendar-style, but hand-rolled (no calendar
 * library dependency) — a vertical time list for day view, a 7-column
 * horizontally-scrollable strip for week view.
 */
@Component({
  selector: 'app-practitioner-schedule',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatDialogModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  template: `
    <div class="toolbar">
      <div class="view-toggle">
        <button
          type="button"
          mat-icon-button
          [class.active]="view() === 'day'"
          matTooltip="Day view"
          (click)="setView('day')"
        >
          <mat-icon>view_day</mat-icon>
        </button>
        <button
          type="button"
          mat-icon-button
          [class.active]="view() === 'week'"
          matTooltip="Week view"
          (click)="setView('week')"
        >
          <mat-icon>view_week</mat-icon>
        </button>
      </div>

      <button mat-icon-button (click)="step(-1)" matTooltip="Previous">
        <mat-icon>chevron_left</mat-icon>
      </button>
      <button mat-button (click)="goToday()">Today</button>
      <button mat-icon-button (click)="step(1)" matTooltip="Next">
        <mat-icon>chevron_right</mat-icon>
      </button>

      <span class="range-label">{{ rangeLabel() }}</span>

      <span class="spacer"></span>

      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="jump">
        <mat-label>Jump to date</mat-label>
        <input matInput [matDatepicker]="picker" [formControl]="jumpDate" />
        <mat-datepicker-toggle matIconSuffix [for]="picker" />
        <mat-datepicker #picker />
      </mat-form-field>
    </div>

    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (view() === 'day') {
      <mat-card appearance="outlined" class="day-card">
        @if (dayAppointments().length === 0 && !loading()) {
          <p class="empty">No appointments on this day.</p>
        }
        @for (a of dayAppointments(); track a.id) {
          <div class="slot" (click)="openDetail(a)">
            <div class="time">{{ a.scheduledAt | date: 'shortTime' }}</div>
            <div class="info">
              <div class="patient">{{ a.patient.name }}</div>
              <div class="muted small">
                {{ a.durationMinutes }} min
                @if (a.isVideo) {
                  · <mat-icon inline class="video-icon">videocam</mat-icon>
                }
              </div>
            </div>
            <span class="badge" [class]="'status-' + a.status">{{ a.status }}</span>
          </div>
        }
      </mat-card>
    } @else {
      <div class="week-scroll">
        <div class="week-grid">
          @for (day of weekDays(); track day.iso) {
            <div class="day-column">
              <div class="day-header" [class.today]="day.isToday">{{ day.label }}</div>
              @for (a of day.appointments; track a.id) {
                <div class="chip" (click)="openDetail(a)">
                  <div class="chip-time">{{ a.scheduledAt | date: 'shortTime' }}</div>
                  <div class="chip-patient">{{ a.patient.name }}</div>
                  <span class="badge small" [class]="'status-' + a.status">{{ a.status }}</span>
                </div>
              } @empty {
                @if (!loading()) {
                  <div class="empty small">—</div>
                }
              }
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: `
    .toolbar {
      display: flex;
      align-items: center;
      gap: 4px;
      flex-wrap: wrap;
      margin-bottom: 16px;
    }
    .view-toggle {
      display: flex;
      gap: 2px;
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 2px;
      margin-right: 12px;
    }
    .view-toggle button.active {
      background: var(--primary-tint);
      color: var(--primary);
    }
    .range-label {
      font-weight: 600;
      margin-left: 8px;
    }
    .spacer {
      flex: 1;
    }
    .jump {
      width: 180px;
    }
    .day-card {
      display: flex;
      flex-direction: column;
      padding: 8px;
    }
    .slot {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 12px 16px;
      border-radius: 8px;
      cursor: pointer;
    }
    .slot:hover {
      background: var(--muted);
    }
    .time {
      width: 90px;
      flex-shrink: 0;
      font-weight: 600;
    }
    .info {
      flex: 1;
    }
    .patient {
      font-weight: 500;
    }
    .muted {
      color: var(--muted-foreground);
    }
    .small {
      font-size: 0.8rem;
    }
    .video-icon {
      font-size: 14px;
      vertical-align: middle;
    }
    .empty {
      padding: 24px;
      text-align: center;
      color: var(--muted-foreground);
    }
    .badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      white-space: nowrap;
    }
    .badge.small {
      padding: 1px 6px;
      font-size: 0.68rem;
    }
    .status-scheduled { background: var(--badge-blue-bg); color: var(--badge-blue-fg); }
    .status-completed { background: var(--badge-green-bg); color: var(--badge-green-fg); }
    .status-cancelled { background: var(--badge-grey-bg); color: var(--badge-grey-fg); }
    .status-no_show { background: var(--badge-red-bg); color: var(--badge-red-fg); }
    .week-scroll {
      width: 100%;
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
    }
    .week-grid {
      display: flex;
      gap: 8px;
      min-width: 980px;
    }
    .day-column {
      flex: 1;
      min-width: 130px;
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 8px;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .day-header {
      font-weight: 600;
      font-size: 0.85rem;
      padding-bottom: 6px;
      border-bottom: 1px solid var(--border);
      margin-bottom: 4px;
    }
    .day-header.today {
      color: var(--primary);
    }
    .chip {
      background: var(--muted);
      border-radius: 6px;
      padding: 6px 8px;
      cursor: pointer;
    }
    .chip:hover {
      background: var(--primary-tint);
    }
    .chip-time {
      font-size: 0.75rem;
      font-weight: 600;
    }
    .chip-patient {
      font-size: 0.8rem;
    }
  `,
})
export class PractitionerScheduleComponent {
  private readonly appointmentsService = inject(AppointmentsService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  protected readonly view = signal<ScheduleView>('day');
  protected readonly anchorDate = signal<Date>(startOfDay(new Date()));
  protected readonly loading = signal(false);
  protected readonly appointments = signal<Appointment[]>([]);
  protected readonly jumpDate = new FormControl<Date | null>(new Date());

  protected readonly dayAppointments = signal<Appointment[]>([]);
  protected readonly weekDays = signal<
    { iso: string; label: string; isToday: boolean; appointments: Appointment[] }[]
  >([]);

  constructor() {
    this.load();
    this.jumpDate.valueChanges.pipe(takeUntilDestroyed()).subscribe((date) => {
      if (date) this.anchorDate.set(startOfDay(date));
      this.load();
    });
  }

  protected setView(view: ScheduleView): void {
    this.view.set(view);
    this.load();
  }

  protected step(direction: 1 | -1): void {
    const amount = this.view() === 'day' ? 1 : 7;
    const next = new Date(this.anchorDate());
    next.setDate(next.getDate() + amount * direction);
    this.anchorDate.set(next);
    this.jumpDate.setValue(next, { emitEvent: false });
    this.load();
  }

  protected goToday(): void {
    const today = startOfDay(new Date());
    this.anchorDate.set(today);
    this.jumpDate.setValue(today, { emitEvent: false });
    this.load();
  }

  protected rangeLabel(): string {
    if (this.view() === 'day') {
      return this.anchorDate().toLocaleDateString(undefined, {
        weekday: 'long',
        month: 'long',
        day: 'numeric',
      });
    }
    const start = startOfWeek(this.anchorDate());
    const end = new Date(start);
    end.setDate(end.getDate() + 6);
    return `${start.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} – ${end.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}`;
  }

  protected openDetail(appointment: Appointment): void {
    this.dialog
      .open(AppointmentDetailDialogComponent, { data: appointment })
      .afterClosed()
      .subscribe((changed) => changed && this.load());
  }

  private load(): void {
    const isWeek = this.view() === 'week';
    const from = isWeek ? startOfWeek(this.anchorDate()) : this.anchorDate();
    const to = isWeek ? addDays(from, 6) : this.anchorDate();

    this.loading.set(true);
    this.appointmentsService
      .list({ dateFrom: toIsoDate(from), dateTo: toIsoDate(to), page: 1, limit: 200 })
      .subscribe({
        next: (result) => {
          this.appointments.set(result.data);
          this.loading.set(false);
          if (isWeek) {
            this.weekDays.set(buildWeek(from, result.data));
          } else {
            this.dayAppointments.set(result.data);
          }
        },
        error: (err: ApiError) => {
          this.loading.set(false);
          this.toast.error(err.error);
        },
      });
  }
}

function startOfDay(date: Date): Date {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return d;
}

function startOfWeek(date: Date): Date {
  const d = startOfDay(date);
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day; // Monday-start week
  d.setDate(d.getDate() + diff);
  return d;
}

function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function buildWeek(
  weekStart: Date,
  appointments: Appointment[],
): { iso: string; label: string; isToday: boolean; appointments: Appointment[] }[] {
  const today = toIsoDate(new Date());
  return Array.from({ length: 7 }, (_, i) => {
    const date = addDays(weekStart, i);
    const iso = toIsoDate(date);
    return {
      iso,
      label: date.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' }),
      isToday: iso === today,
      appointments: appointments.filter((a) => toIsoDate(new Date(a.scheduledAt)) === iso),
    };
  });
}
