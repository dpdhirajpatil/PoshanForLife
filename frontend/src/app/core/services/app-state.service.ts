import { Injectable, signal } from '@angular/core';

/**
 * Shell-wide layout state shared across the sidebar, topbar, and main
 * content — all three must resize/react together, so this can't live as
 * per-component local state.
 */
@Injectable({ providedIn: 'root' })
export class AppStateService {
  readonly sidebarCollapsed = signal(false);
  readonly mobileNavOpen = signal(false);

  toggleSidebar(): void {
    this.sidebarCollapsed.update((v) => !v);
  }

  openMobileNav(): void {
    this.mobileNavOpen.set(true);
  }

  closeMobileNav(): void {
    this.mobileNavOpen.set(false);
  }
}
