import { Routes } from '@angular/router';
import { adminOnlyGuard } from './core/guards/admin-only.guard';
import { adminOrDoctorGuard } from './core/guards/admin-or-doctor.guard';
import { authGuard } from './core/guards/auth.guard';
import { LoginComponent } from './features/auth/login.component';
import { ForbiddenComponent } from './features/errors/forbidden.component';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, title: 'Poshan · Sign in' },
  {
    path: 'forbidden',
    component: ForbiddenComponent,
    canActivate: [authGuard],
    title: 'Poshan · Access denied',
  },
  {
    // Outside the shell (no sidenav/topbar) so window.print() only renders
    // the invoice sheet itself.
    path: 'invoices/:transactionId',
    loadComponent: () =>
      import('./features/transactions/invoice-page.component').then(
        (m) => m.InvoicePageComponent,
      ),
    canActivate: [authGuard, adminOrDoctorGuard],
    title: 'Poshan · Invoice',
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard, adminOrDoctorGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadChildren: () => import('./features/dashboard/dashboard.routes') },
      { path: 'patients', loadChildren: () => import('./features/patients/patients.routes') },
      { path: 'appointments', loadChildren: () => import('./features/appointments/appointments.routes') },
      { path: 'catalogue', loadChildren: () => import('./features/catalogue/catalogue.routes') },
      { path: 'products', loadChildren: () => import('./features/products/products.routes') },
      { path: 'orders', loadChildren: () => import('./features/orders/orders.routes') },
      { path: 'transactions', loadChildren: () => import('./features/transactions/transactions.routes') },
      { path: 'reports', loadChildren: () => import('./features/reports/reports.routes') },
      { path: 'leads', loadChildren: () => import('./features/leads/leads.routes') },
      {
        path: 'users',
        canActivate: [adminOnlyGuard],
        loadChildren: () => import('./features/users/users.routes'),
      },
      { path: 'settings', loadChildren: () => import('./features/settings/settings.routes') },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
