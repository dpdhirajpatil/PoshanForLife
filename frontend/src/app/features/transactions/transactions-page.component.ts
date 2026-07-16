import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** Placeholder — replaced by the transactions feature prompt. */
@Component({
  selector: 'app-transactions-page',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>Transactions</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <p>The Transactions feature will be implemented in a later prompt.</p>
      </mat-card-content>
    </mat-card>
  `,
})
export class TransactionsPageComponent {}
