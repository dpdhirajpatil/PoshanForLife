import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** Placeholder — replaced by the settings feature prompt. */
@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>Settings</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <p>The Settings feature will be implemented in a later prompt.</p>
      </mat-card-content>
    </mat-card>
  `,
})
export class SettingsPageComponent {}
