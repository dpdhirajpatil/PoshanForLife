import { Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AppStateService } from '../core/services/app-state.service';
import { MobileDrawerComponent } from './mobile-drawer.component';
import { SidebarComponent } from './sidebar.component';
import { TopbarComponent } from './topbar.component';

/**
 * Authenticated app frame: desktop sidebar + mobile drawer + topbar + main
 * content, all reacting to the shared AppStateService so the sidebar width,
 * topbar padding, and content margin animate together (200ms, single 1024px
 * cutover — see SidebarComponent/TopbarComponent for the per-breakpoint
 * rendering).
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent, MobileDrawerComponent],
  template: `
    <app-sidebar />
    <app-mobile-drawer />
    <app-topbar />

    <main
      class="min-h-screen pt-[60px] transition-[margin-left] duration-200 ease-in-out"
      [class]="mainClass()"
    >
      <div class="px-4 py-6 lg:px-8">
        <router-outlet />
      </div>
    </main>
  `,
})
export class ShellComponent {
  private readonly appState = inject(AppStateService);

  protected readonly mainClass = computed(() =>
    this.appState.sidebarCollapsed() ? 'lg:ml-[72px]' : 'lg:ml-64',
  );
}
