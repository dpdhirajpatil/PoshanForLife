import { Component, Input, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { NAV_ITEMS } from '../core/config/nav-items';
import { AppStateService } from '../core/services/app-state.service';
import { AuthService } from '../core/services/auth.service';
import { initials } from '../core/utils/initials';
import { ROLE_BADGE_CLASSES, ROLE_BADGE_LABELS } from '../core/utils/role-badge';
import { LeadsService } from '../features/leads/leads.service';
import { SidebarTooltipDirective } from '../shared/sidebar-tooltip.directive';

/**
 * The nav itself, rendered in two modes from one component: the fixed
 * desktop rail (collapsible, reacting to AppStateService) and the mobile
 * drawer's contents (`mobileSheet`: always expanded, relatively positioned,
 * fills its host edge-to-edge). Keeping one component avoids duplicating the
 * nav markup between the two breakpoints.
 */
@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, SidebarTooltipDirective],
  host: { class: 'contents' },
  template: `
    <div [class]="containerClass()">
      <!-- Header -->
      <div class="flex h-[60px] shrink-0 items-center justify-between border-b border-border px-3">
        <div class="flex min-w-0 items-center gap-2.5">
          <div
            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary-600 text-white"
          >
            <span class="material-icons text-[20px] leading-none">eco</span>
          </div>
          @if (showLabels()) {
            <div class="flex min-w-0 items-baseline gap-1 overflow-hidden whitespace-nowrap">
              <span class="font-display font-semibold text-foreground">Poshan</span>
              <span class="font-display font-light text-primary-600">for Life</span>
            </div>
          }
        </div>
        @if (!mobileSheet) {
          <button
            type="button"
            class="flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground"
            (click)="appState.toggleSidebar()"
          >
            <span class="material-icons text-[18px] leading-none">{{
              collapsed() ? 'chevron_right' : 'chevron_left'
            }}</span>
          </button>
        }
      </div>

      <!-- Nav -->
      <nav class="flex-1 overflow-y-auto overflow-x-hidden px-3 py-3">
        @for (item of navItems(); track item.path) {
          @if (item.sectionLabel && showLabels()) {
            <div
              class="mb-1 mt-4 px-3 text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground/80 first:mt-0"
            >
              {{ item.sectionLabel }}
            </div>
          }
          <a
            [routerLink]="item.path"
            routerLinkActive="is-active"
            #rla="routerLinkActive"
            class="relative mb-2 flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors last:mb-0"
            [class.bg-primary-50]="rla.isActive"
            [class.text-primary-700]="rla.isActive"
            [class.text-muted-foreground]="!rla.isActive"
            [class.hover:bg-muted]="!rla.isActive"
            [class.hover:text-foreground]="!rla.isActive"
            [class.justify-center]="!showLabels()"
            [appSidebarTooltip]="item.label"
            [enabled]="!showLabels()"
          >
            @if (rla.isActive) {
              <span class="absolute left-0 top-1/2 h-5 w-[3px] -translate-y-1/2 rounded-full bg-primary-500"></span>
            }
            <span class="material-icons shrink-0 text-[20px] leading-none">{{ item.icon }}</span>
            @if (showLabels()) {
              <span class="truncate">{{ item.label }}</span>
            }
            @if (item.path === '/leads' && followupTodayCount() > 0) {
              <span
                class="ml-auto flex h-5 min-w-5 shrink-0 items-center justify-center rounded-full bg-accent-500 px-1 text-[11px] font-semibold text-white"
                [class.absolute]="!showLabels()"
                [class.right-1]="!showLabels()"
                [class.top-1]="!showLabels()"
              >
                {{ followupTodayCount() }}
              </span>
            }
          </a>
        }
      </nav>

      <!-- Footer -->
      <div
        class="shrink-0 border-t border-border p-3"
        [class.flex]="showLabels()"
        [class.items-center]="showLabels()"
        [class.gap-2.5]="showLabels()"
      >
        @if (showLabels()) {
          <div
            class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-100 text-xs font-semibold text-primary-700"
          >
            {{ userInitials() }}
          </div>
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm font-medium text-foreground">{{ userName() }}</div>
            <span
              class="mt-0.5 inline-block rounded-full px-2 py-0.5 text-[11px] font-medium"
              [class]="roleBadgeClass()"
            >
              {{ roleBadgeLabel() }}
            </span>
          </div>
          <button
            type="button"
            class="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-red-50 hover:text-red-600"
            appSidebarTooltip="Sign out"
            [enabled]="!showLabels()"
            (click)="auth.logout()"
          >
            <span class="material-icons text-[18px] leading-none">logout</span>
          </button>
        } @else {
          <div class="flex flex-col items-center gap-2">
            <div
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-100 text-xs font-semibold text-primary-700"
            >
              {{ userInitials() }}
            </div>
            <span
              class="inline-block rounded-full px-1.5 py-0.5 text-[10px] font-medium"
              [class]="roleBadgeClass()"
            >
              {{ roleBadgeLabel() }}
            </span>
            <button
              type="button"
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-red-50 hover:text-red-600"
              appSidebarTooltip="Sign out"
              [enabled]="true"
              (click)="auth.logout()"
            >
              <span class="material-icons text-[18px] leading-none">logout</span>
            </button>
          </div>
        }
      </div>
    </div>
  `,
})
export class SidebarComponent {
  /** True when rendered inside the mobile drawer: always expanded, fills its host. */
  @Input() mobileSheet = false;

  protected readonly appState = inject(AppStateService);
  protected readonly auth = inject(AuthService);
  private readonly leadsService = inject(LeadsService);
  private readonly router = inject(Router);

  protected readonly followupTodayCount = signal(0);

  constructor() {
    this.leadsService
      .list({ followupToday: true, page: 1, limit: 1 })
      .subscribe((result) => this.followupTodayCount.set(result.meta?.total ?? 0));
  }

  protected readonly collapsed = computed(() => this.appState.sidebarCollapsed());
  protected readonly showLabels = computed(() => this.mobileSheet || !this.collapsed());

  protected readonly navItems = computed(() => {
    const role = this.auth.currentUser()?.role;
    return NAV_ITEMS.filter((item) => !item.roles || (role && item.roles.includes(role)));
  });

  protected readonly userName = computed(() => this.auth.currentUser()?.name ?? '');
  protected readonly userInitials = computed(() => initials(this.userName() || 'U'));
  protected readonly roleBadgeClass = computed(
    () => ROLE_BADGE_CLASSES[this.auth.currentUser()?.role ?? ''] ?? 'bg-muted text-muted-foreground',
  );
  protected readonly roleBadgeLabel = computed(
    () => ROLE_BADGE_LABELS[this.auth.currentUser()?.role ?? ''] ?? '',
  );

  protected isCurrentRoute(path: string): boolean {
    return this.router.url === path || this.router.url.startsWith(path + '/');
  }

  protected containerClass(): string {
    if (this.mobileSheet) {
      return 'flex h-full w-64 flex-col bg-card';
    }
    const width = this.collapsed() ? 'w-[72px]' : 'w-64';
    return (
      'fixed inset-y-0 left-0 z-30 hidden lg:flex flex-col bg-card border-r border-border ' +
      `transition-[width] duration-200 ease-in-out ${width}`
    );
  }
}
