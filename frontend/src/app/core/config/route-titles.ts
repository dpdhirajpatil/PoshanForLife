/**
 * Page titles shown in the topbar, keyed by route path prefix. Resolution
 * matches the LONGEST matching prefix first, so a more specific route (e.g.
 * "/reports/upload") wins over a broader ancestor ("/reports").
 */
export const ROUTE_TITLES: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/patients': 'Patients',
  '/leads': 'Contacts',
  '/catalogue': 'Service Catalogue',
  '/orders': 'Orders',
  '/transactions': 'Transactions',
  '/reports': 'Reports',
  '/reports/upload': 'Upload Report',
  '/users': 'User Management',
  '/settings': 'Settings',
};

const SORTED_PREFIXES = Object.keys(ROUTE_TITLES).sort((a, b) => b.length - a.length);

export function resolvePageTitle(url: string): string {
  const path = url.split('?')[0].split('#')[0];
  const match = SORTED_PREFIXES.find((prefix) => path === prefix || path.startsWith(prefix + '/'));
  return match ? ROUTE_TITLES[match] : 'Poshan for Life';
}
