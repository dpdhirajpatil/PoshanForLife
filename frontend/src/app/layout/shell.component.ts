import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Role } from '../core/models/user.model';
import { AuthService } from '../core/services/auth.service';

interface NavItem {
  label: string;
  icon: string;
  path: string;
  /** When set, the item is only shown to these roles. */
  roles?: Role[];
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', icon: 'dashboard', path: '/dashboard' },
  { label: 'Patients', icon: 'personal_injury', path: '/patients' },
  { label: 'Catalogue', icon: 'inventory_2', path: '/catalogue' },
  { label: 'Orders', icon: 'shopping_cart', path: '/orders' },
  { label: 'Transactions', icon: 'receipt_long', path: '/transactions' },
  { label: 'Reports', icon: 'description', path: '/reports' },
  { label: 'Leads', icon: 'connect_without_contact', path: '/leads' },
  { label: 'Users', icon: 'group', path: '/users', roles: ['ADMIN'] },
  { label: 'Settings', icon: 'settings', path: '/settings' },
];

/** Authenticated app frame: sidebar navigation + topbar with the current user. */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
  ],
  template: `
    <mat-sidenav-container class="shell">
      <mat-sidenav mode="side" opened class="shell-sidenav">
        <div class="shell-brand">Poshan for Life</div>
        <mat-nav-list>
          @for (item of navItems(); track item.path) {
            <a mat-list-item [routerLink]="item.path" routerLinkActive="active-link">
              <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
              <span matListItemTitle>{{ item.label }}</span>
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content>
        <mat-toolbar class="shell-topbar">
          <span class="spacer"></span>
          <button mat-button [matMenuTriggerFor]="userMenu">
            <mat-icon>account_circle</mat-icon>
            {{ auth.currentUser()?.name ?? auth.currentUser()?.email }}
          </button>
          <mat-menu #userMenu="matMenu">
            <button mat-menu-item disabled>{{ auth.currentUser()?.role }}</button>
            <button mat-menu-item (click)="auth.logout()">
              <mat-icon>logout</mat-icon>
              Logout
            </button>
          </mat-menu>
        </mat-toolbar>

        <main class="shell-content">
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    .shell {
      height: 100vh;
    }
    .shell-sidenav {
      width: 240px;
    }
    .shell-brand {
      padding: 16px;
      font-size: 1.15rem;
      font-weight: 600;
    }
    .shell-topbar {
      border-bottom: 1px solid rgba(0, 0, 0, 0.08);
    }
    .spacer {
      flex: 1;
    }
    .shell-content {
      padding: 24px;
    }
    .active-link {
      background: rgba(0, 0, 0, 0.06);
    }
  `,
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);

  /** Nav filtered by the current user's role (e.g. Users is admin-only). */
  protected readonly navItems = computed(() => {
    const role = this.auth.currentUser()?.role;
    return NAV_ITEMS.filter((item) => !item.roles || (role && item.roles.includes(role)));
  });
}
