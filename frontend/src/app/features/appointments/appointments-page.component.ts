import { Component, computed, inject } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { AdminAppointmentsOverviewComponent } from './admin-appointments-overview.component';
import { PractitionerScheduleComponent } from './practitioner-schedule.component';

/**
 * DOCTOR sees their own day/week schedule; ADMIN sees the org-wide filterable
 * overview — same role-branch convention as DashboardPageComponent's
 * isDoctor computed.
 */
@Component({
  selector: 'app-appointments-page',
  standalone: true,
  imports: [PractitionerScheduleComponent, AdminAppointmentsOverviewComponent],
  template: `
    <div class="page-header">
      <h1>Appointments</h1>
    </div>

    @if (isDoctor()) {
      <app-practitioner-schedule />
    } @else {
      <app-admin-appointments-overview />
    }
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 16px;
    }
    .page-header h1 {
      margin: 0;
      font-size: 1.5rem;
    }
  `,
})
export class AppointmentsPageComponent {
  private readonly authService = inject(AuthService);

  protected readonly isDoctor = computed(() => this.authService.currentUser()?.role === 'DOCTOR');
}
