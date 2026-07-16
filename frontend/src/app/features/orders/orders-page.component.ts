import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** Placeholder — replaced by the orders feature prompt. */
@Component({
  selector: 'app-orders-page',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>Orders</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <p>The Orders feature will be implemented in a later prompt.</p>
      </mat-card-content>
    </mat-card>
  `,
})
export class OrdersPageComponent {}
