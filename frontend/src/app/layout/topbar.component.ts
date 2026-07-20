import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { resolvePageTitle } from '../core/config/route-titles';
import { AppStateService } from '../core/services/app-state.service';
import { AuthService } from '../core/services/auth.service';
import { initials } from '../core/utils/initials';

const ROLE_BADGE_CLASSES: Record<string, string> = {
  ADMIN: 'bg-red-100 text-red-700',
  DOCTOR: 'bg-primary-100 text-primary-700',
  PATIENT: 'bg-primary-50 text-primary-600',
};

const ROLE_BADGE_LABELS: Record<string, string> = {
  ADMIN: 'Admin',
  DOCTOR: 'Practitioner',
  PATIENT: 'Patient',
};

/**
 * Fixed topbar: page title (longest-prefix route match) on the left, the
 * notification bell and avatar menu on the right. Its left padding is kept
 * in sync with the sidebar's width/collapse state so both animate together.
 */
@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [RouterLink, CdkOverlayOrigin, CdkConnectedOverlay],
  template: `
    <header
      class="fixed inset-x-0 top-0 z-20 flex h-[60px] items-center border-b border-border bg-card transition-[padding-left] duration-200 ease-in-out"
      [class]="paddingClass()"
    >
      <div class="flex w-full items-center justify-between pl-12 pr-4 lg:pl-6">
        <h1 class="truncate font-display text-lg font-semibold text-foreground">{{ pageTitle() }}</h1>

        <div class="flex items-center gap-2">
          <!-- Notification bell -->
          <button
            type="button"
            cdkOverlayOrigin
            #notifOrigin="cdkOverlayOrigin"
            class="relative flex h-9 w-9 items-center justify-center rounded-full text-muted-foreground hover:bg-muted hover:text-foreground"
            (click)="notifOpen.set(!notifOpen())"
          >
            <span class="material-icons text-[20px] leading-none">notifications</span>
            @if (unreadCount() > 0) {
              <span
                class="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-destructive-foreground"
              >
                {{ unreadCount() }}
              </span>
            }
          </button>
          <ng-template
            cdkConnectedOverlay
            [cdkConnectedOverlayOrigin]="notifOrigin"
            [cdkConnectedOverlayOpen]="notifOpen()"
            [cdkConnectedOverlayHasBackdrop]="true"
            cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
            [cdkConnectedOverlayPositions]="menuPositions"
            (backdropClick)="notifOpen.set(false)"
          >
            <div
              class="mt-2 w-72 rounded-xl border border-border bg-popover p-3 text-popover-foreground shadow-lg"
            >
              <p class="text-sm font-semibold">Notifications</p>
              <p class="mt-2 text-sm text-muted-foreground">No notifications yet.</p>
            </div>
          </ng-template>

          <!-- Avatar menu -->
          <button
            type="button"
            cdkOverlayOrigin
            #avatarOrigin="cdkOverlayOrigin"
            class="flex h-9 w-9 items-center justify-center rounded-full bg-primary-100 text-xs font-semibold text-primary-700"
            (click)="avatarOpen.set(!avatarOpen())"
          >
            {{ userInitials() }}
          </button>
          <ng-template
            cdkConnectedOverlay
            [cdkConnectedOverlayOrigin]="avatarOrigin"
            [cdkConnectedOverlayOpen]="avatarOpen()"
            [cdkConnectedOverlayHasBackdrop]="true"
            cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
            [cdkConnectedOverlayPositions]="menuPositions"
            (backdropClick)="avatarOpen.set(false)"
            (detach)="avatarOpen.set(false)"
          >
            <div
              class="mt-2 w-64 overflow-hidden rounded-xl border border-border bg-popover text-popover-foreground shadow-lg"
            >
              <div class="border-b border-border p-3">
                <p class="truncate text-sm font-semibold">{{ userName() }}</p>
                <p class="truncate text-xs text-muted-foreground">{{ userEmail() }}</p>
                <span class="mt-1.5 inline-block rounded-full px-2 py-0.5 text-[11px] font-medium" [class]="roleBadgeClass()">
                  {{ roleBadgeLabel() }}
                </span>
              </div>
              <nav class="p-1.5">
                <a
                  routerLink="/settings"
                  class="flex items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-foreground hover:bg-muted"
                  (click)="avatarOpen.set(false)"
                >
                  <span class="material-icons text-[18px] leading-none">person</span>
                  My Profile
                </a>
                <a
                  routerLink="/settings"
                  class="flex items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-foreground hover:bg-muted"
                  (click)="avatarOpen.set(false)"
                >
                  <span class="material-icons text-[18px] leading-none">settings</span>
                  Settings
                </a>
                <div class="my-1.5 border-t border-border"></div>
                <button
                  type="button"
                  class="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-sm text-destructive hover:bg-red-50"
                  (click)="signOut()"
                >
                  <span class="material-icons text-[18px] leading-none">logout</span>
                  Sign out
                </button>
              </nav>
            </div>
          </ng-template>
        </div>
      </div>
    </header>
  `,
})
export class TopbarComponent {
  protected readonly appState = inject(AppStateService);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly notifOpen = signal(false);
  protected readonly avatarOpen = signal(false);
  protected readonly unreadCount = signal(0);

  protected readonly menuPositions = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top' } as const,
  ];

  protected readonly pageTitle = signal(resolvePageTitle(this.router.url));

  protected readonly userName = computed(() => this.auth.currentUser()?.name ?? '');
  protected readonly userEmail = computed(() => this.auth.currentUser()?.email ?? '');
  protected readonly userInitials = computed(() => initials(this.userName() || 'U'));
  protected readonly roleBadgeClass = computed(
    () => ROLE_BADGE_CLASSES[this.auth.currentUser()?.role ?? ''] ?? 'bg-muted text-muted-foreground',
  );
  protected readonly roleBadgeLabel = computed(
    () => ROLE_BADGE_LABELS[this.auth.currentUser()?.role ?? ''] ?? '',
  );

  protected readonly paddingClass = computed(() =>
    this.appState.sidebarCollapsed() ? 'lg:pl-[72px]' : 'lg:pl-64',
  );

  constructor() {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.pageTitle.set(resolvePageTitle(this.router.url));
    });
  }

  protected signOut(): void {
    this.avatarOpen.set(false);
    this.auth.logout();
  }
}
