import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** Placeholder — replaced by the patients feature prompt. */
@Component({
  selector: 'app-patients-page',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>Patients</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <p>The Patients feature will be implemented in a later prompt.</p>
      </mat-card-content>
    </mat-card>
  `,
})
export class PatientsPageComponent {}
