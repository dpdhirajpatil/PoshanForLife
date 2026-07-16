import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** Placeholder — replaced by the reports feature prompt. */
@Component({
  selector: 'app-reports-page',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>Reports</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <p>The Reports feature will be implemented in a later prompt.</p>
      </mat-card-content>
    </mat-card>
  `,
})
export class ReportsPageComponent {}
