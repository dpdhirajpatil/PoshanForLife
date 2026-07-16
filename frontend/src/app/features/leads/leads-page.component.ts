import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** Placeholder — replaced by the leads feature prompt. */
@Component({
  selector: 'app-leads-page',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>Leads</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <p>The Leads feature will be implemented in a later prompt.</p>
      </mat-card-content>
    </mat-card>
  `,
})
export class LeadsPageComponent {}
