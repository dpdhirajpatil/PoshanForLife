import { Component, Input } from '@angular/core';

export type TrapeziumBarColor = 'navy' | 'green';

/**
 * The CI guide's recurring slanted-parallelogram motif behind section
 * headings and primary CTAs — a signature shape, not a plain rounded
 * rectangle. Project content into the default slot; pick `color` to match
 * whatever the bar sits on.
 */
@Component({
  selector: 'app-trapezium-bar',
  standalone: true,
  template: `
    <div
      class="inline-flex items-center px-6 py-2.5"
      [class.bg-brand-navy]="color === 'navy'"
      [class.text-white]="color === 'navy'"
      [class.bg-brand-green]="color === 'green'"
      [class.text-brand-navy-darkest]="color === 'green'"
      style="clip-path: polygon(3% 0, 100% 0, 97% 100%, 0 100%);"
    >
      <ng-content></ng-content>
    </div>
  `,
})
export class TrapeziumBarComponent {
  @Input() color: TrapeziumBarColor = 'navy';
}
