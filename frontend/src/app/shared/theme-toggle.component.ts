import { Component, inject } from '@angular/core';
import { Theme, ThemeService } from '../core/services/theme.service';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  template: `
    <div class="inline-flex items-center gap-1 rounded-full bg-muted p-1">
      @for (option of options; track option.value) {
        <button
          type="button"
          class="rounded-full px-4 py-1.5 text-sm font-medium transition-colors"
          [class.bg-primary]="theme.theme() === option.value"
          [class.text-primary-foreground]="theme.theme() === option.value"
          [class.text-muted-foreground]="theme.theme() !== option.value"
          [class.hover:bg-secondary]="theme.theme() !== option.value"
          (click)="theme.setTheme(option.value)"
        >
          {{ option.label }}
        </button>
      }
    </div>
  `,
})
export class ThemeToggleComponent {
  protected readonly theme = inject(ThemeService);

  protected readonly options: { value: Theme; label: string }[] = [
    { value: 'light', label: 'Light' },
    { value: 'system', label: 'System' },
    { value: 'dark', label: 'Dark' },
  ];
}
