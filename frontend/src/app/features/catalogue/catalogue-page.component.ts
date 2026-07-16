import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** Placeholder — replaced by the catalogue feature prompt. */
@Component({
  selector: 'app-catalogue-page',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>Catalogue</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <p>The Catalogue feature will be implemented in a later prompt.</p>
      </mat-card-content>
    </mat-card>
  `,
})
export class CataloguePageComponent {}
