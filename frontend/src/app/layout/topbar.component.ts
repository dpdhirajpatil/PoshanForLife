import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, interval } from 'rxjs';
import { resolvePageTitle } from '../core/config/route-titles';
import { AppNotification } from '../core/models/notification.model';
import { AppStateService } from '../core/services/app-state.service';
import { AuthService } from '../core/services/auth.service';
import { NotificationService } from '../core/services/notification.service';
import { ThemeService } from '../core/services/theme.service';
import { initials } from '../core/utils/initials';
import { relativeTime } from '../core/utils/relative-time';
import { ROLE_BADGE_CLASSES, ROLE_BADGE_LABELS } from '../core/utils/role-badge';

const NOTIFICATION_POLL_MS = 60_000;

/** Maps a notification's deep-link target to a route; null = not navigable (panel just closes). */
function notificationRoute(n: AppNotification): string[] | null {
  if (!n.relatedEntityType || !n.relatedEntityId) return null;
  switch (n.relatedEntityType) {
    case 'lead':
      return ['/leads', n.relatedEntityId];
    case 'patient':
      return ['/patients', n.relatedEntityId];
    case 'report':
      // Reports are dialog-based (no detail route) — land on the list page.
      return ['/reports'];
    default:
      return null;
  }
}

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

        <div class="flex items-center gap-1 sm:gap-2">
          <!-- Theme toggle -->
          <button
            type="button"
            class="flex h-9 w-9 items-center justify-center rounded-full text-muted-foreground hover:bg-muted hover:text-foreground"
            [attr.aria-label]="theme.isDark() ? 'Switch to light mode' : 'Switch to dark mode'"
            [title]="theme.isDark() ? 'Switch to light mode' : 'Switch to dark mode'"
            (click)="theme.toggle()"
          >
            <span class="material-icons text-[20px] leading-none">{{ theme.isDark() ? 'light_mode' : 'dark_mode' }}</span>
          </button>

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
              class="mt-2 w-80 rounded-xl border border-border bg-popover text-popover-foreground shadow-lg"
            >
              <div class="flex items-center justify-between border-b border-border p-3">
                <p class="text-sm font-semibold">Notifications</p>
                @if (unreadCount() > 0) {
                  <button
                    type="button"
                    class="text-xs font-medium text-primary hover:underline"
                    (click)="markAllRead()"
                  >
                    Mark all as read
                  </button>
                }
              </div>
              <div class="max-h-96 overflow-y-auto">
                @if (notifications().length === 0) {
                  <p class="p-3 text-sm text-muted-foreground">No notifications yet.</p>
                } @else {
                  @for (n of notifications(); track n.id) {
                    <button
                      type="button"
                      class="flex w-full items-start gap-2 border-b border-border p-3 text-left last:border-b-0 hover:bg-muted"
                      (click)="openNotification(n)"
                    >
                      @if (!n.read) {
                        <span class="mt-1.5 h-2 w-2 flex-shrink-0 rounded-full bg-primary"></span>
                      } @else {
                        <span class="mt-1.5 h-2 w-2 flex-shrink-0 rounded-full"></span>
                      }
                      <div class="min-w-0 flex-1">
                        <p class="truncate text-sm font-medium text-foreground">{{ n.title }}</p>
                        <p class="mt-0.5 line-clamp-2 text-xs text-muted-foreground">{{ n.message }}</p>
                        <p class="mt-1 text-[11px] text-muted-foreground">{{ timeAgo(n.createdAt) }}</p>
                      </div>
                    </button>
                  }
                }
              </div>
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
  protected readonly theme = inject(ThemeService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly notifOpen = signal(false);
  protected readonly avatarOpen = signal(false);
  protected readonly unreadCount = signal(0);
  protected readonly notifications = signal<AppNotification[]>([]);

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
      this.loadNotifications();
    });

    this.loadNotifications();
    interval(NOTIFICATION_POLL_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.loadNotifications());
  }

  protected timeAgo(iso: string): string {
    return relativeTime(iso);
  }

  protected markAllRead(): void {
    this.notificationService.markAllRead().subscribe(() => {
      this.notifications.update((list) => list.map((n) => ({ ...n, read: true })));
      this.unreadCount.set(0);
    });
  }

  protected openNotification(n: AppNotification): void {
    this.notifOpen.set(false);
    const route = notificationRoute(n);
    if (route) {
      this.router.navigate(route);
    }
  }

  private loadNotifications(): void {
    this.notificationService.list(20).subscribe((res) => {
      this.notifications.set(res.notifications);
      this.unreadCount.set(res.unreadCount);
    });
  }

  protected signOut(): void {
    this.avatarOpen.set(false);
    this.auth.logout();
  }
}
