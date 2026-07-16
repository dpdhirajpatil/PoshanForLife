import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** Placeholder — replaced by the dashboard feature prompt. */
@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>Dashboard</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <p>The Dashboard feature will be implemented in a later prompt.</p>
      </mat-card-content>
    </mat-card>
  `,
})
export class DashboardPageComponent {}
