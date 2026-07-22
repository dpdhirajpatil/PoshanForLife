import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { CatalogueTableComponent } from './catalogue-table.component';

/**
 * Catalogue: three tabs (Programmes / Sessions / Challenges), each the same
 * shared table component pointed at its resource group. Tab content is lazy
 * so only the visited tabs fetch.
 */
@Component({
  selector: 'app-catalogue-page',
  standalone: true,
  imports: [MatCardModule, MatTabsModule, CatalogueTableComponent],
  template: `
    <div class="page-header">
      <h1>Catalogue</h1>
    </div>

    <mat-card appearance="outlined">
      <mat-tab-group>
        <mat-tab label="Programmes">
          <ng-template matTabContent>
            <app-catalogue-table type="programmes" />
          </ng-template>
        </mat-tab>
        <mat-tab label="Sessions">
          <ng-template matTabContent>
            <app-catalogue-table type="sessions" />
          </ng-template>
        </mat-tab>
        <mat-tab label="Challenges">
          <ng-template matTabContent>
            <app-catalogue-table type="challenges" />
          </ng-template>
        </mat-tab>
      </mat-tab-group>
    </mat-card>
  `,
  styles: `
    .page-header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 16px;
    }
    .page-header h1 {
      margin: 0;
      font-size: 1.5rem;
    }
  `,
})
export class CataloguePageComponent {}
