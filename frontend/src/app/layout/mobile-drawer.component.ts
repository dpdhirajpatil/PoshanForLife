import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AppStateService } from '../core/services/app-state.service';
import { SidebarComponent } from './sidebar.component';

/**
 * The < 1024px nav: a fixed hamburger button that opens a slide-in drawer
 * hosting the SAME SidebarComponent in `mobileSheet` mode, rather than
 * duplicating the nav markup for mobile. Closes on backdrop click or route
 * navigation.
 */
@Component({
  selector: 'app-mobile-drawer',
  standalone: true,
  imports: [SidebarComponent],
  template: `
    <button
      type="button"
      class="fixed left-4 top-[14px] z-40 flex h-8 w-8 items-center justify-center rounded-lg border border-border bg-card shadow-sm lg:hidden"
      (click)="appState.openMobileNav()"
    >
      <span class="material-icons text-[20px] leading-none">menu</span>
    </button>

    @if (appState.mobileNavOpen()) {
      <div class="fixed inset-0 z-50 lg:hidden">
        <div class="absolute inset-0 bg-black/40" (click)="appState.closeMobileNav()"></div>
        <div class="absolute left-0 top-0 h-full w-64 shadow-xl">
          <app-sidebar [mobileSheet]="true" />
        </div>
      </div>
    }
  `,
})
export class MobileDrawerComponent implements OnInit, OnDestroy {
  protected readonly appState = inject(AppStateService);
  private readonly router = inject(Router);
  private sub: Subscription | null = null;

  ngOnInit(): void {
    this.sub = this.router.events.subscribe((event) => {
      if (event instanceof NavigationStart) {
        this.appState.closeMobileNav();
      }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
