import { Role } from '../models/user.model';

export interface NavItem {
  label: string;
  /** Material Icons ligature name. */
  icon: string;
  path: string;
  /** When set, the item is only shown to these roles. */
  roles?: Role[];
  /** Uppercase section divider rendered directly above this item. */
  sectionLabel?: string;
}

/**
 * The portal's nav, in display order. Mirrors the backend's role model:
 * ADMIN sees everything, DOCTOR sees everything except User Management,
 * PATIENT has no portal UI (kept in the type for a generic filter, not used
 * here). Add/reorder items by editing this list only.
 */
export const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', icon: 'dashboard', path: '/dashboard' },
  { label: 'Patients', icon: 'personal_injury', path: '/patients' },
  { label: 'Appointments', icon: 'event', path: '/appointments' },
  { label: 'Contacts', icon: 'connect_without_contact', path: '/leads', sectionLabel: 'CRM' },
  { label: 'Service Catalogue', icon: 'inventory_2', path: '/catalogue', sectionLabel: 'Catalogue' },
  { label: 'Products', icon: 'shopping_bag', path: '/products' },
  { label: 'Orders', icon: 'shopping_cart', path: '/orders' },
  { label: 'Transactions', icon: 'receipt_long', path: '/transactions' },
  { label: 'Reports', icon: 'description', path: '/reports' },
  { label: 'User Management', icon: 'group', path: '/users', roles: ['ADMIN'] },
  { label: 'Settings', icon: 'settings', path: '/settings' },
];
